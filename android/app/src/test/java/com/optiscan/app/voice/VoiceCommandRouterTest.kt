package com.optiscan.app.voice

import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for deterministic bilingual voice command parsing and contextual routing.
 */
class VoiceCommandRouterTest {

    @Test
    fun testEnglishGlobalNavigationCommands() {
        assertEquals(VoiceCommand.Help, VoiceCommandRouter.parse("help", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.WhatCanISay, VoiceCommandRouter.parse("what can i say", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.WhatCanISay, VoiceCommandRouter.parse("what can i do", Screen.Home, AppLanguage.ENGLISH))

        assertEquals(VoiceCommand.GoHome, VoiceCommandRouter.parse("go home", Screen.Camera(ScanTask.READ_TEXT), AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.GoHome, VoiceCommandRouter.parse("take me home", Screen.Settings, AppLanguage.ENGLISH))

        assertEquals(VoiceCommand.GoBack, VoiceCommandRouter.parse("go back", Screen.Camera(ScanTask.READ_TEXT), AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.GoBack, VoiceCommandRouter.parse("back", Screen.Settings, AppLanguage.ENGLISH))

        assertEquals(VoiceCommand.OpenCamera(ScanTask.DESCRIBE_SURROUNDINGS), VoiceCommandRouter.parse("open camera", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.ReadText, VoiceCommandRouter.parse("read text", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.ReadText, VoiceCommandRouter.parse("read document", Screen.Home, AppLanguage.ENGLISH))

        assertEquals(VoiceCommand.DescribeSurroundings, VoiceCommandRouter.parse("describe surroundings", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.DescribeSurroundings, VoiceCommandRouter.parse("what is around me", Screen.Home, AppLanguage.ENGLISH))

        assertEquals(VoiceCommand.GuidedTask, VoiceCommandRouter.parse("guided task", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.GuidedTask, VoiceCommandRouter.parse("step by step help", Screen.Home, AppLanguage.ENGLISH))
    }

    @Test
    fun testHindiGlobalNavigationCommands() {
        assertEquals(VoiceCommand.Help, VoiceCommandRouter.parse("मदद", Screen.Home, AppLanguage.HINDI))
        assertEquals(VoiceCommand.WhatCanISay, VoiceCommandRouter.parse("मैं क्या बोल सकता हूँ", Screen.Home, AppLanguage.HINDI))

        assertEquals(VoiceCommand.GoHome, VoiceCommandRouter.parse("होम पर जाओ", Screen.Camera(ScanTask.READ_TEXT), AppLanguage.HINDI))
        assertEquals(VoiceCommand.GoHome, VoiceCommandRouter.parse("घर जाओ", Screen.Settings, AppLanguage.HINDI))

        assertEquals(VoiceCommand.GoBack, VoiceCommandRouter.parse("वापस जाओ", Screen.Camera(ScanTask.READ_TEXT), AppLanguage.HINDI))
        assertEquals(VoiceCommand.GoBack, VoiceCommandRouter.parse("पीछे जाओ", Screen.Settings, AppLanguage.HINDI))

        assertEquals(VoiceCommand.OpenCamera(ScanTask.DESCRIBE_SURROUNDINGS), VoiceCommandRouter.parse("कैमरा खोलो", Screen.Home, AppLanguage.HINDI))
        assertEquals(VoiceCommand.ReadText, VoiceCommandRouter.parse("टेक्स्ट पढ़ो", Screen.Home, AppLanguage.HINDI))
        assertEquals(VoiceCommand.ReadText, VoiceCommandRouter.parse("दस्तावेज़ पढ़ो", Screen.Home, AppLanguage.HINDI))

        assertEquals(VoiceCommand.DescribeSurroundings, VoiceCommandRouter.parse("आसपास बताओ", Screen.Home, AppLanguage.HINDI))
        assertEquals(VoiceCommand.DescribeSurroundings, VoiceCommandRouter.parse("यहाँ क्या है", Screen.Home, AppLanguage.HINDI))

        assertEquals(VoiceCommand.GuidedTask, VoiceCommandRouter.parse("स्टेप बाय स्टेप मदद", Screen.Home, AppLanguage.HINDI))
    }

    @Test
    fun testSpeechControlCommands() {
        // Repeat
        assertEquals(VoiceCommand.Repeat, VoiceCommandRouter.parse("repeat", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.Repeat, VoiceCommandRouter.parse("say that again", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.Repeat, VoiceCommandRouter.parse("दोबारा बोलो", Screen.Home, AppLanguage.HINDI))
        assertEquals(VoiceCommand.Repeat, VoiceCommandRouter.parse("फिर से बोलो", Screen.Home, AppLanguage.HINDI))

        // Stop Speaking
        assertEquals(VoiceCommand.StopSpeaking, VoiceCommandRouter.parse("stop speaking", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.StopSpeaking, VoiceCommandRouter.parse("shut up", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.StopSpeaking, VoiceCommandRouter.parse("चुप रहो", Screen.Home, AppLanguage.HINDI))
        assertEquals(VoiceCommand.StopSpeaking, VoiceCommandRouter.parse("बोलना बंद करो", Screen.Home, AppLanguage.HINDI))

        // Stop Listening
        assertEquals(VoiceCommand.StopListening, VoiceCommandRouter.parse("stop listening", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.StopListening, VoiceCommandRouter.parse("pause listening", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.StopListening, VoiceCommandRouter.parse("सुनना बंद करो", Screen.Home, AppLanguage.HINDI))
        assertEquals(VoiceCommand.StopListening, VoiceCommandRouter.parse("सुनना रोको", Screen.Home, AppLanguage.HINDI))

        // Start Listening
        assertEquals(VoiceCommand.StartListening, VoiceCommandRouter.parse("start listening", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.StartListening, VoiceCommandRouter.parse("resume listening", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.StartListening, VoiceCommandRouter.parse("सुनना शुरू करो", Screen.Home, AppLanguage.HINDI))
        assertEquals(VoiceCommand.StartListening, VoiceCommandRouter.parse("सुनना चालू करो", Screen.Home, AppLanguage.HINDI))

        // Cancel
        assertEquals(VoiceCommand.Cancel, VoiceCommandRouter.parse("cancel", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.Cancel, VoiceCommandRouter.parse("never mind", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.Cancel, VoiceCommandRouter.parse("रद्द करो", Screen.Home, AppLanguage.HINDI))
    }

    @Test
    fun testCameraContextualCommands() {
        val cameraScreen = Screen.Camera(ScanTask.DESCRIBE_SURROUNDINGS)

        assertEquals(VoiceCommand.CapturePhoto, VoiceCommandRouter.parse("capture", cameraScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.CapturePhoto, VoiceCommandRouter.parse("take photo", cameraScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.CapturePhoto, VoiceCommandRouter.parse("snap", cameraScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.CapturePhoto, VoiceCommandRouter.parse("फोटो खींचो", cameraScreen, AppLanguage.HINDI))
        assertEquals(VoiceCommand.CapturePhoto, VoiceCommandRouter.parse("तस्वीर लो", cameraScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.RetakePhoto, VoiceCommandRouter.parse("retake", cameraScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.RetakePhoto, VoiceCommandRouter.parse("दोबारा खींचो", cameraScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.ToggleFlash(true), VoiceCommandRouter.parse("flash on", cameraScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.ToggleFlash(true), VoiceCommandRouter.parse("turn on light", cameraScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.ToggleFlash(true), VoiceCommandRouter.parse("फ्लैश चालू", cameraScreen, AppLanguage.HINDI))
        assertEquals(VoiceCommand.ToggleFlash(true), VoiceCommandRouter.parse("लाइट जलाओ", cameraScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.ToggleFlash(false), VoiceCommandRouter.parse("flash off", cameraScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.ToggleFlash(false), VoiceCommandRouter.parse("turn off light", cameraScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.ToggleFlash(false), VoiceCommandRouter.parse("फ्लैश बंद", cameraScreen, AppLanguage.HINDI))
        assertEquals(VoiceCommand.ToggleFlash(false), VoiceCommandRouter.parse("लाइट बंद करो", cameraScreen, AppLanguage.HINDI))
    }

    @Test
    fun testImagePreviewContextualCommands() {
        val previewScreen = Screen.ImagePreview(ScanTask.READ_TEXT)

        assertEquals(VoiceCommand.UsePhoto, VoiceCommandRouter.parse("use photo", previewScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.UsePhoto, VoiceCommandRouter.parse("analyze", previewScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.UsePhoto, VoiceCommandRouter.parse("proceed", previewScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.UsePhoto, VoiceCommandRouter.parse("फोटो ठीक है", previewScreen, AppLanguage.HINDI))
        assertEquals(VoiceCommand.UsePhoto, VoiceCommandRouter.parse("विश्लेषण करो", previewScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.RetakePhoto, VoiceCommandRouter.parse("retake", previewScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.RetakePhoto, VoiceCommandRouter.parse("दोबारा खींचो", previewScreen, AppLanguage.HINDI))
    }

    @Test
    fun testOcrResultsContextualCommands() {
        val readTextScreen = Screen.ReadTextResult("Sample text line 1\nSample text line 2")

        assertEquals(VoiceCommand.ReadAll, VoiceCommandRouter.parse("read all", readTextScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.ReadAll, VoiceCommandRouter.parse("पूरा पढ़ो", readTextScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.NextLine, VoiceCommandRouter.parse("next line", readTextScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.NextLine, VoiceCommandRouter.parse("अगली लाइन", readTextScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.PreviousLine, VoiceCommandRouter.parse("previous line", readTextScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.PreviousLine, VoiceCommandRouter.parse("पिछली लाइन", readTextScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.RepeatLine, VoiceCommandRouter.parse("repeat line", readTextScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.RepeatLine, VoiceCommandRouter.parse("यह लाइन फिर से", readTextScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.PauseReading, VoiceCommandRouter.parse("pause reading", readTextScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.PauseReading, VoiceCommandRouter.parse("रोको", readTextScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.ResumeReading, VoiceCommandRouter.parse("resume reading", readTextScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.ResumeReading, VoiceCommandRouter.parse("पढ़ना जारी रखो", readTextScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.ReadFaster, VoiceCommandRouter.parse("read faster", readTextScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.ReadFaster, VoiceCommandRouter.parse("तेज़ पढ़ो", readTextScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.ReadSlower, VoiceCommandRouter.parse("read slower", readTextScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.ReadSlower, VoiceCommandRouter.parse("धीमे पढ़ो", readTextScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.AskAboutThisText, VoiceCommandRouter.parse("ask about this text", readTextScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.AskAboutThisText, VoiceCommandRouter.parse("इस टेक्स्ट के बारे में पूछो", readTextScreen, AppLanguage.HINDI))
    }

    @Test
    fun testGuidedTaskContextualCommands() {
        val guidedScreen = Screen.GuidedTask("make tea", "Fill the kettle with water.")

        assertEquals(VoiceCommand.NextStep, VoiceCommandRouter.parse("next step", guidedScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.NextStep, VoiceCommandRouter.parse("i did it", guidedScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.NextStep, VoiceCommandRouter.parse("अगला स्टेप", guidedScreen, AppLanguage.HINDI))
        assertEquals(VoiceCommand.NextStep, VoiceCommandRouter.parse("हो गया", guidedScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.RepeatStep, VoiceCommandRouter.parse("repeat step", guidedScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.RepeatStep, VoiceCommandRouter.parse("स्टेप दोबारा बताओ", guidedScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.StopTask, VoiceCommandRouter.parse("stop task", guidedScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.StopTask, VoiceCommandRouter.parse("टास्क बंद करो", guidedScreen, AppLanguage.HINDI))

        assertEquals(VoiceCommand.ScanAgain, VoiceCommandRouter.parse("scan again", guidedScreen, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.ScanAgain, VoiceCommandRouter.parse("दोबारा स्कैन करो", guidedScreen, AppLanguage.HINDI))
    }

    @Test
    fun testPoliteFillerWordsStripped() {
        assertEquals(VoiceCommand.OpenCamera(ScanTask.DESCRIBE_SURROUNDINGS), VoiceCommandRouter.parse("please open camera", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.DescribeSurroundings, VoiceCommandRouter.parse("can you please describe surroundings", Screen.Home, AppLanguage.ENGLISH))
        assertEquals(VoiceCommand.Help, VoiceCommandRouter.parse("कृपया मदद", Screen.Home, AppLanguage.HINDI))
    }

    @Test
    fun testAskQuestionQueryExtraction() {
        val cmd = VoiceCommandRouter.parse("ask a question what is in front of me", Screen.Home, AppLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.AskQuestion)
        assertEquals("what is in front of me", (cmd as VoiceCommand.AskQuestion).query)

        val hindiCmd = VoiceCommandRouter.parse("सवाल पूछो सामने क्या है", Screen.Home, AppLanguage.HINDI)
        assertTrue(hindiCmd is VoiceCommand.AskQuestion)
        assertEquals("सामने क्या है", (hindiCmd as VoiceCommand.AskQuestion).query)
    }

    @Test
    fun testUnknownCommandFallback() {
        val cmd = VoiceCommandRouter.parse("random unrecognized utterance xyz", Screen.Home, AppLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.Unknown)
        assertEquals("random unrecognized utterance xyz", (cmd as VoiceCommand.Unknown).rawText)
    }

    @Test
    fun testHelpTextGeneration() {
        val homeHelpEn = VoiceCommandRouter.getHelpText(Screen.Home, AppLanguage.ENGLISH)
        assertTrue(homeHelpEn.contains("Open camera"))
        assertTrue(homeHelpEn.contains("Read text"))

        val homeHelpHi = VoiceCommandRouter.getHelpText(Screen.Home, AppLanguage.HINDI)
        assertTrue(homeHelpHi.contains("कैमरा खोलो"))
        assertTrue(homeHelpHi.contains("टेक्स्ट पढ़ो"))

        val cameraHelpEn = VoiceCommandRouter.getHelpText(Screen.Camera(ScanTask.READ_TEXT), AppLanguage.ENGLISH)
        assertTrue(cameraHelpEn.contains("Capture"))
        assertTrue(cameraHelpEn.contains("Flash on"))

        val cameraHelpHi = VoiceCommandRouter.getHelpText(Screen.Camera(ScanTask.READ_TEXT), AppLanguage.HINDI)
        assertTrue(cameraHelpHi.contains("कैप्चर"))
        assertTrue(cameraHelpHi.contains("फ्लैश चालू"))
    }
}
