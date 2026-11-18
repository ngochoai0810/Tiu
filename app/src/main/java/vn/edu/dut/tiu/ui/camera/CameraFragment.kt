package vn.edu.dut.tiu.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.TorchState
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.navigation.fragment.findNavController
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.databinding.FragmentCameraBinding
import vn.edu.dut.tiu.ui.common.setup
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class CameraFragment : Fragment(R.layout.fragment_camera) {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var isOcrMode = true
    private var isFlashOn = false
    private var camera: Camera? = null
    private var cameraSession = 0
    private var liveRecognizer: TextRecognizer? = null
    private var lastAnalysisAt = 0L
    private var ocrReadyAt = Long.MAX_VALUE
    private var instructionJob: Job? = null
    private var galleryJob: Job? = null
    private var galleryPermissionPending = false

    private val cameraPreferences
        get() = requireContext().getSharedPreferences("camera_preferences", Context.MODE_PRIVATE)

    private val galleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        galleryPermissionPending = false
        refreshGalleryThumbnail()
        showFirstUseInstructions()
    }

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(context, "Cần cấp quyền Camera để sử dụng chức năng này", Toast.LENGTH_SHORT).show()
                requestGalleryAccessOnce()
            }
        }

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bundle = Bundle().apply {
                putString("imageUri", uri.toString())
                putBoolean("isOcrMode", isOcrMode)
            }
            findNavController().navigate(R.id.transactionEditorFragment, bundle)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentCameraBinding.bind(view)
        val b = binding
        liveRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        b.topBar.setup(this)
        b.topBar.logo.visibility = View.GONE
        b.tvInstructionTitle.visibility = View.GONE
        b.tvInstructionSubtitle.visibility = View.GONE
        b.viewFinder.previewStreamState.observe(viewLifecycleOwner) { state ->
            if (state == PreviewView.StreamState.STREAMING) showFirstUseInstructions()
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Toggle OCR / Ảnh
        b.tabOcr.setOnClickListener {
            isOcrMode = true
            ocrReadyAt = SystemClock.elapsedRealtime() + 3_000
            b.tabOcr.setBackgroundResource(R.drawable.bg_camera_mode_selected)
            b.tabPhoto.setBackgroundResource(0)
            b.tabOcr.setTextColor(ContextCompat.getColor(requireContext(), R.color.tiu_ink))
            b.tabPhoto.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            b.tabOcr.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(b.tabOcr.currentTextColor)
            b.tabPhoto.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(b.tabPhoto.currentTextColor)
            b.viewReceiptFrame.visibility = View.VISIBLE
            b.badgeScanning.visibility = View.GONE
            b.viewOcrFrame.visibility = View.VISIBLE
            b.tvInstructionTitle.text = "Chụp món đồ hoặc hóa đơn"
        }

        b.tabPhoto.setOnClickListener {
            isOcrMode = false
            ocrReadyAt = Long.MAX_VALUE
            b.tabPhoto.setBackgroundResource(R.drawable.bg_camera_mode_selected)
            b.tabOcr.setBackgroundResource(0)
            b.tabPhoto.setTextColor(ContextCompat.getColor(requireContext(), R.color.tiu_ink))
            b.tabOcr.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            b.tabPhoto.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(b.tabPhoto.currentTextColor)
            b.tabOcr.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(b.tabOcr.currentTextColor)
            b.viewReceiptFrame.visibility = View.VISIBLE
            b.badgeScanning.visibility = View.GONE
            b.viewOcrFrame.visibility = View.GONE
            b.tvInstructionTitle.text = "Chụp món đồ hoặc hóa đơn"
        }

        // Restore the selected capture mode when the view is recreated.
        if (isOcrMode) b.tabOcr.performClick() else b.tabPhoto.performClick()

        b.btnFlash.setOnClickListener {
            val activeCamera = camera ?: return@setOnClickListener
            if (!activeCamera.cameraInfo.hasFlashUnit()) return@setOnClickListener
            val result = activeCamera.cameraControl.enableTorch(!isFlashOn)
            result.addListener({
                if (_binding !== b || camera !== activeCamera) return@addListener
                try {
                    result.get()
                } catch (e: Exception) {
                    Log.e("CameraFragment", "Không thể bật đèn flash", e)
                    Toast.makeText(b.root.context, "Không thể đổi trạng thái đèn flash", Toast.LENGTH_SHORT).show()
                }
            }, ContextCompat.getMainExecutor(b.root.context))
        }

        b.btnZoom.setOnClickListener {
            val activeCamera = camera ?: return@setOnClickListener
            val state = activeCamera.cameraInfo.zoomState.value ?: return@setOnClickListener
            val normalZoom = 1f.coerceIn(state.minZoomRatio, state.maxZoomRatio)
            val zoomed = 2f.coerceIn(state.minZoomRatio, state.maxZoomRatio)
            val target = if (state.zoomRatio > normalZoom + 0.01f) normalZoom else zoomed
            val result = activeCamera.cameraControl.setZoomRatio(target)
            result.addListener({
                if (_binding !== b || camera !== activeCamera) return@addListener
                try {
                    result.get()
                } catch (e: Exception) {
                    Log.e("CameraFragment", "Không thể thay đổi độ phóng đại", e)
                    Toast.makeText(b.root.context, "Không thể thay đổi mức zoom", Toast.LENGTH_SHORT).show()
                }
            }, ContextCompat.getMainExecutor(b.root.context))
        }

        // Đảo camera
        b.btnFlipCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera()
            Toast.makeText(context, "Đã chuyển đổi camera", Toast.LENGTH_SHORT).show()
        }

        // Nút chụp ảnh hoặc chọn ảnh từ thư viện -> Mở form thêm giao dịch
        b.btnShutter.setOnClickListener {
            takePhoto()
        }

        b.btnGallery.setOnClickListener {
            pickMediaLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // Nút hoàn tác trên banner đã lưu
        b.btnUndo.setOnClickListener {
            b.layoutSavedToast.visibility = View.GONE
            Toast.makeText(context, "Đã hoàn tác giao dịch", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshGalleryThumbnail()
        showFirstUseInstructions()
    }

    override fun onPause() {
        instructionJob?.cancel()
        instructionJob = null
        _binding?.badgeScanning?.visibility = View.GONE
        _binding?.tvInstructionTitle?.visibility = View.GONE
        _binding?.tvInstructionSubtitle?.visibility = View.GONE
        super.onPause()
    }

    private fun showFirstUseInstructions() {
        val b = _binding ?: return
        if (!isResumed || galleryPermissionPending || instructionJob?.isActive == true ||
            cameraPreferences.getBoolean("instructions_seen", false) ||
            b.viewFinder.previewStreamState.value != PreviewView.StreamState.STREAMING) return
        b.tvInstructionTitle.visibility = View.VISIBLE
        b.tvInstructionSubtitle.visibility = View.VISIBLE
        instructionJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(2_000)
            b.tvInstructionTitle.visibility = View.GONE
            b.tvInstructionSubtitle.visibility = View.GONE
            cameraPreferences.edit().putBoolean("instructions_seen", true).apply()
        }
    }

    private fun hasGalleryAccess(): Boolean {
        val context = context ?: return false
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val fullAccess = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val selectedAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
            PackageManager.PERMISSION_GRANTED
        return fullAccess || selectedAccess
    }

    private fun requestGalleryAccessOnce() {
        if (hasGalleryAccess()) {
            refreshGalleryThumbnail()
            return
        }
        if (galleryPermissionPending || cameraPreferences.getBoolean("gallery_permission_requested", false)) return
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        cameraPreferences.edit().putBoolean("gallery_permission_requested", true).apply()
        galleryPermissionPending = true
        galleryPermissionLauncher.launch(permissions)
    }

    private fun refreshGalleryThumbnail() {
        val b = _binding ?: return
        galleryJob?.cancel()
        if (!hasGalleryAccess()) {
            Glide.with(b.root).clear(b.btnGallery)
            b.btnGallery.setImageDrawable(null)
            b.btnGallery.contentDescription = "Chọn ảnh từ thư viện"
            return
        }
        val resolver = b.root.context.applicationContext.contentResolver
        galleryJob = viewLifecycleOwner.lifecycleScope.launch {
            val latestImage = withContext(Dispatchers.IO) {
                try {
                    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        "${MediaStore.Images.Media.IS_PENDING} = 0"
                    } else null
                    resolver.query(
                        collection,
                        arrayOf(MediaStore.Images.Media._ID),
                        selection,
                        null,
                        "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("CameraFragment", "Không thể đọc ảnh mới nhất", e)
                    null
                }
            }
            val radius = (10 * b.root.resources.displayMetrics.density).toInt()
            Glide.with(b.root)
                .load(latestImage)
                .transform(CenterCrop(), RoundedCorners(radius))
                .placeholder(R.drawable.bg_photo_empty)
                .error(R.drawable.bg_photo_empty)
                .into(b.btnGallery)
            b.btnGallery.contentDescription = if (latestImage != null) {
                "Ảnh mới nhất trong thư viện. Chạm để chọn ảnh"
            } else {
                "Chọn ảnh từ thư viện"
            }
        }
    }

    private fun startCamera() {
        val b = _binding ?: return
        val owner = viewLifecycleOwner
        val session = ++cameraSession
        camera = null
        imageCapture = null
        b.btnFlash.isEnabled = false
        b.btnZoom.isEnabled = false
        b.btnShutter.isEnabled = false
        val executor = ContextCompat.getMainExecutor(b.root.context)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(b.root.context)
        cameraProviderFuture.addListener({
            if (_binding !== b || session != cameraSession) return@addListener
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                provider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = b.viewFinder.surfaceProvider
                }
                val capture = ImageCapture.Builder().build()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { frame -> analyzeReceiptFrame(frame, b, session) }
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val activeCamera = provider.bindToLifecycle(owner, selector, preview, capture, analysis)
                camera = activeCamera
                imageCapture = capture
                requestGalleryAccessOnce()
                b.btnShutter.isEnabled = true
                b.btnFlash.isEnabled = activeCamera.cameraInfo.hasFlashUnit()
                b.btnFlash.alpha = if (b.btnFlash.isEnabled) 1f else 0.4f
                activeCamera.cameraInfo.torchState.observe(owner) { state ->
                    if (camera !== activeCamera) return@observe
                    isFlashOn = state == TorchState.ON
                    b.btnFlash.setColorFilter(ContextCompat.getColor(
                        b.root.context, if (isFlashOn) R.color.tiu_orange else R.color.white
                    ))
                    b.btnFlash.contentDescription = if (isFlashOn) "Tắt đèn flash" else "Bật đèn flash"
                }
                activeCamera.cameraInfo.zoomState.observe(owner) { state ->
                    if (camera !== activeCamera) return@observe
                    val label = String.format(Locale.US, "%.1f", state.zoomRatio).removeSuffix(".0")
                    b.btnZoom.text = "$label×"
                    b.btnZoom.isEnabled = state.maxZoomRatio > 1.01f
                    b.btnZoom.alpha = if (b.btnZoom.isEnabled) 1f else 0.4f
                }
            } catch (exc: Exception) {
                camera = null
                imageCapture = null
                Log.e("CameraFragment", "Lỗi khởi chạy camera", exc)
                Toast.makeText(b.root.context, "Không thể mở camera đã chọn", Toast.LENGTH_SHORT).show()
            }
        }, executor)
    }

    @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
    private fun analyzeReceiptFrame(frame: ImageProxy, b: FragmentCameraBinding, session: Int) {
        val recognizer = liveRecognizer
        val now = SystemClock.elapsedRealtime()
        if (_binding !== b || session != cameraSession || !isResumed || !isOcrMode ||
            recognizer == null || now < ocrReadyAt || now - lastAnalysisAt < 1_000) {
            frame.close()
            return
        }
        val mediaImage = frame.image
        if (mediaImage == null) {
            frame.close()
            return
        }
        lastAnalysisAt = now
        b.badgeScanning.visibility = View.VISIBLE
        val executor = ContextCompat.getMainExecutor(b.root.context)
        try {
            val image = InputImage.fromMediaImage(mediaImage, frame.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener(executor) { result ->
                    if (_binding !== b || session != cameraSession || !isOcrMode ||
                        SystemClock.elapsedRealtime() < ocrReadyAt) return@addOnSuccessListener
                    val color = if (result.text.isNotBlank()) R.color.tiu_orange else R.color.white
                    b.viewOcrFrame.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(b.root.context, color)
                    )
                }
                .addOnFailureListener(executor) { error ->
                    Log.w("CameraFragment", "Không đọc được khung hình OCR", error)
                }
                .addOnCompleteListener(executor) {
                    if (_binding === b && session == cameraSession) b.badgeScanning.visibility = View.GONE
                    frame.close()
                }
        } catch (e: Exception) {
            if (_binding === b && session == cameraSession) b.badgeScanning.visibility = View.GONE
            frame.close()
            Log.w("CameraFragment", "Không thể quét khung hình", e)
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        // Tạo file tạm để lưu ảnh
        val outputDirectory = File(requireContext().cacheDir, "camera_images").apply { mkdirs() }
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "Chụp ảnh thất bại: ${exc.message}", exc)
                    val b = _binding ?: return
                    Toast.makeText(b.root.context, "Lỗi khi chụp ảnh", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (_binding == null || !isAdded) return
                    val savedUri = Uri.fromFile(photoFile)
                    val bundle = Bundle().apply {
                        putString("imageUri", savedUri.toString())
                        putBoolean("isOcrMode", isOcrMode)
                    }
                    findNavController().navigate(R.id.transactionEditorFragment, bundle)
                }
            }
        )
    }
    
    override fun onDestroyView() {
        instructionJob?.cancel()
        instructionJob = null
        galleryJob?.cancel()
        galleryJob = null
        _binding?.let { Glide.with(it.root).clear(it.btnGallery) }
        cameraSession++
        liveRecognizer?.close()
        liveRecognizer = null
        camera?.cameraControl?.enableTorch(false)
        cameraProvider?.unbindAll()
        camera = null
        imageCapture = null
        _binding = null
        super.onDestroyView()
    }
}
