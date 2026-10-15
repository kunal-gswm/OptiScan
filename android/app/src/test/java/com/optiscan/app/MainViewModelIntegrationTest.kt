package com.optiscan.app

import android.content.Context
import android.graphics.Bitmap
import com.optiscan.app.camera.CameraManager
import com.optiscan.app.data.models.AnalysisResponse
import com.optiscan.app.data.models.StatusTypes
import com.optiscan.app.data.models.TaskTypes
import com.optiscan.app.data.repository.ScanRepository
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.AppSettings
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.navigation.Screen
import com.optiscan.app.ui.localization.AppStrings
import com.optiscan.app.ui.screens.GuidedTaskState
import com.optiscan.app.ui.screens.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fake repository implementation for hermetic unit and integration testing.
 */
class FakeScanRepository : ScanRepository {
    private var isMock: Boolean = false
    var analyzeResult: Result<AnalysisResponse>? = null
    var onDeviceOcrResult: Result<String>? = null
    var capturedTask: String? = null
    var capturedOcrText: String? = null
    var capturedQuestion: String? = null
    var capturedLanguage: String? = null
    var capturedTaskGoal: String? = null
    var capturedStepHistory: String? = null
    var callCount: Int = 0

    override suspend fun analyzeImage(
        imageBytes: ByteArray,
        task: String,
        question: String?,
        ocrText: String?,
        language: String,
        taskGoal: String?,
        stepHistory: String?
    ): Result<AnalysisResponse> {
        callCount++
        capturedTask = task
        capturedQuestion = question
        capturedOcrText = ocrText
        capturedLanguage = language
        capturedTaskGoal = taskGoal
        capturedStepHistory = stepHistory

        return analyzeResult ?: Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = task,
                answer = "A clear hallway with doors on both sides.",
                shouldRecapture = false,
                reason = null
            )
        )
    }

    override suspend fun readTextOnDevice(bitmap: Bitmap): Result<String> {
        return onDeviceOcrResult ?: Result.success("Amoxicillin 500mg - Take one capsule daily")
    }

    var activeUrl: String = "http://10.0.2.2:8000/"
    var configured: Boolean = true

    override suspend fun checkBackendHealth(): Boolean = true

    override fun setMockMode(enabled: Boolean) {
        isMock = enabled
    }

    override fun isMockMode(): Boolean = isMock

    override fun updateBaseUrl(newUrl: String) {
        activeUrl = newUrl
    }

    override fun getBaseUrl(): String = activeUrl

    override fun isConfigured(): Boolean = configured
}

/**
 * Unit tests verifying end-to-end integration logic in MainViewModel:
 * - Live scene description analysis and state transitions
 * - Live document text reading and ML Kit integration
 * - Blur and darkness recapture guidance handling
 * - Network errors and timeout handling with retry capability
 * - Offline mock mode settings propagation
 * - Duplicate-tap request prevention
 * - Request cancellation and state hygiene
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeScanRepository
    private lateinit var viewModel: MainViewModel
    private val sampleBytes = "test_image_bytes".toByteArray()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeScanRepository()
        viewModel = MainViewModel(
            cameraManager = CameraManager(),
            repository = fakeRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDescribeSurroundingsSuccessNavigatesToDescriptionResult() = runTest(testDispatcher) {
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = "An open office area with three desks and an exit door ahead.",
                shouldRecapture = false,
                reason = null
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertNull(viewModel.analysisError.value)

        val currentScreen = viewModel.currentScreen.value
        assertTrue("Expected DescriptionResult screen", currentScreen is Screen.DescriptionResult)
        val result = currentScreen as Screen.DescriptionResult
        assertEquals("An open office area with three desks and an exit door ahead.", result.description)
        assertFalse(result.shouldRecapture)
        assertTrue(result.isLiveResult)
    }

    @Test
    fun testReadTextSuccessNavigatesToReadTextResult() = runTest(testDispatcher) {
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.READ_TEXT,
                answer = "Rx #987654 - Ibuprofen 400mg Tablets",
                shouldRecapture = false,
                reason = null
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.READ_TEXT, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertNull(viewModel.analysisError.value)

        val currentScreen = viewModel.currentScreen.value
        assertTrue("Expected ReadTextResult screen", currentScreen is Screen.ReadTextResult)
        val result = currentScreen as Screen.ReadTextResult
        assertEquals("Rx #987654 - Ibuprofen 400mg Tablets", result.text)
        assertFalse(result.shouldRecapture)
        assertTrue(result.isLiveResult)
    }

    @Test
    fun testBlurryImageTriggersRecaptureGuidance() = runTest(testDispatcher) {
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.RECAPTURE,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = null,
                shouldRecapture = true,
                reason = "Image is too blurry. Please hold steady and recapture."
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        val currentScreen = viewModel.currentScreen.value
        assertTrue("Expected DescriptionResult screen", currentScreen is Screen.DescriptionResult)
        val result = currentScreen as Screen.DescriptionResult
        assertTrue(result.shouldRecapture)
        assertEquals("Image is too blurry. Please hold steady and recapture.", result.recaptureReason)
    }

    @Test
    fun testNetworkFailureSetsActionableErrorAndAllowsRetry() = runTest(testDispatcher) {
        val serverErrorMessage = "Backend server error (500). Please try again later."
        fakeRepository.analyzeResult = Result.failure(Exception(serverErrorMessage))

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertEquals(serverErrorMessage, viewModel.analysisError.value)
        assertTrue(viewModel.currentScreen.value is Screen.Welcome)

        // Retry succeeds
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = "Clear path ahead.",
                shouldRecapture = false,
                reason = null
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertNull(viewModel.analysisError.value)
        assertTrue(viewModel.currentScreen.value is Screen.DescriptionResult)
    }

    @Test
    fun testPreventDuplicateRequestsFromRepeatedTaps() = runTest(testDispatcher) {
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = "Result",
                shouldRecapture = false
            )
        )

        // First tap initiates request
        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        // Second tap immediately while first is running
        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)

        advanceUntilIdle()

        assertEquals("Only one analysis request should have been dispatched", 1, fakeRepository.callCount)
    }

    @Test
    fun testCancelAnalysisCleansUpState() = runTest(testDispatcher) {
        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        viewModel.cancelAnalysis()
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertNull(viewModel.analysisError.value)
    }

    @Test
    fun testOfflineMockModeSettingsPropagation() = runTest(testDispatcher) {
        viewModel.updateSettings(AppSettings(useMockBackend = true))
        assertTrue(fakeRepository.isMockMode())

        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = "[Offline Mock] Simulated response",
                shouldRecapture = false
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        val currentScreen = viewModel.currentScreen.value as Screen.DescriptionResult
        assertFalse("Mock mode should flag isLiveResult as false", currentScreen.isLiveResult)
    }

    @Test
    fun testInteractiveQuestionAnswering() = runTest(testDispatcher) {
        viewModel.setCapturedImage(null, sampleBytes)
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = "The dosage is two tablets every 6 hours.",
                shouldRecapture = false
            )
        )

        var answeredQuery: String? = null
        viewModel.askQuestion(
            query = "What is the recommended dosage?",
            contextContent = "Acetaminophen 500mg"
        ) { answer ->
            answeredQuery = answer
        }

        advanceUntilIdle()
        assertNotNull(answeredQuery)
        assertEquals("The dosage is two tablets every 6 hours.", answeredQuery)
        assertEquals("What is the recommended dosage?", fakeRepository.capturedQuestion)
    }

    @Test
    fun testHindiLanguageSettingPropagatedToAnalysisRequest() = runTest(testDispatcher) {
        viewModel.updateSettings(AppSettings(language = AppLanguage.HINDI))
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = "सामने एक खुला गलियारा है।",
                shouldRecapture = false
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        assertEquals("hi", fakeRepository.capturedLanguage)
        val current = viewModel.currentScreen.value as Screen.DescriptionResult
        assertEquals("सामने एक खुला गलियारा है।", current.description)
    }

    @Test
    fun testHindiReadTextRequestPreservesLanguage() = runTest(testDispatcher) {
        viewModel.updateSettings(AppSettings(language = AppLanguage.HINDI))
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.READ_TEXT,
                answer = "Paracetamol 500mg - Take 2 tablets every 6 hours",
                shouldRecapture = false
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.READ_TEXT, sampleBytes)
        advanceUntilIdle()

        assertEquals("hi", fakeRepository.capturedLanguage)
        val current = viewModel.currentScreen.value as Screen.ReadTextResult
        assertEquals("Paracetamol 500mg - Take 2 tablets every 6 hours", current.text)
    }

    @Test
    fun testHindiFollowUpQuestionPropagatesLanguage() = runTest(testDispatcher) {
        viewModel.updateSettings(AppSettings(language = AppLanguage.HINDI))
        viewModel.setCapturedImage(null, sampleBytes)

        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = "पानी के साथ हर 6 घंटे में 2 caplets लें।",
                shouldRecapture = false
            )
        )

        var resultAnswer: String? = null
        viewModel.askQuestion(
            query = "खुराक क्या है?",
            contextContent = "Acetaminophen 500mg"
        ) { answer ->
            resultAnswer = answer
        }

        advanceUntilIdle()
        assertEquals("hi", fakeRepository.capturedLanguage)
        assertEquals("पानी के साथ हर 6 घंटे में 2 caplets लें।", resultAnswer)
    }

    @Test
    fun testAskQuestionNetworkFailureReturnsCleanErrorAndNeverFabricatesMock() = runTest(testDispatcher) {
        viewModel.updateSettings(AppSettings(language = AppLanguage.ENGLISH))
        viewModel.setCapturedImage(null, sampleBytes)

        fakeRepository.analyzeResult = Result.failure(Exception("Connection timeout"))

        var resultAnswer: String? = null
        viewModel.askQuestion(
            query = "What is the expiration date?",
            contextContent = "Bottle label"
        ) { answer ->
            resultAnswer = answer
        }

        advanceUntilIdle()
        assertNotNull(resultAnswer)
        assertTrue("Error must explain connection/failure", resultAnswer!!.contains("Failed to get answer from AI service"))
        assertFalse("Must never silently fabricate mock scene description on network failure", resultAnswer!!.contains("brightly lit indoor space"))
    }

    @Test
    fun testNavigateToStopsSpeechOnNavigationToCameraOrHome() = runTest(testDispatcher) {
        // Start speech
        viewModel.speak("Reading long paragraph of text aloud...")
        // Navigate to camera
        viewModel.navigateTo(Screen.Camera(ScanTask.READ_TEXT))
        // Speech must be stopped
        assertFalse(viewModel.isSpeaking.value)
    }

    @Test
    fun testAskQuestionWhenNoImageCapturedReturnsHonestNoticeAndNeverMocks() = runTest(testDispatcher) {
        // Ensure no image is captured and mock mode is false
        viewModel.setCapturedImage(null, null)
        fakeRepository.setMockMode(false)

        var resultAnswer: String? = null
        viewModel.askQuestion(
            query = "Where is the door?",
            contextContent = null
        ) { answer ->
            resultAnswer = answer
        }

        advanceUntilIdle()
        assertNotNull(resultAnswer)
        assertEquals("No image available to answer questions about. Please capture a photo first.", resultAnswer)
        assertFalse("Must never return canned mock room descriptions when no image exists", resultAnswer!!.contains("doors on both sides"))
    }

    @Test
    fun testServerConfigurationUpdatesRepositoryUrl() = runTest(testDispatcher) {
        val testUrl = "https://api.optiscan.example.com/"
        viewModel.updateSettings(AppSettings(customBackendUrl = testUrl))

        assertEquals(testUrl, fakeRepository.activeUrl)
    }

    @Test
    fun testOfflinePreservesOnDeviceOcrWhenCloudFails() = runTest(testDispatcher) {
        val sampleBitmap = org.mockito.Mockito.mock(Bitmap::class.java)
        viewModel.setCapturedBitmap(sampleBitmap)

        fakeRepository.onDeviceOcrResult = Result.success("Aspirin 81mg - Daily Dose")
        fakeRepository.analyzeResult = Result.failure(Exception("Network unreachable"))

        viewModel.analyzeCapturedImage(ScanTask.READ_TEXT, sampleBytes)
        advanceUntilIdle()

        // Screen must arrive at ReadTextResult with on-device text preserved
        val currentScreen = viewModel.currentScreen.value
        assertTrue("Must navigate to ReadTextResult on offline fallback", currentScreen is Screen.ReadTextResult)
        val textResult = currentScreen as Screen.ReadTextResult
        assertEquals("Aspirin 81mg - Daily Dose", textResult.text)
        assertFalse(textResult.isLiveResult)
        assertEquals(AppStrings.English.onDeviceFallbackNotice, textResult.recaptureReason)
    }

    @Test
    fun testIsCapturingStateInitiallyFalse() = runTest(testDispatcher) {
        assertFalse("isCapturing must initially be false", viewModel.isCapturing.value)
    }

    @Test
    fun testCapturePhotoWithoutCameraPermissionSpeaksWarningAndAvoidsNavigation() = runTest(testDispatcher) {
        viewModel.setCameraPermission(false)
        val mockContext = org.mockito.Mockito.mock(Context::class.java)

        viewModel.capturePhoto(mockContext, ScanTask.READ_TEXT)
        advanceUntilIdle()

        assertFalse("isCapturing must reset after permission denial check", viewModel.isCapturing.value)
        assertFalse("Must not navigate to ImagePreview without camera permission", viewModel.currentScreen.value is Screen.ImagePreview)
    }

    @Test
    fun testNavigateToStopsSpeechOnNavigationToAskQuestion() = runTest(testDispatcher) {
        viewModel.speak("Ongoing text readout")
        viewModel.navigateTo(Screen.AskQuestion(contextTitle = "Medicine Label", contextContent = "Sample OCR"))
        assertFalse("Speech must be cancelled on navigating to AskQuestion screen", viewModel.isSpeaking.value)
    }

    @Test
    fun testCancelAnalysisNavigatesToCameraAndSpeaksCancellation() = runTest(testDispatcher) {
        viewModel.analyzeCapturedImage(ScanTask.READ_TEXT, sampleBytes)
        viewModel.cancelAnalysis(ScanTask.READ_TEXT)
        advanceUntilIdle()

        assertFalse("isAnalyzing must be false after cancellation", viewModel.isAnalyzing.value)
        val currentScreen = viewModel.currentScreen.value
        assertTrue("Must navigate back to Camera on cancellation", currentScreen is Screen.Camera)
        assertEquals(ScanTask.READ_TEXT, (currentScreen as Screen.Camera).task)
    }

    @Test
    fun testAskQuestionWithOcrTextRoutesReadTextTask() = runTest(testDispatcher) {
        viewModel.setCapturedImage(null, sampleBytes)
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.READ_TEXT,
                answer = "Take 1 tablet every 4 to 6 hours.",
                shouldRecapture = false
            )
        )

        var answeredQuery: String? = null
        val ocrContext = "Ibuprofen 200mg - Take 1 tablet every 4 to 6 hours as needed."
        viewModel.askQuestion(
            query = "What is the frequency of dosage?",
            contextContent = ocrContext
        ) { answer ->
            answeredQuery = answer
        }

        advanceUntilIdle()
        assertNotNull(answeredQuery)
        assertEquals("Take 1 tablet every 4 to 6 hours.", answeredQuery)
        assertEquals(TaskTypes.READ_TEXT, fakeRepository.capturedTask)
        assertEquals(ocrContext, fakeRepository.capturedOcrText)
    }

    @Test
    fun testStartGuidedTaskInitializesObservingStateAndNavigatesToCamera() = runTest(testDispatcher) {
        viewModel.startGuidedTask("make tea", autoNavigateToCamera = true)
        val session = viewModel.guidedTaskSession.value
        assertNotNull(session)
        assertEquals("make tea", session?.goal)
        assertEquals(GuidedTaskState.OBSERVING, session?.state)
        assertEquals(0, session?.currentStepIndex)
        val screen = viewModel.currentScreen.value
        assertTrue(screen is Screen.Camera)
        assertEquals(ScanTask.GUIDED_TASK, (screen as Screen.Camera).task)
    }

    @Test
    fun testAnalyzeCapturedImageGuidedTaskUpdatesSessionAndNavigatesToGuidedTaskScreen() = runTest(testDispatcher) {
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.GUIDED_TASK,
                answer = "Step 1: Plug in the kettle and switch it on.",
                stepIndex = 1,
                isTaskComplete = false,
                needsClarification = false
            )
        )

        viewModel.startGuidedTask("make tea", autoNavigateToCamera = false)
        viewModel.analyzeCapturedImage(ScanTask.GUIDED_TASK, sampleBytes)
        advanceUntilIdle()

        assertFalse("isAnalyzing must be false after response", viewModel.isAnalyzing.value)
        val session = viewModel.guidedTaskSession.value
        assertNotNull(session)
        assertEquals(GuidedTaskState.INSTRUCTING, session?.state)
        assertEquals(1, session?.currentStepIndex)
        assertEquals("Step 1: Plug in the kettle and switch it on.", session?.currentInstruction)
        assertFalse(session?.needsClarification == true)
        assertFalse(session?.isTaskComplete == true)

        val screen = viewModel.currentScreen.value
        assertTrue(screen is Screen.GuidedTask)
        val guidedScreen = screen as Screen.GuidedTask
        assertEquals("make tea", guidedScreen.goal)
        assertEquals("Step 1: Plug in the kettle and switch it on.", guidedScreen.currentInstruction)
        assertEquals(TaskTypes.GUIDED_TASK, fakeRepository.capturedTask)
        assertEquals("make tea", fakeRepository.capturedTaskGoal)
    }

    @Test
    fun testAdvanceGuidedTaskWithCompletionMarksTaskComplete() = runTest(testDispatcher) {
        fakeRepository.setMockMode(true)
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.GUIDED_TASK,
                answer = "Your tea is poured and ready. Enjoy your drink.",
                stepIndex = 5,
                isTaskComplete = true,
                needsClarification = false
            )
        )

        viewModel.setCapturedImage(null, sampleBytes)
        viewModel.startGuidedTask("make tea", autoNavigateToCamera = false)
        viewModel.advanceGuidedTask()
        advanceUntilIdle()

        val session = viewModel.guidedTaskSession.value
        assertNotNull(session)
        assertEquals(GuidedTaskState.COMPLETED, session?.state)
        assertTrue(session?.isTaskComplete == true)
        assertEquals(5, session?.currentStepIndex)
    }

    @Test
    fun testAdvanceGuidedTaskInLiveModeNavigatesToCameraToPreventStaleContext() = runTest(testDispatcher) {
        fakeRepository.setMockMode(false)
        viewModel.startGuidedTask("make tea", autoNavigateToCamera = false)
        viewModel.advanceGuidedTask()

        val screen = viewModel.currentScreen.value
        assertTrue("Live mode advance must navigate to Camera for visual verification", screen is Screen.Camera)
        assertEquals(ScanTask.GUIDED_TASK, (screen as Screen.Camera).task)
    }

    @Test
    fun testStopGuidedTaskHaltsSessionAndNavigatesHome() = runTest(testDispatcher) {
        viewModel.startGuidedTask("make tea", autoNavigateToCamera = false)
        viewModel.stopGuidedTask()

        val session = viewModel.guidedTaskSession.value
        assertNotNull(session)
        assertEquals(GuidedTaskState.STOPPED, session?.state)
        assertTrue(viewModel.currentScreen.value is Screen.Home)
    }

    @Test
    fun testStopGuidedTaskCancelsInflightAnalysisAndDiscardsLateResponse() = runTest(testDispatcher) {
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.GUIDED_TASK,
                answer = "Late response that should be discarded.",
                stepIndex = 2
            )
        )

        viewModel.startGuidedTask("make tea", autoNavigateToCamera = false)
        viewModel.stopGuidedTask()
        viewModel.analyzeCapturedImage(ScanTask.GUIDED_TASK, sampleBytes)
        advanceUntilIdle()

        assertEquals("Session state must remain STOPPED", GuidedTaskState.STOPPED, viewModel.guidedTaskSession.value?.state)
        assertTrue("Screen must remain Home", viewModel.currentScreen.value is Screen.Home)
    }

    @Test
    fun testGuidedTaskPropagatesHindiLanguagePreference() = runTest(testDispatcher) {
        viewModel.updateSettings(AppSettings(language = AppLanguage.HINDI))
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.GUIDED_TASK,
                answer = "कदम 1: केतली का स्विच चालू करें।",
                stepIndex = 1
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.GUIDED_TASK, sampleBytes)
        advanceUntilIdle()

        assertEquals("hi", fakeRepository.capturedLanguage)
        assertEquals(TaskTypes.GUIDED_TASK, fakeRepository.capturedTask)
    }

    @Test
    fun testDescribeSurroundingsWhenBackendUnconfiguredShowsHonestUnavailableMessage() = runTest(testDispatcher) {
        fakeRepository.configured = false
        val expectedMessage = "AI object description is unavailable because no backend is connected. Connect to a local backend to use live AI analysis."

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertEquals(expectedMessage, viewModel.analysisError.value)
        assertEquals(0, fakeRepository.callCount)
    }

    @Test
    fun testDescribeSurroundingsHindiWhenBackendUnconfiguredShowsHonestUnavailableMessage() = runTest(testDispatcher) {
        viewModel.updateSettings(AppSettings(language = AppLanguage.HINDI))
        fakeRepository.configured = false
        val expectedMessage = "AI वस्तु विवरण अनुपलब्ध है क्योंकि कोई बैकएंड कनेक्ट नहीं है। लाइव AI विश्लेषण का उपयोग करने के लिए स्थानीय बैकएंड से कनेक्ट करें।"

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertEquals(expectedMessage, viewModel.analysisError.value)
        assertEquals(0, fakeRepository.callCount)
    }

    @Test
    fun testReadTextWhenBackendUnconfiguredRunsOnDeviceOcrIndependently() = runTest(testDispatcher) {
        fakeRepository.configured = false
        val dummyBitmap = org.mockito.Mockito.mock(Bitmap::class.java)
        viewModel.setCapturedBitmap(dummyBitmap)
        fakeRepository.onDeviceOcrResult = Result.success("Aspirin 81mg - Heart Health")

        viewModel.analyzeCapturedImage(ScanTask.READ_TEXT, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertNull(viewModel.analysisError.value)
        val currentScreen = viewModel.currentScreen.value
        assertTrue("Should navigate to ReadTextResult without backend", currentScreen is Screen.ReadTextResult)
        val resultScreen = currentScreen as Screen.ReadTextResult
        assertEquals("Aspirin 81mg - Heart Health", resultScreen.text)
        assertFalse("On-device OCR is not live cloud result", resultScreen.isLiveResult)
        assertEquals(0, fakeRepository.callCount)
    }

    @Test
    fun testReadTextNoTextDetectedWhenBackendUnconfigured() = runTest(testDispatcher) {
        fakeRepository.configured = false
        val dummyBitmap = org.mockito.Mockito.mock(Bitmap::class.java)
        viewModel.setCapturedBitmap(dummyBitmap)
        fakeRepository.onDeviceOcrResult = Result.success("")

        viewModel.analyzeCapturedImage(ScanTask.READ_TEXT, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        val currentScreen = viewModel.currentScreen.value
        assertTrue(currentScreen is Screen.ReadTextResult)
        assertEquals("No printed text detected in the image.", (currentScreen as Screen.ReadTextResult).text)
    }

    @Test
    fun testLiveNetworkErrorDoesNotFallBackToMockMode() = runTest(testDispatcher) {
        fakeRepository.configured = true
        fakeRepository.analyzeResult = Result.failure(java.io.IOException("Connection refused"))

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertEquals(
            "AI object description is unavailable because no backend is connected. Connect to a local backend to use live AI analysis.",
            viewModel.analysisError.value
        )
        assertFalse("Must never transition to DescriptionResult with mock content on live failure", viewModel.currentScreen.value is Screen.DescriptionResult)
    }

    @Test
    fun testAskQuestionWhenBackendUnconfiguredReturnsUnavailableMessage() = runTest(testDispatcher) {
        fakeRepository.configured = false
        viewModel.setCapturedImage(null, sampleBytes)

        var receivedAnswer: String? = null
        viewModel.askQuestion("What is in front of me?", null) { answer ->
            receivedAnswer = answer
        }

        assertEquals(
            "AI object description is unavailable because no backend is connected. Connect to a local backend to use live AI analysis.",
            receivedAnswer
        )
    }

    @Test
    fun testLocalBackendUrlUpdateAndHealthTest() = runTest(testDispatcher) {
        viewModel.testBackendConnection("http://10.0.2.2:8000/") { success ->
            assertTrue(success)
        }
        advanceUntilIdle()

        assertEquals("http://10.0.2.2:8000/", fakeRepository.getBaseUrl())
    }
}

