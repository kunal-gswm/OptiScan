package com.optiscan.app.voice

import android.content.Context
import android.speech.SpeechRecognizer
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.navigation.Screen
import com.optiscan.app.speech.SpeechInputManager
import com.optiscan.app.speech.SpeechRecognitionState
import com.optiscan.app.speech.TtsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Fake implementation of SpeechInputManager for testing VoiceController in JVM without Android SpeechRecognizer.
 */
class FakeSpeechInputManager(context: Context) : SpeechInputManager(context) {
    var isAvailable: Boolean = true
    var startListeningCalledCount = 0
    var stopListeningCalledCount = 0
    var cancelCalledCount = 0
    var destroyCalledCount = 0
    var lastLanguage: AppLanguage? = null

    override fun isRecognitionAvailable(): Boolean = isAvailable

    override fun startListening(language: AppLanguage) {
        startListeningCalledCount++
        lastLanguage = language
        onStateChanged?.invoke(SpeechRecognitionState.Ready)
    }

    override fun stopListening() {
        stopListeningCalledCount++
    }

    override fun cancel() {
        cancelCalledCount++
        onStateChanged?.invoke(SpeechRecognitionState.Idle)
    }

    override fun destroy() {
        destroyCalledCount++
    }

    fun emitState(state: SpeechRecognitionState) {
        onStateChanged?.invoke(state)
    }

    fun emitRms(rmsDb: Float) {
        onRmsChanged?.invoke(rmsDb)
    }
}

/**
 * Fake implementation of TtsManager for testing VoiceController audio coordination.
 */
class FakeTtsManager(context: Context) : TtsManager(context) {
    var speakCount = 0
    var lastSpokenText: String? = null
    var stopCount = 0
    private var simulatedSpeaking = false

    override fun speak(text: String, queueMode: Int) {
        speakCount++
        lastSpokenText = text
        simulatedSpeaking = true
        onSpeechStateChanged?.invoke(true)
    }

    override fun stop() {
        stopCount++
        simulatedSpeaking = false
        onSpeechStateChanged?.invoke(false)
    }

    override fun isSpeaking(): Boolean = simulatedSpeaking

    fun completeSpeech() {
        simulatedSpeaking = false
        onSpeechStateChanged?.invoke(false)
        onSpeechCompleted?.invoke()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceControllerTest {

    private lateinit var mockContext: Context
    private lateinit var fakeTts: FakeTtsManager
    private lateinit var fakeSpeechInput: FakeSpeechInputManager
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var controller: VoiceController

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        fakeTts = FakeTtsManager(mockContext)
        fakeSpeechInput = FakeSpeechInputManager(mockContext)

        controller = VoiceController(
            context = mockContext,
            ttsManager = fakeTts,
            scope = testScope,
            speechInputManagerFactory = { fakeSpeechInput }
        )
    }

    @Test
    fun testInitialStateIsIdle() {
        assertEquals(VoiceControllerState.IDLE, controller.voiceState.value)
        assertFalse(controller.isActivityForeground)
        assertFalse(controller.hasAudioPermission)
        assertFalse(controller.isUserStopped)
    }

    @Test
    fun testStartListeningWithoutPermissionEntersError() {
        controller.hasAudioPermission = false
        var feedbackMsg: String? = null
        controller.onFeedbackMessage = { msg -> feedbackMsg = msg }

        controller.startListening()

        assertEquals(VoiceControllerState.ERROR, controller.voiceState.value)
        assertTrue(feedbackMsg != null && feedbackMsg!!.contains("Microphone permission"))
    }

    @Test
    fun testStartListeningWhenForegroundedEntersListening() {
        controller.hasAudioPermission = true
        controller.onActivityResume()
        testScope.advanceTimeBy(600) // allow resume delay

        assertEquals(VoiceControllerState.LISTENING, controller.voiceState.value)
        assertTrue(fakeSpeechInput.startListeningCalledCount >= 1)
    }

    @Test
    fun testSpeechAmplitudeNormalizedMapping() {
        controller.hasAudioPermission = true
        controller.onActivityResume()
        testScope.advanceTimeBy(600)

        fakeSpeechInput.emitRms(10f)
        assertEquals(1.0f, controller.micAmplitude.value, 0.01f)

        fakeSpeechInput.emitRms(-2f)
        assertEquals(0.0f, controller.micAmplitude.value, 0.01f)

        fakeSpeechInput.emitRms(4f)
        assertEquals(0.5f, controller.micAmplitude.value, 0.01f)
    }

    @Test
    fun testSpeechSuccessDispatchesCommand() {
        controller.hasAudioPermission = true
        controller.onActivityResume()
        testScope.advanceTimeBy(600)

        var dispatchedCommand: VoiceCommand? = null
        controller.onCommandDispatched = { cmd, _ ->
            dispatchedCommand = cmd
        }

        fakeSpeechInput.emitState(SpeechRecognitionState.Success("open camera"))

        assertEquals(VoiceControllerState.COMMAND_PROCESSING, controller.voiceState.value)
        assertEquals("open camera", controller.lastRecognizedText.value)
        assertEquals(VoiceCommand.OpenCamera(ScanTask.DESCRIBE_SURROUNDINGS), dispatchedCommand)
    }

    @Test
    fun testStopSpeakingCommandStopsTtsAndResumesListening() {
        controller.hasAudioPermission = true
        controller.onActivityResume()
        testScope.advanceTimeBy(600)

        // Simulate TTS vocalizing
        fakeTts.speak("Test speech")
        assertEquals(VoiceControllerState.SPEAKING, controller.voiceState.value)

        // User speaks "stop speaking"
        fakeSpeechInput.emitState(SpeechRecognitionState.Success("stop speaking"))
        testScope.advanceTimeBy(300)

        assertTrue(fakeTts.stopCount >= 1)
        assertEquals(VoiceControllerState.LISTENING, controller.voiceState.value)
    }

    @Test
    fun testStopListeningCommandEntersStoppedState() {
        controller.hasAudioPermission = true
        controller.onActivityResume()
        testScope.advanceTimeBy(600)

        fakeSpeechInput.emitState(SpeechRecognitionState.Success("stop listening"))

        assertTrue(controller.isUserStopped)
        assertTrue(fakeTts.lastSpokenText != null && fakeTts.lastSpokenText!!.contains("paused"))
    }

    @Test
    fun testTtsEchoPreventionAndAutomaticResumption() {
        controller.hasAudioPermission = true
        controller.onActivityResume()
        testScope.advanceTimeBy(600)
        assertEquals(VoiceControllerState.LISTENING, controller.voiceState.value)

        // TTS starts speaking -> recognition must cease immediately
        fakeTts.speak("Welcome to OptiScan")
        assertEquals(VoiceControllerState.SPEAKING, controller.voiceState.value)
        assertEquals(0f, controller.micAmplitude.value, 0.001f)

        // TTS finishes speaking -> recognition resumes after settling delay
        fakeTts.completeSpeech()
        testScope.advanceTimeBy(400) // settle delay + margin
        assertEquals(VoiceControllerState.LISTENING, controller.voiceState.value)
    }

    @Test
    fun testConsecutiveSilenceBackoffSettlesInWaitingForUser() {
        controller.hasAudioPermission = true
        controller.onActivityResume()
        testScope.advanceTimeBy(600)

        // Silence 1
        fakeSpeechInput.emitState(SpeechRecognitionState.Error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, "Timeout"))
        testScope.advanceTimeBy(500)
        assertEquals(VoiceControllerState.LISTENING, controller.voiceState.value)

        // Silence 2
        fakeSpeechInput.emitState(SpeechRecognitionState.Error(SpeechRecognizer.ERROR_NO_MATCH, "No match"))
        testScope.advanceTimeBy(500)
        assertEquals(VoiceControllerState.LISTENING, controller.voiceState.value)

        // Silence 3 -> Should enter WAITING_FOR_USER to prevent battery churn
        fakeSpeechInput.emitState(SpeechRecognitionState.Error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, "Timeout"))
        testScope.advanceTimeBy(500)
        assertEquals(VoiceControllerState.WAITING_FOR_USER, controller.voiceState.value)
    }

    @Test
    fun testLifecyclePauseAndResume() {
        controller.hasAudioPermission = true
        controller.onActivityResume()
        testScope.advanceTimeBy(600)
        assertTrue(controller.isActivityForeground)
        assertEquals(VoiceControllerState.LISTENING, controller.voiceState.value)

        // Pause
        controller.onActivityPause()
        assertFalse(controller.isActivityForeground)
        assertEquals(VoiceControllerState.IDLE, controller.voiceState.value)
        assertTrue(fakeSpeechInput.cancelCalledCount >= 1)

        // Resume
        controller.onActivityResume()
        testScope.advanceTimeBy(600)
        assertTrue(controller.isActivityForeground)
        assertEquals(VoiceControllerState.LISTENING, controller.voiceState.value)

        // Destroy
        controller.onDestroy()
        assertFalse(controller.isActivityForeground)
        assertTrue(fakeSpeechInput.destroyCalledCount >= 1)
    }
}
