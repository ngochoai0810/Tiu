package vn.edu.dut.tiu.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
    private var currentZoom = 1

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(context, "Cần cấp quyền Camera để sử dụng chức năng này", Toast.LENGTH_SHORT).show()
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
        b.topBar.setup(this)

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Toggle OCR / Ảnh
        b.tabOcr.setOnClickListener {
            isOcrMode = true
            b.tabOcr.setBackgroundResource(R.drawable.bg_toggle_selected)
            b.tabOcr.setTextColor(ContextCompat.getColor(requireContext(), R.color.tiu_ink))
            b.tabPhoto.setBackgroundResource(0)
            b.tabPhoto.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            b.viewReceiptFrame.visibility = View.VISIBLE
            b.badgeScanning.visibility = View.VISIBLE
            b.tvInstructionTitle.text = "Chụp món đồ hoặc hóa đơn"
        }

        b.tabPhoto.setOnClickListener {
            isOcrMode = false
            b.tabPhoto.setBackgroundResource(R.drawable.bg_toggle_selected)
            b.tabPhoto.setTextColor(ContextCompat.getColor(requireContext(), R.color.tiu_ink))
            b.tabOcr.setBackgroundResource(0)
            b.tabOcr.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            b.viewReceiptFrame.visibility = View.GONE
            b.badgeScanning.visibility = View.GONE
            b.tvInstructionTitle.text = "Chụp ảnh khoảnh khắc chi tiêu"
        }

        // Bật / tắt flash
        b.btnFlash.setOnClickListener {
            isFlashOn = !isFlashOn
            b.btnFlash.setColorFilter(
                if (isFlashOn) ContextCompat.getColor(requireContext(), R.color.tiu_orange)
                else ContextCompat.getColor(requireContext(), R.color.white)
            )
            Toast.makeText(context, if (isFlashOn) "Đã bật Flash" else "Đã tắt Flash", Toast.LENGTH_SHORT).show()
        }

        // Chuyển đổi Zoom
        b.btnZoom.setOnClickListener {
            currentZoom = if (currentZoom == 1) 2 else 1
            b.btnZoom.text = "${currentZoom}×"
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

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.viewFinder.surfaceProvider
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("CameraFragment", "Lỗi khởi chạy camera", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
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
                    Toast.makeText(requireContext(), "Lỗi khi chụp ảnh", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
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
        super.onDestroyView()
        _binding = null
    }
}
