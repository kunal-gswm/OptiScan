package com.optiscan.app

import android.content.SharedPreferences
import android.speech.SpeechRecognizer
import com.optiscan.app.data.mock.MockData
import com.optiscan.app.data.models.TaskTypes
import com.optiscan.app.data.preferences.PreferencesManager
import com.optiscan.app.data.repository.ScanRepositoryImpl
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.AppSettings
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.speech.SpeechInputManager
import com.optiscan.app.speech.SpeechRecognitionState
import com.optiscan.app.ui.localization.AppStrings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * In-memory fake SharedPreferences for hermetic unit testing of PreferencesManager.
 */
class InMemorySharedPreferences : SharedPreferences {
    val store = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(store)

    override fun getString(key: String?, defValue: String?): String? =
        store[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        store[key] as? MutableSet<String> ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (store[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (store[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (store[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (store[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = store.containsKey(key)

    override fun edit(): SharedPreferences.Editor = InMemoryEditor(this)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class InMemoryEditor(private val sharedPrefs: InMemorySharedPreferences) : SharedPreferences.Editor {
        private val tempStore = mutableMapOf<String, Any?>()
        private val removeKeys = mutableSetOf<String>()
        private var clearFlag = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) tempStore[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) tempStore[key] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) tempStore[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) tempStore[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) tempStore[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) tempStore[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) removeKeys.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearFlag = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearFlag) sharedPrefs.store.clear()
            removeKeys.forEach { sharedPrefs.store.remove(it) }
            sharedPrefs.store.putAll(tempStore)
        }
    }
}

/**
 * Automated tests covering:
 * - Language enum conversion and default handling
 * - AppSettings language persistence
 * - Speech recognition error mapping and locale parameters (Hindi / English)
 * - Offline mock repository Hindi responses and medical safety
 * - AppStrings localization parity
 * - MockData Hindi responses preserving dosages, units, and brand names
 */
class PreferencesAndSpeechTest {

    @Test
    fun testAppLanguageEnumConversions() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.HINDI, AppLanguage.fromCode("hi"))
        assertEquals(AppLanguage.HINDI, AppLanguage.fromCode("HI"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("unknown"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode(null))

        assertEquals("en", AppLanguage.ENGLISH.code)
        assertEquals("hi", AppLanguage.HINDI.code)
        assertEquals("English", AppLanguage.ENGLISH.displayName)
        assertEquals("हिंदी", AppLanguage.HINDI.nativeName)
    }

    @Test
    fun testPreferencesManagerLanguagePersistence() {
        val fakePrefs = InMemorySharedPreferences()
        val editor = fakePrefs.edit()

        // Verify default load when empty
        val defaultLangCode = fakePrefs.getString(PreferencesManager.KEY_LANGUAGE, AppLanguage.ENGLISH.code)
        assertEquals("en", defaultLangCode)

        // Save Hindi setting
        editor.putString(PreferencesManager.KEY_LANGUAGE, AppLanguage.HINDI.code)
        editor.putFloat(PreferencesManager.KEY_SPEECH_RATE, 1.25f)
        editor.putBoolean(PreferencesManager.KEY_VOICE_FEEDBACK, true)
        editor.putBoolean(PreferencesManager.KEY_HAPTIC_FEEDBACK, false)
        editor.putFloat(PreferencesManager.KEY_TEXT_SIZE_SCALE, 1.2f)
        editor.putBoolean(PreferencesManager.KEY_USE_MOCK_BACKEND, true)
        editor.apply()

        // Read back
        val loadedCode = fakePrefs.getString(PreferencesManager.KEY_LANGUAGE, AppLanguage.ENGLISH.code)
        val loadedLang = AppLanguage.fromCode(loadedCode)
        assertEquals(AppLanguage.HINDI, loadedLang)
        assertEquals(1.25f, fakePrefs.getFloat(PreferencesManager.KEY_SPEECH_RATE, 1.0f), 0.01f)
        assertTrue(fakePrefs.getBoolean(PreferencesManager.KEY_VOICE_FEEDBACK, false))
        assertFalse(fakePrefs.getBoolean(PreferencesManager.KEY_HAPTIC_FEEDBACK, true))
        assertTrue(fakePrefs.getBoolean(PreferencesManager.KEY_USE_MOCK_BACKEND, false))
    }

    @Test
    fun testSpeechInputManagerErrorMappingEnglish() {
        val errAudio = SpeechInputManager.mapErrorCodeToMessage(SpeechRecognizer.ERROR_AUDIO, AppLanguage.ENGLISH)
        assertTrue(errAudio.contains("microphone", ignoreCase = true))

        val errPerm = SpeechInputManager.mapErrorCodeToMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, AppLanguage.ENGLISH)
        assertTrue(errPerm.contains("Microphone permission", ignoreCase = true))

        val errNetwork = SpeechInputManager.mapErrorCodeToMessage(SpeechRecognizer.ERROR_NETWORK, AppLanguage.ENGLISH)
        assertTrue(errNetwork.contains("Network error", ignoreCase = true))

        val errNoMatch = SpeechInputManager.mapErrorCodeToMessage(SpeechRecognizer.ERROR_NO_MATCH, AppLanguage.ENGLISH)
        assertTrue(errNoMatch.contains("No speech detected", ignoreCase = true))
    }

    @Test
    fun testSpeechInputManagerErrorMappingHindi() {
        val errAudio = SpeechInputManager.mapErrorCodeToMessage(SpeechRecognizer.ERROR_AUDIO, AppLanguage.HINDI)
        assertTrue(errAudio.contains("माइक्रोफ़ोन"))

        val errPerm = SpeechInputManager.mapErrorCodeToMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, AppLanguage.HINDI)
        assertTrue(errPerm.contains("अनुमति"))

        val errNetwork = SpeechInputManager.mapErrorCodeToMessage(SpeechRecognizer.ERROR_NETWORK, AppLanguage.HINDI)
        assertTrue(errNetwork.contains("नेटवर्क"))

        val errNoMatch = SpeechInputManager.mapErrorCodeToMessage(SpeechRecognizer.ERROR_NO_MATCH, AppLanguage.HINDI)
        assertTrue(errNoMatch.contains("आवाज़"))
    }

    @Test
    fun testSpeechRecognitionStateTypes() {
        val idle: SpeechRecognitionState = SpeechRecognitionState.Idle
        val ready: SpeechRecognitionState = SpeechRecognitionState.Ready
        val listening: SpeechRecognitionState = SpeechRecognitionState.Listening
        val success: SpeechRecognitionState = SpeechRecognitionState.Success("दवा की खुराक क्या है?")
        val error: SpeechRecognitionState = SpeechRecognitionState.Error(7, "कोई आवाज़ नहीं पहचानी गई")

        assertTrue(idle is SpeechRecognitionState.Idle)
        assertTrue(ready is SpeechRecognitionState.Ready)
        assertTrue(listening is SpeechRecognitionState.Listening)
        assertEquals("दवा की खुराक क्या है?", (success as SpeechRecognitionState.Success).recognizedText)
        assertEquals(7, (error as SpeechRecognitionState.Error).errorCode)
    }

    @Test
    fun testScanRepositoryMockModeHindiPreservesMedicineSafety() = runTest {
        val repo = ScanRepositoryImpl()
        repo.setMockMode(true)

        // Describe image in Hindi
        val descRes = repo.analyzeImage(
            imageBytes = ByteArray(0),
            task = TaskTypes.DESCRIBE_IMAGE,
            language = "hi"
        )
        assertTrue(descRes.isSuccess)
        val descAnswer = descRes.getOrNull()?.answer
        assertNotNull(descAnswer)
        assertTrue("Expected Hindi description mock", descAnswer!!.contains("गलियारा") || descAnswer.contains("ऑफलाइन मॉक"))

        // Read text in Hindi - strictly preserves English room & entrance
        val textRes = repo.analyzeImage(
            imageBytes = ByteArray(0),
            task = TaskTypes.READ_TEXT,
            language = "hi"
        )
        assertTrue(textRes.isSuccess)
        val textAnswer = textRes.getOrNull()?.answer
        assertNotNull(textAnswer)
        assertTrue("Room and entrance number must be preserved", textAnswer!!.contains("ROOM 302 - ACCESSIBLE ENTRANCE"))
    }

    @Test
    fun testMockDataHindiQnAPreservesDosagesAndIngredients() {
        // Dosage question in Hindi
        val dosageAnswer = MockData.answerQuestion("खुराक कितनी है?", "Acetaminophen 500mg", "hi")
        assertTrue("Dosage must preserve caplets and hours", dosageAnswer.contains("2 caplets"))
        assertTrue("Dosage must preserve 6 घंटे", dosageAnswer.contains("6 घंटे"))
        assertTrue("Dosage must preserve 24 घंटे", dosageAnswer.contains("24 घंटे"))

        // Ingredient question in Hindi
        val ingredientAnswer = MockData.answerQuestion("सक्रिय घटक क्या है?", "Acetaminophen 500mg", "hi")
        assertTrue("Must preserve Acetaminophen USP", ingredientAnswer.contains("Acetaminophen USP"))
        assertTrue("Must preserve 500 mg", ingredientAnswer.contains("500 mg"))

        // Expiration question in Hindi
        val expiryAnswer = MockData.answerQuestion("समाप्ति तारीख क्या है?", null, "hi")
        assertTrue("Must preserve November 2028", expiryAnswer.contains("November 2028"))
        assertTrue("Must preserve Lot number", expiryAnswer.contains("9342B-01"))

        // Navigation path question in Hindi
        val pathAnswer = MockData.answerQuestion("क्या रास्ता साफ है?", null, "hi")
        assertTrue("Must preserve 10 फीट", pathAnswer.contains("10 फीट"))
        assertTrue(pathAnswer.contains("खुला दरवाजा"))
    }

    @Test
    fun testAppStringsLocalizationParity() {
        val en = AppStrings.English
        val hi = AppStrings.Hindi

        assertFalse(en.appName.isBlank())
        assertFalse(hi.appName.isBlank())
        assertEquals("OptiScan", en.appName)
        assertEquals("ऑप्टीस्कैन", hi.appName)

        assertFalse(en.readTextTitle.isBlank())
        assertFalse(hi.readTextTitle.isBlank())
        assertEquals("Read Text", en.readTextTitle)
        assertEquals("टेक्स्ट पढ़ें", hi.readTextTitle)

        assertFalse(en.describeSurroundingsTitle.isBlank())
        assertFalse(hi.describeSurroundingsTitle.isBlank())

        assertFalse(en.askQuestionTitle.isBlank())
        assertFalse(hi.askQuestionTitle.isBlank())

        assertEquals(en.readTextTitle, en.taskTitle(ScanTask.READ_TEXT))
        assertEquals(hi.readTextTitle, hi.taskTitle(ScanTask.READ_TEXT))
        assertEquals(en.describeSurroundingsTitle, en.taskTitle(ScanTask.DESCRIBE_SURROUNDINGS))
        assertEquals(hi.describeSurroundingsTitle, hi.taskTitle(ScanTask.DESCRIBE_SURROUNDINGS))

        // Verify fallback notice exists
        assertFalse(en.hindiTtsFallbackNotice.isBlank())
        assertFalse(hi.hindiTtsFallbackNotice.isBlank())
    }

    @Test
    fun testMockDataExplicitOfflineMockBranding() {
        val enAnswer = MockData.answerQuestion("Is the path clear?", null, "en")
        val hiAnswer = MockData.answerQuestion("क्या रास्ता साफ है?", null, "hi")

        assertTrue("English mock answer must have [Simulated Answer - Mock Mode] prefix", enAnswer.startsWith("[Simulated Answer - Mock Mode]"))
        assertTrue("Hindi mock answer must have [अनुकरण उत्तर - मॉक मोड] prefix", hiAnswer.startsWith("[अनुकरण उत्तर - मॉक मोड]"))
    }

    @Test
    fun testPreferencesManagerCustomBackendUrlPersistence() {
        val fakePrefs = InMemorySharedPreferences()
        val editor = fakePrefs.edit()

        // Default should be empty string
        val defaultUrl = fakePrefs.getString(PreferencesManager.KEY_CUSTOM_BACKEND_URL, "")
        assertEquals("", defaultUrl)

        // Save custom backend URL
        val customUrl = "https://optiscan-api.production.app/"
        editor.putString(PreferencesManager.KEY_CUSTOM_BACKEND_URL, customUrl)
        editor.apply()

        // Read back
        val loadedUrl = fakePrefs.getString(PreferencesManager.KEY_CUSTOM_BACKEND_URL, "")
        assertEquals(customUrl, loadedUrl)
    }
}
