package com.optiscan.app.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.optiscan.app.data.api.ApiClient
import com.optiscan.app.data.api.OptiScanApi
import com.optiscan.app.data.models.AnalysisResponse
import com.optiscan.app.data.models.StatusTypes
import com.optiscan.app.data.models.TaskTypes
import com.optiscan.app.ocr.OcrService
import com.optiscan.app.utils.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Repository interface defining visual analysis, OCR, and server configuration operations.
 */
interface ScanRepository {
    suspend fun analyzeImage(
        imageBytes: ByteArray,
        task: String,
        question: String? = null,
        ocrText: String? = null,
        language: String = "en",
        taskGoal: String? = null,
        stepHistory: String? = null
    ): Result<AnalysisResponse>

    suspend fun readTextOnDevice(bitmap: Bitmap): Result<String>

    suspend fun checkBackendHealth(): Boolean

    fun setMockMode(enabled: Boolean)

    fun isMockMode(): Boolean

    fun updateBaseUrl(newUrl: String)

    fun getBaseUrl(): String

    fun isConfigured(): Boolean
}

/**
 * Concrete repository implementation with offline mock support and ML Kit integration.
 */
class ScanRepositoryImpl(
    private var api: OptiScanApi = ApiClient.createService(),
    ocrService: OcrService? = null,
    private var useMockBackend: Boolean = false
) : ScanRepository {

    private val ocrService: OcrService by lazy { ocrService ?: OcrService() }

    private val tag = "ScanRepository"
    private val gson = Gson()

    override fun setMockMode(enabled: Boolean) {
        this.useMockBackend = enabled
        Log.i(tag, "Mock mode set to: $enabled")
    }

    override fun isMockMode(): Boolean = useMockBackend

    override fun updateBaseUrl(newUrl: String) {
        if (newUrl.isNotBlank()) {
            NetworkConfig.activeBaseUrl = newUrl
            this.api = ApiClient.createService(NetworkConfig.activeBaseUrl)
            Log.i(tag, "Updated backend URL to: ${NetworkConfig.activeBaseUrl}")
        }
    }

    override fun getBaseUrl(): String = NetworkConfig.activeBaseUrl

    override fun isConfigured(): Boolean = NetworkConfig.isConfigured()

    override suspend fun checkBackendHealth(): Boolean = withContext(Dispatchers.IO) {
        if (useMockBackend) return@withContext true
        if (!isConfigured()) return@withContext false
        try {
            val response = api.checkHealth()
            response.status == "ok"
        } catch (e: Exception) {
            Log.w(tag, "Backend health check failed: ${e.message}")
            false
        }
    }

    override suspend fun analyzeImage(
        imageBytes: ByteArray,
        task: String,
        question: String?,
        ocrText: String?,
        language: String,
        taskGoal: String?,
        stepHistory: String?
    ): Result<AnalysisResponse> = withContext(Dispatchers.IO) {
        if (useMockBackend) {
            Log.i(tag, "Returning mock backend response for task: $task, language: $language")
            val isHindi = language.equals("hi", ignoreCase = true)
            val cleanUser = (question ?: "").trim().lowercase()
            val historyStr = (stepHistory ?: "").trim().lowercase()

            val mockResponse = when (task) {
                TaskTypes.DESCRIBE_IMAGE -> {
                    val mockAnswer = if (isHindi) {
                        "[अनुकरण विवरण - मॉक मोड] दोनों तरफ दरवाजों वाला एक सीधा और साफ इनडोर गलियारा। आगे का रास्ता पूरी तरह खुला और सुरक्षित है।"
                    } else {
                        "[Simulated Description - Mock Mode] A clear indoor hallway with doors on both sides. Straight ahead is unobstructed."
                    }
                    AnalysisResponse(
                        status = StatusTypes.SUCCESS,
                        task = task,
                        answer = mockAnswer,
                        shouldRecapture = false,
                        reason = if (isHindi) "ऑफलाइन मॉक बैकएंड मोड सक्षम है।" else "Offline mock backend mode is enabled."
                    )
                }
                TaskTypes.READ_TEXT -> {
                    val mockAnswer = if (isHindi) {
                        "[अनुकरण लेबल - मॉक मोड] ROOM 302 - ACCESSIBLE ENTRANCE"
                    } else {
                        "[Simulated Label - Mock Mode] ROOM 302 - ACCESSIBLE ENTRANCE"
                    }
                    AnalysisResponse(
                        status = StatusTypes.SUCCESS,
                        task = task,
                        answer = mockAnswer,
                        shouldRecapture = false,
                        reason = if (isHindi) "ऑफलाइन मॉक बैकएंड मोड सक्षम है।" else "Offline mock backend mode is enabled."
                    )
                }
                TaskTypes.GUIDED_TASK -> {
                    if (cleanUser.contains("stop") || cleanUser.contains("रोक")) {
                        AnalysisResponse(
                            status = StatusTypes.SUCCESS,
                            task = task,
                            answer = if (isHindi) "टास्क सत्र रोक दिया गया है।" else "Task session stopped.",
                            isTaskComplete = true,
                            reason = "Guided task stopped by user."
                        )
                    } else if (historyStr.isBlank() && cleanUser.isBlank()) {
                        AnalysisResponse(
                            status = StatusTypes.SUCCESS,
                            task = task,
                            answer = if (isHindi) {
                                "[अनुकरण मार्गदर्शन - मॉक मोड] मुझे मेज पर एक कप और एक केतली दिखाई दे रही है। सुरक्षित रूप से शुरू करने के लिए: क्या केतली में पानी भरा है?"
                            } else {
                                "[Simulated Guidance - Mock Mode] I can see a cup and a kettle on the table. To begin safely: can you confirm if the kettle contains water?"
                            },
                            stepIndex = 0,
                            needsClarification = true,
                            reason = "Initial workspace observation requiring clarification."
                        )
                    } else if (!historyStr.contains("step 1") && !historyStr.contains("कदम 1")) {
                        AnalysisResponse(
                            status = StatusTypes.SUCCESS,
                            task = task,
                            answer = if (isHindi) {
                                "[अनुकरण मार्गदर्शन - मॉक मोड] कदम 1: केतली का प्लग लगाएं और स्विच चालू करें। उबलने के बाद केतली के अपने आप बंद (क्लिक) होने की प्रतीक्षा करें। तापमान जांचने के लिए केतली को न छुएं।"
                            } else {
                                "[Simulated Guidance - Mock Mode] Step 1: Ensure the kettle is plugged in and switched on. Wait for it to click off automatically once boiled. Do not touch the body of the kettle to check heat."
                            },
                            stepIndex = 1,
                            reason = "First procedural step."
                        )
                    } else if (!historyStr.contains("step 2") && !historyStr.contains("कदम 2")) {
                        AnalysisResponse(
                            status = StatusTypes.SUCCESS,
                            task = task,
                            answer = if (isHindi) {
                                "[अनुकरण मार्गदर्शन - मॉक मोड] कदम 2: कप में एक टी बैग रखें।"
                            } else {
                                "[Simulated Guidance - Mock Mode] Step 2: Place a tea bag into your cup on the table."
                            },
                            stepIndex = 2,
                            reason = "Second procedural step."
                        )
                    } else if (!historyStr.contains("step 3") && !historyStr.contains("कदम 3")) {
                        AnalysisResponse(
                            status = StatusTypes.SUCCESS,
                            task = task,
                            answer = if (isHindi) {
                                "[अनुकरण मार्गदर्शन - मॉक मोड] कदम 3: सावधानी से केतली से गर्म पानी कप में तीन-चौथाई भरें। केवल हैंडल पकड़ें, गर्म तरल से सावधान रहें।"
                            } else {
                                "[Simulated Guidance - Mock Mode] Step 3: Carefully pour hot water from the kettle into the cup up to three quarters full. Grip only the insulated handle; exercise caution with hot liquids."
                            },
                            stepIndex = 3,
                            reason = "Third procedural step with safety caution."
                        )
                    } else if (!historyStr.contains("step 4") && !historyStr.contains("कदम 4")) {
                        AnalysisResponse(
                            status = StatusTypes.SUCCESS,
                            task = task,
                            answer = if (isHindi) {
                                "[अनुकरण मार्गदर्शन - मॉक मोड] कदम 4: टी बैग को 2 मिनट के लिए भीगने दें, फिर धागा पकड़कर सावधानी से निकाल लें।"
                            } else {
                                "[Simulated Guidance - Mock Mode] Step 4: Allow the tea bag to steep for 2 minutes, then remove it carefully by the string."
                            },
                            stepIndex = 4,
                            reason = "Fourth procedural step."
                        )
                    } else {
                        AnalysisResponse(
                            status = StatusTypes.SUCCESS,
                            task = task,
                            answer = if (isHindi) {
                                "[अनुकरण मार्गदर्शन - मॉक मोड] आपकी चाय तैयार है! सावधानी से आनंद लें।"
                            } else {
                                "[Simulated Guidance - Mock Mode] Your tea is ready! Enjoy your drink with care."
                            },
                            stepIndex = 5,
                            isTaskComplete = true,
                            reason = "Guided task complete."
                        )
                    }
                }
                else -> {
                    AnalysisResponse(
                        status = StatusTypes.SUCCESS,
                        task = task,
                        answer = "[Offline Mock] Task processed.",
                        shouldRecapture = false
                    )
                }
            }
            return@withContext Result.success(mockResponse)
        }

        // Release/production check: verify that a valid server URL is configured
        if (!isConfigured()) {
            val isHindi = language.equals("hi", ignoreCase = true)
            val unconfiguredMsg = if (isHindi) {
                if (task == TaskTypes.DESCRIBE_IMAGE) {
                    "AI वस्तु विवरण अनुपलब्ध है क्योंकि कोई बैकएंड कनेक्ट नहीं है। लाइव AI विश्लेषण का उपयोग करने के लिए स्थानीय बैकएंड से कनेक्ट करें।"
                } else {
                    "क्लाउड बैकएंड सर्वर कॉन्फ़िगर नहीं है। कृपया सेटिंग्स में सर्वर URL दर्ज करें या ऑफलाइन मॉक मोड सक्षम करें।"
                }
            } else {
                if (task == TaskTypes.DESCRIBE_IMAGE) {
                    "AI object description is unavailable because no backend is connected. Connect to a local backend to use live AI analysis."
                } else {
                    "Hosted backend URL is not configured. Please enter a valid server URL in Settings or enable Offline Mock Mode."
                }
            }
            return@withContext Result.failure(IllegalStateException(unconfiguredMsg))
        }

        try {
            val imageRequestBody = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", "camera_capture.jpg", imageRequestBody)
            val taskPart = task.toRequestBody("text/plain".toMediaTypeOrNull())
            val questionPart = question?.toRequestBody("text/plain".toMediaTypeOrNull())
            val ocrPart = ocrText?.toRequestBody("text/plain".toMediaTypeOrNull())
            val langPart = language.toRequestBody("text/plain".toMediaTypeOrNull())
            val goalPart = taskGoal?.toRequestBody("text/plain".toMediaTypeOrNull())
            val historyPart = stepHistory?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.analyzeImage(imagePart, taskPart, questionPart, ocrPart, langPart, goalPart, historyPart)
            Result.success(response)
        } catch (e: HttpException) {
            Log.e(tag, "HTTP error from backend: ${e.code()}", e)
            val errorBody = try {
                e.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            val parsedResponse = try {
                if (!errorBody.isNullOrBlank()) {
                    gson.fromJson(errorBody, AnalysisResponse::class.java)
                } else null
            } catch (_: Exception) {
                null
            }

            if (parsedResponse != null) {
                if (parsedResponse.status == StatusTypes.RECAPTURE || parsedResponse.shouldRecapture) {
                    Result.success(parsedResponse)
                } else {
                    val message = parsedResponse.reason ?: "Server error (${e.code()})"
                    Result.failure(Exception(message, e))
                }
            } else {
                val message = when (e.code()) {
                    400 -> "Bad request. The captured image or task parameters were invalid."
                    502 -> "Vision AI provider unavailable. Please try again."
                    500 -> "Backend server error. Please try again later."
                    else -> "Server error (${e.code()}). Please try again."
                }
                Result.failure(Exception(message, e))
            }
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Connection timed out", e)
            val isHindi = language.equals("hi", ignoreCase = true)
            val timeoutMsg = if (isHindi) {
                if (task == TaskTypes.DESCRIBE_IMAGE) {
                    "AI वस्तु विवरण अनुपलब्ध है क्योंकि कोई बैकएंड कनेक्ट नहीं है। लाइव AI विश्लेषण का उपयोग करने के लिए स्थानीय बैकएंड से कनेक्ट करें।"
                } else {
                    "AI बैकएंड से कनेक्शन का समय समाप्त हो गया। कृपया अपना नेटवर्क जांचें।"
                }
            } else {
                if (task == TaskTypes.DESCRIBE_IMAGE) {
                    "AI object description is unavailable because no backend is connected. Connect to a local backend to use live AI analysis."
                } else {
                    "Connection to AI backend timed out. Please check your network and try again."
                }
            }
            Result.failure(Exception(timeoutMsg, e))
        } catch (e: IOException) {
            Log.e(tag, "Network I/O failure", e)
            val isHindi = language.equals("hi", ignoreCase = true)
            val connMsg = if (isHindi) {
                if (task == TaskTypes.DESCRIBE_IMAGE) {
                    "AI वस्तु विवरण अनुपलब्ध है क्योंकि कोई बैकएंड कनेक्ट नहीं है। लाइव AI विश्लेषण का उपयोग करने के लिए स्थानीय बैकएंड से कनेक्ट करें।"
                } else {
                    "OptiScan बैकएंड से कनेक्ट नहीं हो सका। कृपया अपना नेटवर्क कनेक्शन जांचें।"
                }
            } else {
                if (task == TaskTypes.DESCRIBE_IMAGE) {
                    "AI object description is unavailable because no backend is connected. Connect to a local backend to use live AI analysis."
                } else {
                    "Could not connect to OptiScan backend. Please verify your connection."
                }
            }
            Result.failure(Exception(connMsg, e))
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error in analyzeImage", e)
            Result.failure(e)
        }
    }

    override suspend fun readTextOnDevice(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        ocrService.recognizeText(bitmap)
    }
}
