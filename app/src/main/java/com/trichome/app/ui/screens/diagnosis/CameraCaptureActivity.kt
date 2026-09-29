package com.trichome.app.ui.screens.diagnosis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * In-process camera capture built on CameraX.
 *
 * The previous implementation used `ActivityResultContracts.TakePicturePreview`,
 * which builds an implicit `ACTION_IMAGE_CAPTURE` intent and calls
 * `resolveActivity()`. Without a `<queries>` declaration the platform filters the
 * camera app out, `resolveActivity()` returns null and the contract throws
 * `ActivityNotFoundException` synchronously inside the click handler — the
 * process dies with no chance to show an error. It also only ever returned a
 * low-resolution thumbnail, which is useless for analysing trichomes.
 *
 * CameraX binds to this activity's own lifecycle, needs no external app, and
 * produces a full-resolution JPEG.
 */
class CameraCaptureActivity : ComponentActivity() {

    /**
     * Bound by [CameraCaptureContent] once the preview is live. They are
     * [internal] rather than private because the composable lives in this file
     * but outside the class body.
     */
    internal var imageCapture: ImageCapture? = null
    internal var provider: ProcessCameraProvider? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(
                this,
                getString(com.trichome.app.R.string.camera_permission_required),
                Toast.LENGTH_LONG
            ).show()
            finishWithError(EXTRA_ERROR, getString(com.trichome.app.R.string.camera_permission_required))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        setContent {
            MaterialTheme {
                Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                    CameraCaptureContent(
                        onCapture = { capture() },
                        onCancel = { finishWithError(EXTRA_ERROR, null) }
                    )
                }
            }
        }
    }

    private fun capture() {
        val capture = imageCapture
        if (capture == null) {
            Log.w(TAG, "Capture requested before the camera was bound")
            return
        }
        val target = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(target).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_OUTPUT, target.absolutePath))
                    finish()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed", exception)
                    target.delete()
                    finishWithError(
                        EXTRA_ERROR,
                        exception.message ?: getString(com.trichome.app.R.string.camera_capture_failed)
                    )
                }
            }
        )
    }

    private fun finishWithError(key: String, message: String?) {
        setResult(RESULT_CANCELED, Intent().putExtra(key, message))
        finish()
    }

    companion object {
        private const val TAG = "CameraCapture"
        const val EXTRA_OUTPUT = "output_path"
        const val EXTRA_ERROR = "error_message"

        /** Full-quality JPEG, capped so a huge sensor cannot OOM the process. */
        const val MAX_CAPTURE_EDGE = 2048

        /**
         * Decodes [path] downsampled so its longest edge is at most [MAX_CAPTURE_EDGE].
         *
         * `BitmapFactory` with `inSampleSize` reads the file at a reduced scale
         * instead of allocating the full image first, which is what keeps a
         * 12 MP capture from exhausting the heap.
         */
        fun decodeSampled(path: String, maxEdge: Int = MAX_CAPTURE_EDGE): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            var w = bounds.outWidth
            var h = bounds.outHeight
            while (w / (sample * 2) >= maxEdge || h / (sample * 2) >= maxEdge) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeFile(path, options)
        }

        /** Encodes [bitmap] as JPEG, halving the resolution to bound the size. */
        fun encodeJpeg(bitmap: Bitmap, quality: Int = 85): ByteArray {
            val scaled = if (bitmap.width > MAX_CAPTURE_EDGE || bitmap.height > MAX_CAPTURE_EDGE) {
                val ratio = MAX_CAPTURE_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap

            return ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
        }
    }
}

@Composable
private fun CameraCaptureContent(
    onCapture: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var bound by remember { mutableStateOf(false) }
    var bindingError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val cameraProvider = context.cameraProvider()
            val preview = Preview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
            } catch (e: IllegalArgumentException) {
                // No back camera on this device: fall back to the front one.
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    capture
                )
            }
            (context as? CameraCaptureActivity)?.let { activity ->
                activity.imageCapture = capture
                activity.provider = cameraProvider
            }
            bound = true
        } catch (e: Exception) {
            Log.e("CameraCaptureContent", "Camera binding failed", e)
            bindingError = e.message ?: context.getString(
                com.trichome.app.R.string.camera_unavailable
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            (context as? CameraCaptureActivity)?.provider?.unbindAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar: cancel, plus a framing guide once the camera is live.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = context.getString(com.trichome.app.R.string.cancel),
                    tint = Color.White
                )
            }
        }

        if (bound) {
            Text(
                text = context.getString(com.trichome.app.R.string.camera_hint),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp, start = 56.dp)
            )
        }

        bindingError?.let { error ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onCancel) {
                    Text(context.getString(com.trichome.app.R.string.close))
                }
            }
        }

        // Shutter button, floating above the bottom inset.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .size(76.dp)
                .background(Color.White.copy(alpha = 0.25f), CircleShape)
                .padding(7.dp)
                .background(
                    if (bound) MaterialTheme.colorScheme.primary else Color.Gray,
                    CircleShape
                )
                .then(
                    if (bound) Modifier.clickableNoRipple(onCapture) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = context.getString(com.trichome.app.R.string.take_photo),
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    role = Role.Button,
    onClick = onClick
)

/** Resolves the [ProcessCameraProvider]; suspends until the async bind completes. */
private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                continuation.resume(future.get())
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(this))
    }
