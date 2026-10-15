package com.optiscan.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.ResultCard
import com.optiscan.app.ui.components.ScreenTitle
import com.optiscan.app.ui.components.SpeechControls
import com.optiscan.app.ui.components.StatusLevel
import com.optiscan.app.ui.components.StatusMessage
import com.optiscan.app.ui.localization.AppStrings
import com.optiscan.app.ui.theme.BackgroundNearBlack

/**
 * Screen for Guided Task Mode (AROMA / Be My Eyes mixed-initiative model).
 *
 * Provides single-step procedural instructions with conservative safety checks,
 * audible repeat triggers, workspace re-scanning, and prompt task cancellation.
 */
@Composable
fun GuidedTaskScreen(
    goal: String = "make tea",
    stepIndex: Int = 1,
    instruction: String = "",
    needsClarification: Boolean = false,
    isTaskComplete: Boolean = false,
    isSpeaking: Boolean = false,
    language: AppLanguage = AppLanguage.ENGLISH,
    onNextStep: () -> Unit,
    onRepeatStep: () -> Unit,
    onScanAgain: () -> Unit,
    onAskQuestion: () -> Unit,
    onStopTask: () -> Unit,
    onReadAloud: (String) -> Unit,
    onStopSpeech: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.get(language)
    val displayInstruction = instruction.ifBlank {
        if (language == AppLanguage.HINDI) "शुरू करने के लिए अपने कार्यक्षेत्र को स्कैन करें।" else "Scan your workspace to begin."
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundNearBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Screen Title + Step Counter
            ScreenTitle(
                title = strings.guidedTaskTitle,
                subtitle = if (isTaskComplete) {
                    strings.taskCompleted
                } else if (stepIndex > 0) {
                    "${strings.stepLabel} $stepIndex: $goal"
                } else {
                    goal
                }
            )

            // Clarification Warning / Success Status / Instruction Card
            when {
                isTaskComplete -> {
                    StatusMessage(
                        title = strings.taskCompleted,
                        message = displayInstruction,
                        level = StatusLevel.SUCCESS
                    )
                }

                needsClarification -> {
                    StatusMessage(
                        title = strings.clarificationPrompt,
                        message = displayInstruction,
                        level = StatusLevel.WARNING
                    )
                }

                else -> {
                    ResultCard(
                        title = if (stepIndex > 0) "${strings.instructionTitle} (${strings.stepLabel} $stepIndex)" else strings.observationTitle,
                        content = displayInstruction
                    )
                }
            }

            // Safety Warning Notice (conservative precaution around hot surfaces, appliances, liquids)
            StatusMessage(
                title = strings.safetyWarningTitle,
                message = strings.safetyWarningMessage,
                level = StatusLevel.INFO
            )

            // Speech Playback Controller
            SpeechControls(
                isSpeaking = isSpeaking,
                onReadAloud = { onReadAloud(displayInstruction) },
                onStopSpeech = onStopSpeech,
                readAloudText = strings.readAnswerAloud,
                stopText = strings.stopSpeech
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action 1: Scan Workspace for Next Step
            if (!isTaskComplete) {
                OptiScanButton(
                    text = strings.scanWorkspaceForNextStep,
                    onClick = onNextStep,
                    variant = ButtonVariant.PRIMARY,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDesc = strings.scanWorkspaceNextStepContentDesc
                )
            }

            // Action 2: Repeat Step
            OptiScanButton(
                text = strings.repeatStep,
                onClick = onRepeatStep,
                variant = ButtonVariant.SURFACE,
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                contentDesc = "${strings.repeatStep}. Read current instruction aloud again."
            )

            // Action 3: Scan Workspace Again
            OptiScanButton(
                text = strings.scanAgain,
                onClick = onScanAgain,
                variant = ButtonVariant.SECONDARY,
                icon = Icons.Default.CameraAlt,
                contentDesc = "${strings.scanAgain}. Point camera at workspace to update visual context."
            )

            // Action 4: Ask Question About Task
            OptiScanButton(
                text = strings.askQuestionTitle,
                onClick = onAskQuestion,
                variant = ButtonVariant.SURFACE,
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                contentDesc = "${strings.askQuestionTitle}. Ask a specific question about the current task."
            )

            // Action 5: Stop Task
            OptiScanButton(
                text = strings.stopTask,
                onClick = onStopTask,
                variant = ButtonVariant.SURFACE,
                icon = Icons.Default.Close,
                contentDesc = "${strings.stopTask}. Stop guided task session and return home."
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
