package com.optiscan.app.voice

import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.navigation.Screen

/**
 * Deterministic local voice command parser for English and Hindi.
 * Does not send navigation or control commands to external LLMs.
 */
object VoiceCommandRouter {

    /**
     * Normalize spoken text: lowercase, remove punctuation, strip leading/trailing whitespace.
     */
    fun normalize(input: String): String {
        return input.lowercase().trim()
            .replace(Regex("[.,?!;:।_\\-'\"]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Parse raw recognized speech into a deterministic [VoiceCommand] based on the current screen and language.
     */
    fun parse(rawText: String, currentScreen: Screen, language: AppLanguage): VoiceCommand {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) return VoiceCommand.Unknown("")

        // Strip common politeness fillers like "please", "can you", "कृपया"
        val text = stripFillers(normalized)

        // 1. Context-Sensitive Screen Commands
        when (currentScreen) {
            is Screen.Camera -> {
                if (matchesRetake(text)) return VoiceCommand.RetakePhoto
                if (matchesCapture(text)) return VoiceCommand.CapturePhoto
                if (matchesFlashOn(text)) return VoiceCommand.ToggleFlash(true)
                if (matchesFlashOff(text)) return VoiceCommand.ToggleFlash(false)
                if (matchesCancel(text)) return VoiceCommand.GoBack
            }

            is Screen.ImagePreview -> {
                if (matchesUsePhoto(text)) return VoiceCommand.UsePhoto
                if (matchesRetake(text)) return VoiceCommand.RetakePhoto
                if (matchesCancel(text)) return VoiceCommand.Cancel
            }

            is Screen.ReadTextResult -> {
                if (matchesReadAll(text)) return VoiceCommand.ReadAll
                if (matchesNextLine(text)) return VoiceCommand.NextLine
                if (matchesPreviousLine(text)) return VoiceCommand.PreviousLine
                if (matchesRepeatLine(text)) return VoiceCommand.RepeatLine
                if (matchesPauseReading(text) || matchesStopSpeaking(text)) return VoiceCommand.PauseReading
                if (matchesResumeReading(text)) return VoiceCommand.ResumeReading
                if (matchesReadFaster(text)) return VoiceCommand.ReadFaster
                if (matchesReadSlower(text)) return VoiceCommand.ReadSlower
                if (matchesAskAboutText(text)) return VoiceCommand.AskAboutThisText
                if (matchesScanAgain(text)) return VoiceCommand.ScanAgain
            }

            is Screen.DescriptionResult -> {
                if (matchesScanAgain(text)) return VoiceCommand.ScanAgain
                if (matchesRepeat(text)) return VoiceCommand.Repeat
                if (matchesAskQuestion(text)) return VoiceCommand.AskQuestion()
            }

            is Screen.GuidedTask -> {
                if (matchesScanAgain(text)) return VoiceCommand.ScanAgain
                if (matchesStopTask(text)) return VoiceCommand.StopTask
                if (matchesNextStep(text)) return VoiceCommand.NextStep
                if (matchesRepeatStep(text) || matchesRepeat(text)) return VoiceCommand.RepeatStep
            }

            is Screen.Processing -> {
                if (matchesCancel(text) || matchesStopSpeaking(text)) return VoiceCommand.Cancel
            }

            is Screen.Welcome -> {
                if (matchesGetStarted(text)) return VoiceCommand.GoHome
            }

            is Screen.Home -> {
                if (matchesReadThis(text)) return VoiceCommand.ReadText
                if (matchesDescribeThis(text)) return VoiceCommand.DescribeSurroundings
            }

            else -> {}
        }

        // 2. Global Navigation & System Commands
        return when {
            matchesGuidedTask(text) -> VoiceCommand.GuidedTask
            matchesWhatCanISay(text) -> VoiceCommand.WhatCanISay
            matchesHelp(text) -> VoiceCommand.Help
            matchesGoHome(text) -> VoiceCommand.GoHome
            matchesGoBack(text) -> VoiceCommand.GoBack
            matchesReadText(text) -> VoiceCommand.ReadText
            matchesDescribeSurroundings(text) -> VoiceCommand.DescribeSurroundings
            matchesOpenCamera(text) -> VoiceCommand.OpenCamera()
            matchesAskQuestion(text) -> {
                val query = extractQuestionQuery(text)
                VoiceCommand.AskQuestion(query)
            }
            matchesStopListening(text) -> VoiceCommand.StopListening
            matchesStartListening(text) -> VoiceCommand.StartListening
            matchesStopSpeaking(text) -> VoiceCommand.StopSpeaking
            matchesRepeat(text) -> VoiceCommand.Repeat
            matchesCancel(text) -> VoiceCommand.Cancel
            matchesScanAgain(text) -> VoiceCommand.ScanAgain
            matchesGetStarted(text) -> VoiceCommand.GoHome
            else -> VoiceCommand.Unknown(rawText)
        }
    }

    /**
     * Provide spoken contextual help describing available commands for the current screen.
     */
    fun getHelpText(currentScreen: Screen, language: AppLanguage): String = getContextualHelp(currentScreen, language)

    fun getContextualHelp(currentScreen: Screen, language: AppLanguage): String {
        val isHindi = language == AppLanguage.HINDI
        return when (currentScreen) {
            is Screen.Home -> {
                if (isHindi) {
                    "आप होम स्क्रीन पर हैं। आप बोल सकते हैं: कैमरा खोलो, टेक्स्ट पढ़ो, आसपास बताओ, सवाल पूछो, या गाइडेड टास्क।"
                } else {
                    "You are on Home. You can say: Open camera, Read text, Describe surroundings, Ask a question, or Guided task."
                }
            }

            is Screen.Camera -> {
                if (isHindi) {
                    "आप कैमरा स्क्रीन पर हैं। आप बोल सकते हैं: कैप्चर या फोटो खींचो, फ्लैश चालू, फ्लैश बंद, या वापस जाओ।"
                } else {
                    "You are on Camera. You can say: Capture, Flash on, Flash off, or Go back."
                }
            }

            is Screen.ImagePreview -> {
                if (isHindi) {
                    "आप फोटो प्रीव्यू पर हैं। आप बोल सकते हैं: यह फोटो इस्तेमाल करो, दोबारा फोटो लो, या रद्द करो।"
                } else {
                    "You are on Image Preview. You can say: Use this photo, Retake photo, or Cancel."
                }
            }

            is Screen.ReadTextResult -> {
                if (isHindi) {
                    "आप टेक्स्ट परिणाम पर हैं। आप बोल सकते हैं: पूरा पढ़ो, अगली लाइन, पिछली लाइन, तेज़ पढ़ो, धीमे पढ़ो, इस टेक्स्ट पर सवाल, या फिर से स्कैन करो।"
                } else {
                    "You are on Text Results. You can say: Read all, Next line, Previous line, Read faster, Read slower, Ask about this text, or Scan again."
                }
            }

            is Screen.DescriptionResult -> {
                if (isHindi) {
                    "आप दृश्य विवरण पर हैं। आप बोल सकते हैं: दोबारा बताओ, सवाल पूछो, फिर से स्कैन करो, या होम पर जाओ।"
                } else {
                    "You are on Scene Description. You can say: Repeat description, Ask a question, Scan again, or Go home."
                }
            }

            is Screen.GuidedTask -> {
                if (isHindi) {
                    "आप गाइडेड टास्क में हैं। आप बोल सकते हैं: अगला कदम, दोबारा बोलो, फिर से स्कैन करो, या टास्क बंद करो।"
                } else {
                    "You are in Guided Task Mode. You can say: Next step, Repeat step, Scan again, or Stop task."
                }
            }

            is Screen.Processing -> {
                if (isHindi) {
                    "विश्लेषण हो रहा है। आप रद्द करने के लिए रद्द करो या रोको बोल सकते हैं।"
                } else {
                    "Analysis in progress. You can say Cancel or Stop to return to camera."
                }
            }

            is Screen.Welcome -> {
                if (isHindi) {
                    "ऑप्टीस्कैन में आपका स्वागत है। शुरू करने के लिए शुरू करें बोलें।"
                } else {
                    "Welcome to OptiScan. Say Get started to begin."
                }
            }

            else -> {
                if (isHindi) {
                    "आप बोल सकते हैं: होम पर जाओ, वापस जाओ, या मदद।"
                } else {
                    "You can say: Go home, Go back, or Help."
                }
            }
        }
    }

    // --- Matcher Implementations ---

    private fun stripFillers(text: String): String {
        return text
            .replace(Regex("^(please|can you|could you|would you|कृपया|जरा)\\s+"), "")
            .replace(Regex("\\s+(please|now|कृपया)$"), "")
            .trim()
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        for (kw in keywords) {
            if (text == kw || text.startsWith("$kw ") || text.endsWith(" $kw") || text.contains(" $kw ")) {
                return true
            }
        }
        return false
    }

    private fun matchesHelp(t: String): Boolean =
        t == "help" || containsAny(t, "help", "मदद", "सहायता")

    private fun matchesWhatCanISay(t: String): Boolean =
        containsAny(t, "what can i say", "commands", "what are commands", "options", "what can i do",
            "मैं क्या बोल सकता हूँ", "क्या बोल सकता हूँ", "कमांड्स", "कमांड")

    private fun matchesGoHome(t: String): Boolean =
        containsAny(t, "go home", "home", "back to home", "main menu", "open home",
            "होम पर जाओ", "होम जाओ", "होम", "मुख्य मेनू", "घर जाओ")

    private fun matchesGoBack(t: String): Boolean =
        containsAny(t, "go back", "back", "return", "previous", "go backwards",
            "वापस जाओ", "वापस", "पीछे जाओ", "पीछे", "लौटो")

    private fun matchesOpenCamera(t: String): Boolean =
        containsAny(t, "open camera", "camera", "start camera", "launch camera", "open the camera", "show camera",
            "कैमरा खोलो", "कैमरा", "कैमरा शुरू करो", "कैमरा चालू करो")

    private fun matchesReadText(t: String): Boolean =
        containsAny(t, "read text", "read document", "read label", "read sign", "scan text", "ocr", "read words",
            "टेक्स्ट पढ़ो", "टेक्स्ट", "पढ़ो", "टेक्स्ट स्कैन करो", "किताब पढ़ो", "लेबल पढ़ो", "लिखा हुआ पढ़ो")

    private fun matchesReadThis(t: String): Boolean =
        containsAny(t, "read this", "read this text", "यह पढ़ो", "इसे पढ़ो")

    private fun matchesDescribeSurroundings(t: String): Boolean =
        containsAny(t, "describe surroundings", "describe scene", "what's around me", "what is around me",
            "describe object", "describe objects", "explore surroundings", "explore", "scan surroundings",
            "आसपास बताओ", "आसपास क्या है", "यहाँ क्या है", "यहाँ क्या दिख रहा है", "विवरण दो", "दृश्य बताओ", "आसपास", "माहौल बताओ")

    private fun matchesDescribeThis(t: String): Boolean =
        containsAny(t, "describe this", "what is this", "what's this", "what do you see", "describe it",
            "यह बताओ", "इसे बताओ", "यह क्या है", "यहाँ क्या है", "क्या दिख रहा है")

    private fun matchesAskQuestion(t: String): Boolean =
        containsAny(t, "ask a question", "ask question", "ask", "question", "i have a question",
            "सवाल पूछो", "प्रश्न पूछो", "सवाल", "प्रश्न", "एक सवाल है", "पूछो")

    private fun matchesGuidedTask(t: String): Boolean =
        containsAny(t, "guided task", "step by step help", "step by step", "task assistance", "guide me", "start task",
            "स्टेप बाय स्टेप मदद", "गाइडेड टास्क", "स्टेप बाय स्टेप", "कार्य सहायता", "टास्क")

    private fun matchesRepeat(t: String): Boolean =
        containsAny(t, "repeat", "say that again", "say again", "repeat that", "what did you say", "repeat description", "say it again",
            "दोबारा बोलो", "फिर से बोलो", "दोहराएं", "दोबारा", "फिर से", "क्या कहा")

    private fun matchesStopSpeaking(t: String): Boolean =
        containsAny(t, "stop speaking", "stop reading", "be quiet", "silence", "shut up", "pause reading", "quiet",
            "बोलना बंद करो", "चुप रहो", "शांत", "पढ़ना बंद करो", "बंद करो", "आवाज़ बंद करो")

    private fun matchesStartListening(t: String): Boolean =
        containsAny(t, "start listening", "listen", "resume listening", "wake up", "start assistant",
            "सुनना शुरू करो", "सुनना चालू करो", "सुनो", "जागो", "शुरू करो")

    private fun matchesStopListening(t: String): Boolean =
        containsAny(t, "stop listening", "pause listening", "stop assistant", "pause assistant", "go to sleep",
            "सुनना बंद करो", "सुनना रोको", "सहायक बंद करो", "सो जाओ")

    private fun matchesCancel(t: String): Boolean =
        containsAny(t, "cancel", "never mind", "abort", "discard", "stop", "रद्द करो", "कैंसिल", "रद्द", "रोको")

    private fun matchesCapture(t: String): Boolean =
        containsAny(t, "capture", "take photo", "take picture", "take a photo", "snap", "click", "shoot", "capture photo",
            "फोटो खींचो", "फोटो लो", "कैप्चर", "खींचो", "क्लिक करो", "तस्वीर लो")

    private fun matchesRetake(t: String): Boolean =
        containsAny(t, "retake", "retake photo", "try again", "take another photo", "take another",
            "दोबारा फोटो लो", "फिर से खींचो", "दोबारा खींचो", "फिर से लो", "दोबारा")

    private fun matchesFlashOn(t: String): Boolean =
        containsAny(t, "turn flash on", "turn on light", "flash on", "enable flash", "torch on", "light on",
            "फ्लैश चालू करो", "फ्लैश चालू", "फ्लैश ऑन", "लाइट जलाओ", "टॉर्च ऑन", "फ्लैश शुरू करो")

    private fun matchesFlashOff(t: String): Boolean =
        containsAny(t, "turn flash off", "turn off light", "flash off", "disable flash", "torch off", "light off",
            "फ्लैश बंद करो", "फ्लैश बंद", "फ्लैश ऑफ", "लाइट बंद करो", "टॉर्च ऑफ")

    private fun matchesUsePhoto(t: String): Boolean =
        containsAny(t, "use this photo", "use photo", "use this", "analyze", "process", "proceed", "confirm", "yes", "accept",
            "फोटो ठीक है", "विश्लेषण करो", "यह फोटो इस्तेमाल करो", "फोटो उपयोग करो", "हाँ", "आगे बढ़ो", "उपयोग करो", "मंजूर")

    private fun matchesReadAll(t: String): Boolean =
        containsAny(t, "read all", "read everything", "read all text", "read whole text",
            "पूरा पढ़ो", "सब पढ़ो", "पूरा टेक्स्ट पढ़ो", "सब कुछ पढ़ो")

    private fun matchesNextLine(t: String): Boolean =
        containsAny(t, "next line", "next sentence", "next", "read next", "forward",
            "अगली लाइन", "आगे पढ़ो", "अगला", "अगली")

    private fun matchesPreviousLine(t: String): Boolean =
        containsAny(t, "previous line", "previous sentence", "prev line", "back a line", "previous",
            "पिछली लाइन", "पीछे पढ़ो", "पिछला", "पिछली")

    private fun matchesRepeatLine(t: String): Boolean =
        containsAny(t, "repeat line", "current line", "say line again", "यह लाइन फिर से",
            "यह लाइन दोबारा बोलो", "लाइन दोहराओ", "यही लाइन बोलो", "दोबारा पढ़ो")

    private fun matchesPauseReading(t: String): Boolean =
        containsAny(t, "pause reading", "pause", "रोकें", "रोको", "पढ़ना रोको")

    private fun matchesResumeReading(t: String): Boolean =
        containsAny(t, "resume reading", "resume", "continue reading", "continue", "पढ़ना जारी रखो",
            "जारी रखो", "आगे पढ़ो", "फिर से शुरू करो")

    private fun matchesReadFaster(t: String): Boolean =
        containsAny(t, "read faster", "faster", "speed up", "increase speed",
            "तेज़ पढ़ो", "तेज पढ़ो", "तेज़ी से", "तेज़ करो", "तेज़")

    private fun matchesReadSlower(t: String): Boolean =
        containsAny(t, "read slower", "slower", "slow down", "decrease speed",
            "धीमे पढ़ो", "धीरे पढ़ो", "धीमे", "धीरे")

    private fun matchesAskAboutText(t: String): Boolean =
        containsAny(t, "ask about this text", "ask about text", "ask about this", "ask question about text",
            "इस टेक्स्ट के बारे में पूछो", "टेक्स्ट पर सवाल", "इसके बारे में पूछो")

    private fun matchesScanAgain(t: String): Boolean =
        containsAny(t, "scan again", "capture another", "read another", "take another", "scan workspace again",
            "फिर से स्कैन करो", "दूसरा फोटो लो", "दोबारा स्कैन करो", "फिर से")

    private fun matchesNextStep(t: String): Boolean =
        containsAny(t, "next step", "scan workspace", "advance", "next", "completed step", "i did it",
            "अगला कदम", "अगला स्टेप", "कार्यक्षेत्र स्कैन करो", "आगे बढ़ो", "हो गया", "कर लिया")

    private fun matchesRepeatStep(t: String): Boolean =
        containsAny(t, "repeat step", "repeat instruction", "यह स्टेप दोबारा बोलो", "निर्देश दोहराओ", "स्टेप दोबारा बताओ", "फिर से बताओ")

    private fun matchesStopTask(t: String): Boolean =
        containsAny(t, "stop task", "end task", "quit task", "टास्क बंद करो", "कार्य समाप्त करो")

    private fun matchesGetStarted(t: String): Boolean =
        containsAny(t, "get started", "start", "begin", "let's go", "शुरू करें", "शुरू करो", "आगे बढ़ो")

    private fun extractQuestionQuery(text: String): String? {
        val prefixes = listOf(
            "ask a question about", "ask a question", "ask question", "ask", "i want to know",
            "सवाल पूछो", "प्रश्न पूछो", "पूछो कि", "पूछो"
        )
        for (prefix in prefixes) {
            if (text.startsWith("$prefix ")) {
                val candidate = text.substring(prefix.length).trim()
                if (candidate.isNotBlank()) return candidate
            }
        }
        return null
    }
}
