package com.optiscan.app.ui.components.aurora

/**
 * State machine for the OptiScan Aurora Voice Assistant.
 * Directly mapped to live application events (mic listening, image analysis, TTS output).
 */
enum class AssistantState {
    /** Ambient, resting state with gentle slow breathing */
    IDLE,

    /** Active microphone listening; responds to audio amplitude / speech recognition */
    LISTENING,

    /** Backend Gemini analysis, OCR processing, or prompt evaluation */
    PROCESSING,

    /** Voice command parsing and routing in progress */
    COMMAND_PROCESSING,

    /** Waiting for user speech after silence or prompt */
    WAITING_FOR_USER,

    /** Spoken TTS response actively playing back to the user */
    SPEAKING,

    /** Brief confirmation bloom on successful action or completed task */
    SUCCESS,

    /** Restrained warning/error pulse on network failure or recognition timeout */
    ERROR,

    /** Emergency stop or user cancellation; animation settles immediately */
    STOPPED
}
