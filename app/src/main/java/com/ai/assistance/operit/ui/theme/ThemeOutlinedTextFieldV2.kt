package com.ai.assistance.operit.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2

/**
 * Applies the V2 input skin to simple string-backed Material fields without replacing their
 * editing, focus, IME, or accessibility implementation.
 */
@Composable
internal fun ThemeOutlinedTextFieldV2(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    placeholder: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = LocalTextStyle.current,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
) {
    var isFocused by remember { mutableStateOf(false) }
    val state = resolveThemeOutlinedTextFieldStateV2(enabled, isError, isFocused)
    val skin =
        LocalThemePackageUiRuntimeV2.current.componentSkin(
            component = ThemeComponentCatalogV2.INPUT,
            state = state,
        )
    val disabledContent = skin.content.copy(alpha = 0.38f)
    val placeholderContent = skin.content.copy(alpha = 0.64f)
    val resolvedTextColor = if (enabled) skin.content else disabledContent

    ThemeComponentSurfaceV2(
        skin = skin,
        modifier = modifier,
        applyContentPadding = false,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(textFieldModifier)
                    .onFocusChanged { focusState -> isFocused = focusState.isFocused },
            enabled = enabled,
            readOnly = readOnly,
            isError = isError,
            placeholder = placeholder,
            textStyle = textStyle.copy(color = resolvedTextColor),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = skin.content,
                    unfocusedTextColor = skin.content,
                    disabledTextColor = disabledContent,
                    errorTextColor = skin.content,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = Color.Transparent,
                    cursorColor = skin.content,
                    errorCursorColor = skin.content,
                    focusedPlaceholderColor = placeholderContent,
                    unfocusedPlaceholderColor = placeholderContent,
                    disabledPlaceholderColor = disabledContent,
                    errorPlaceholderColor = placeholderContent,
                ),
        )
    }
}

internal fun resolveThemeOutlinedTextFieldStateV2(
    enabled: Boolean,
    isError: Boolean,
    isFocused: Boolean,
): ThemeComponentStateV2 =
    when {
        !enabled -> ThemeComponentStateV2.DISABLED
        isError -> ThemeComponentStateV2.ERROR
        isFocused -> ThemeComponentStateV2.FOCUSED
        else -> ThemeComponentStateV2.NORMAL
    }
