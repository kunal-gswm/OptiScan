package com.optiscan.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.optiscan.app.data.preferences.PreferencesManager
import com.optiscan.app.speech.TtsManager
import com.optiscan.app.ui.localization.AppStrings
import com.optiscan.app.ui.screens.MainViewModel
import com.optiscan.app.ui.screens.OptiScanApp
import com.optiscan.app.voice.VoiceController

/**
 * Main application activity initializing permissions, TTS, VoiceController, and the Compose UI root.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var ttsManager: TtsManager
    private var voiceController: VoiceController? = null
    private var activePreviewView: PreviewView? = null

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            viewModel.setCameraPermission(isGranted)
            if (isGranted) {
                activePreviewView?.let { bindCamera(it) }
            }
        }

    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            viewModel.setAudioPermission(isGranted)
            if (isGranted) {
                initVoiceController()
            } else {
                val strings = AppStrings.get(viewModel.settings.value.language)
                viewModel.speak(strings.micPermissionRequiredWarning)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Attach application context
        viewModel.attachContext(this)

        // Initialize preferences and attach to ViewModel
        val preferencesManager = PreferencesManager(this)
        viewModel.attachPreferencesManager(preferencesManager)

        // Initialize TTS
        ttsManager = TtsManager(this)
        viewModel.attachTtsManager(ttsManager)
        viewModel.initAccessibility(this)

        // Check initial camera permission
        val initialCameraGranted = hasCameraPermission()
        viewModel.setCameraPermission(initialCameraGranted)

        // Check initial audio permission and initialize voice controller
        val initialAudioGranted = hasAudioPermission()
        viewModel.setAudioPermission(initialAudioGranted)
        if (initialAudioGranted) {
            initVoiceController()
        } else {
            checkAndRequestAudioPermission()
        }

        setContent {
            OptiScanApp(
                viewModel = viewModel,
                onRequestCameraPermission = { checkAndRequestCameraPermission() },
                onBindCamera = { previewView ->
                    activePreviewView = previewView
                    if (hasCameraPermission()) {
                        bindCamera(previewView)
                    } else {
                        checkAndRequestCameraPermission()
                    }
                }
            )
        }
    }

    private fun initVoiceController() {
        if (voiceController == null) {
            val controller = VoiceController(this, ttsManager)
            voiceController = controller
            viewModel.attachVoiceController(controller)
        }
    }

    override fun onResume() {
        super.onResume()
        voiceController?.onActivityResume()
    }

    override fun onPause() {
        super.onPause()
        voiceController?.onActivityPause()
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            val handled = viewModel.handleVolumeKey(this, keyCode)
            if (handled) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestCameraPermission() {
        if (hasCameraPermission()) {
            viewModel.setCameraPermission(true)
            activePreviewView?.let { bindCamera(it) }
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkAndRequestAudioPermission() {
        if (hasAudioPermission()) {
            viewModel.setAudioPermission(true)
            initVoiceController()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun bindCamera(previewView: PreviewView) {
        viewModel.cameraManager.startCamera(
            context = this,
            lifecycleOwner = this,
            previewView = previewView
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceController?.onDestroy()
        ttsManager.shutdown()
    }
}
