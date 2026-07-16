package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import com.ai.assistance.operit.R

@Composable
internal fun Modifier.userMessageAccessibilityHeading(): Modifier =
    accessibilityHeading(stringResource(R.string.chat_accessibility_user_message))

@Composable
internal fun Modifier.aiReplyAccessibilityHeading(): Modifier =
    accessibilityHeading(stringResource(R.string.chat_accessibility_ai_reply))

private fun Modifier.accessibilityHeading(label: String): Modifier =
    semantics {
        heading()
        text = AnnotatedString(label)
    }
