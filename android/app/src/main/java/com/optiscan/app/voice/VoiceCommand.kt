package com.optiscan.app.voice

import com.optiscan.app.navigation.ScanTask

/**
 * Deterministic voice commands recognized across the application.
 */
sealed interface VoiceCommand {
    // Global Navigation & Control Commands
    data object Help : VoiceCommand
    data object WhatCanISay : VoiceCommand
    data object GoHome : VoiceCommand
    data object GoBack : VoiceCommand
    data class OpenCamera(val task: ScanTask = ScanTask.DESCRIBE_SURROUNDINGS) : VoiceCommand
    data object ReadText : VoiceCommand
    data object DescribeSurroundings : VoiceCommand
    data class AskQuestion(val query: String? = null) : VoiceCommand
    data object GuidedTask : VoiceCommand
    data object Repeat : VoiceCommand
    data object StopSpeaking : VoiceCommand
    data object StartListening : VoiceCommand
    data object StopListening : VoiceCommand
    data object Cancel : VoiceCommand

    // Context-sensitive Camera Commands
    data object CapturePhoto : VoiceCommand
    data object RetakePhoto : VoiceCommand
    data class ToggleFlash(val enable: Boolean) : VoiceCommand

    // Context-sensitive Image Preview Commands
    data object UsePhoto : VoiceCommand

    // Context-sensitive OCR Results Commands
    data object ReadAll : VoiceCommand
    data object NextLine : VoiceCommand
    data object PreviousLine : VoiceCommand
    data object RepeatLine : VoiceCommand
    data object PauseReading : VoiceCommand
    data object ResumeReading : VoiceCommand
    data object ReadFaster : VoiceCommand
    data object ReadSlower : VoiceCommand
    data object AskAboutThisText : VoiceCommand

    // Context-sensitive Scene & Guided Task Commands
    data object ScanAgain : VoiceCommand
    data object NextStep : VoiceCommand
    data object RepeatStep : VoiceCommand
    data object StopTask : VoiceCommand

    // Unrecognized speech
    data class Unknown(val rawText: String) : VoiceCommand
}
