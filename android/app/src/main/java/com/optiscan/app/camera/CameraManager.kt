package com.optiscan.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume

/**
 * CameraX controller managing camera binding, preview, and photo capture.
 */
class CameraManager {

    private val tag = "CameraManager"
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Bind CameraX lifecycle and connect Preview to PreviewView.
     */
    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onCameraBound: ((Boolean) -> Unit)? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                Log.i(tag, "CameraX successfully bound to lifecycle")
                onCameraBound?.invoke(true)
            } catch (e: Exception) {
                Log.e(tag, "Use case binding failed", e)
                onCameraBound?.invoke(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private var camera: androidx.camera.core.Camera? = null

    /**
     * Enable or disable the camera torch / flash.
     */
    fun setTorchEnabled(enabled: Boolean): Boolean {
        val cam = camera ?: return false
        return try {
            cam.cameraControl.enableTorch(enabled)
            true
        } catch (e: Exception) {
            Log.w(tag, "Failed to toggle torch: ${e.message}")
            false
        }
    }

    /**
     * Capture a still photo and return as compressed JPEG ByteArray.
     */
    suspend fun capturePhoto(context: Context): Result<ByteArray> = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            continuation.resume(Result.failure(IllegalStateException("Camera capture is not initialized")))
            return@suspendCancellableCoroutine
        }

        val executor = ContextCompat.getMainExecutor(context)
        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val jpegBytes = imageProxyToJpegBytes(imageProxy)
                        continuation.resume(Result.success(jpegBytes))
                    } catch (e: Exception) {
                        continuation.resume(Result.failure(e))
                    } finally {
                        imageProxy.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(tag, "Photo capture failed: ${exception.message}", exception)
                    continuation.resume(Result.failure(exception))
                }
            }
        )
    }

    /**
     * Convert ImageProxy buffer to JPEG byte array, rotating to upright based on imageInfo.rotationDegrees.
     */
    private fun imageProxyToJpegBytes(imageProxy: ImageProxy): ByteArray {
        val plane = imageProxy.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val rawBytes = ByteArray(buffer.remaining())
        buffer.get(rawBytes)

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees == 0) {
            return rawBytes
        }

        return try {
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            if (bitmap != null) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                val out = ByteArrayOutputStream()
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                if (rotatedBitmap != bitmap) {
                    rotatedBitmap.recycle()
                }
                bitmap.recycle()
                out.toByteArray()
            } else {
                rawBytes
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to apply rotation $rotationDegrees to captured image: ${e.message}")
            rawBytes
        }
    }

    /**
     * Utility to decode byte array into Android Bitmap, respecting EXIF orientation if present.
     */
    fun bytesToBitmap(bytes: ByteArray): Bitmap? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            if (degrees != 0) {
                val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotated
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to evaluate EXIF orientation: ${e.message}")
            bitmap
        }
    }

    /**
     * Unbind all use cases and release camera provider.
     */
    fun unbind() {
        camera = null
        cameraProvider?.unbindAll()
        cameraProvider = null
    }
}
