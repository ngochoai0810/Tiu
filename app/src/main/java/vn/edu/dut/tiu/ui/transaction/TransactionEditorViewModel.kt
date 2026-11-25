package vn.edu.dut.tiu.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import vn.edu.dut.tiu.data.model.Category
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.data.repository.CategoryRepository
import vn.edu.dut.tiu.data.repository.TransactionRepository
import vn.edu.dut.tiu.domain.ReceiptAmountParser
import vn.edu.dut.tiu.domain.ReceiptLine
import vn.edu.dut.tiu.domain.TransactionValidator
import vn.edu.dut.tiu.ui.common.toUserMessage

/**
 * ViewModel của form Thêm (Create) / Sửa (Update) / Xóa (Delete) giao dịch.
 * [transactionId] = null -> thêm mới; khác null -> sửa giao dịch có sẵn.
 */
class TransactionEditorViewModel(
    private val transactionRepo: TransactionRepository,
    categoryRepo: CategoryRepository,
    private val transactionId: String?,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    data class UiState(
        val isEditing: Boolean = false,
        val type: TransactionType = TransactionType.EXPENSE,
        /** Danh mục cùng loại với [type] để người dùng chọn. */
        val categories: List<Category> = emptyList(),
        val selectedCategory: Category? = null,
        val dateMillis: Long = 0L,
        /** Giao dịch gốc khi sửa – Fragment dùng để điền sẵn số tiền / ghi chú. */
        val original: Transaction? = null,
        val imageUri: String? = null,
        val isOcrMode: Boolean = false,
        val ocrAmount: String? = null,
        val ocrText: String? = null,
        val isRecognizing: Boolean = false,
        val ocrError: String? = null,
        val isUploadingImage: Boolean = false,
        val errors: Map<TransactionValidator.Field, String> = emptyMap(),
        val isSaving: Boolean = false,
    )

    sealed interface Event {
        data object Saved : Event
        data object Deleted : Event
        data class Error(val message: String) : Event
    }

    private var ocrJob: Job? = null
    var ocrPrefillConsumed = false
        private set

    private var allCategories: List<Category> = emptyList()
    private var selectedCategoryId: String? = null

    /** Fragment chỉ điền sẵn form một lần (kể cả khi xoay màn hình). */
    var prefillConsumed = false
    var imageArgumentConsumed = false

    private val _state = MutableStateFlow(UiState(isEditing = transactionId != null, dateMillis = now()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            categoryRepo.observeCategories()
                .catch { e -> _events.send(Event.Error(e.toUserMessage())) }
                .collect { cats ->
                    allCategories = cats
                    refreshCategories()
                }
        }
        if (transactionId != null) loadExisting(transactionId)
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            val tx = try {
                transactionRepo.get(id)
            } catch (e: Exception) {
                _events.send(Event.Error(e.toUserMessage())); return@launch
            }
            if (tx == null) {
                _events.send(Event.Error("Không tìm thấy giao dịch (có thể đã bị xóa)"))
                return@launch
            }
            selectedCategoryId = tx.categoryId
            _state.update { it.copy(type = tx.type, dateMillis = tx.date, original = tx) }
            refreshCategories()
        }
    }

    private fun refreshCategories() {
        _state.update { s ->
            val ofType = allCategories.filter { it.type == s.type }
            // Danh mục của giao dịch cũ có thể đã bị xóa -> vẫn hiển thị tên đã lưu.
            val selected = ofType.firstOrNull { it.id == selectedCategoryId }
                ?: s.original?.takeIf { it.categoryId == selectedCategoryId && it.type == s.type }
                    ?.let { Category(id = it.categoryId, name = it.categoryName, type = it.type) }
            s.copy(categories = ofType, selectedCategory = selected)
        }
    }

    fun setType(type: TransactionType) {
        if (type == _state.value.type) return
        selectedCategoryId = null
        _state.update { it.copy(type = type, errors = it.errors - TransactionValidator.Field.CATEGORY) }
        refreshCategories()
    }

    fun selectCategory(category: Category) {
        selectedCategoryId = category.id
        _state.update { it.copy(errors = it.errors - TransactionValidator.Field.CATEGORY) }
        refreshCategories()
    }

    fun setImage(uri: String?, isOcrMode: Boolean = false) {
        if (_state.value.imageUri == uri && _state.value.isOcrMode == isOcrMode) return
        ocrJob?.cancel()
        ocrPrefillConsumed = false
        _state.update {
            it.copy(imageUri = uri, isOcrMode = isOcrMode, ocrAmount = null,
                ocrText = null, isRecognizing = false, ocrError = null)
        }
    }

    fun consumeOcrPrefill() {
        ocrPrefillConsumed = true
    }

    fun setDate(millis: Long) {
        _state.update { it.copy(dateMillis = millis, errors = it.errors - TransactionValidator.Field.DATE) }
    }

    fun save(amountText: String, note: String) {
        val s = _state.value
        if (s.isSaving) return
        val cleanNote = note.trim()
        val result = TransactionValidator.validate(amountText, s.selectedCategory?.id, cleanNote, s.dateMillis, now())
        _state.update { it.copy(errors = result.errors) }
        if (!result.isValid) return

        val category = s.selectedCategory!!
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            
            val uploadedImageUrl = if (s.imageUri != null && (s.imageUri.startsWith("content://") || s.imageUri.startsWith("file://"))) {
                _state.update { it.copy(isUploadingImage = true) }
                try {
                    // Giải mã + nén ảnh nặng -> chạy nền để không đứng giao diện (ANR).
                    withContext(Dispatchers.IO) { uploadImageToStorage(s.imageUri) }
                } catch (e: Exception) {
                    _events.send(Event.Error("Lỗi tải ảnh lên: ${e.toUserMessage()}"))
                    _state.update { it.copy(isSaving = false, isUploadingImage = false) }
                    return@launch
                }
            } else {
                // Giữ nguyên ảnh đã có (bắt đầu bằng http hoặc data:image) hoặc null nếu xóa ảnh
                s.imageUri
            }

            val tx = Transaction(
                id = transactionId.orEmpty(),
                type = s.type,
                amount = result.amount!!,
                categoryId = category.id,
                categoryName = category.name,
                note = cleanNote,
                date = s.dateMillis,
                imageUrl = uploadedImageUrl,
                recurringId = s.original?.recurringId,
                createdAt = s.original?.createdAt ?: 0L,
            )

            try {
                if (transactionId == null) transactionRepo.add(tx) else transactionRepo.update(tx)
                _events.send(Event.Saved)
            } catch (e: Exception) {
                _events.send(Event.Error(e.toUserMessage()))
            } finally {
                _state.update { it.copy(isSaving = false, isUploadingImage = false) }
            }
        }
    }

    private suspend fun uploadImageToStorage(uriString: String): String {
        return suspendCancellableCoroutine { cont ->
            try {
                val uri = Uri.parse(uriString)
                val context = vn.edu.dut.tiu.TiuApp.instance // Cần có context để đọc file
                
                // Mở luồng đọc file ảnh
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap == null) {
                    cont.resumeWithException(Exception("Không thể đọc file ảnh"))
                    return@suspendCancellableCoroutine
                }

                // Nén ảnh lại (tối đa chiều dài/rộng là 800px)
                val maxWidth = 800f
                val maxHeight = 800f
                val scale = minOf(maxWidth / originalBitmap.width, maxHeight / originalBitmap.height)
                
                val scaledBitmap = if (scale < 1) {
                    Bitmap.createScaledBitmap(
                        originalBitmap, 
                        (originalBitmap.width * scale).toInt(), 
                        (originalBitmap.height * scale).toInt(), 
                        true
                    )
                } else originalBitmap

                // Chuyển sang chuỗi Base64 (JPEG, chất lượng 70%)
                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                
                // Giải phóng bộ nhớ
                if (scaledBitmap != originalBitmap) {
                    scaledBitmap.recycle()
                }
                originalBitmap.recycle()

                // Trả về chuỗi prefix Base64 chuẩn để có thể nhúng trực tiếp vào ImageView qua Glide
                val dataUri = "data:image/jpeg;base64,$base64String"
                cont.resume(dataUri)
                
            } catch (e: Exception) {
                e.printStackTrace()
                cont.resumeWithException(e)
            }
        }
    }

    fun performOcr(context: android.content.Context, uriString: String) {
        val state = _state.value
        if (!state.isOcrMode || state.imageUri != uriString || state.isRecognizing ||
            state.ocrText != null || state.ocrError != null) return
        val appContext = context.applicationContext
        _state.update { it.copy(isRecognizing = true, ocrError = null) }
        ocrJob = viewModelScope.launch(Dispatchers.Default) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val image = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(appContext, Uri.parse(uriString))
                }
                val result = recognizer.process(image).await()
                val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                    line.boundingBox?.let { box ->
                        ReceiptLine(line.text, box.left, box.top, box.right, box.bottom)
                    }
                }
                val amount = ReceiptAmountParser.extract(lines)?.toString()
                _state.update {
                    it.copy(ocrText = result.text, ocrAmount = amount,
                        isRecognizing = false, ocrError = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isRecognizing = false,
                        ocrError = "Không đọc được ảnh hóa đơn. Bạn có thể nhập số tiền thủ công.")
                }
            } finally {
                recognizer.close()
            }
        }
    }

    fun delete() {
        val id = transactionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                transactionRepo.delete(id)
                _events.send(Event.Deleted)
            } catch (e: Exception) {
                _events.send(Event.Error(e.toUserMessage()))
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
}
