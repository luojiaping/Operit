package com.ai.assistance.operit.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.ai.assistance.operit.data.theme.packages.ThemePresentationTargetV2
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Materializes already-authorized schema-4 font URIs inside the app cache for Compose. */
internal suspend fun resolveThemePackageFontFamiliesV2(
    context: Context,
    runtime: ThemePackageUiRuntimeV2,
): Map<ThemePresentationTargetV2, FontFamily> {
    val fonts = linkedMapOf<ThemePresentationTargetV2, FontFamily>()
    resolveThemePackageFontFamilyV2(
        context = context,
        runtime = runtime,
        enabledTarget = ThemePresentationTargetV2.TYPOGRAPHY_USE_CUSTOM_FONT,
        fontTarget = ThemePresentationTargetV2.TYPOGRAPHY_FONT_URI,
    )?.let { fontFamily -> fonts[ThemePresentationTargetV2.TYPOGRAPHY_FONT_URI] = fontFamily }
    resolveThemePackageFontFamilyV2(
        context = context,
        runtime = runtime,
        enabledTarget = ThemePresentationTargetV2.BUBBLE_USER_USE_CUSTOM_FONT,
        fontTarget = ThemePresentationTargetV2.BUBBLE_USER_FONT_URI,
    )?.let { fontFamily -> fonts[ThemePresentationTargetV2.BUBBLE_USER_FONT_URI] = fontFamily }
    resolveThemePackageFontFamilyV2(
        context = context,
        runtime = runtime,
        enabledTarget = ThemePresentationTargetV2.BUBBLE_ASSISTANT_USE_CUSTOM_FONT,
        fontTarget = ThemePresentationTargetV2.BUBBLE_ASSISTANT_FONT_URI,
    )?.let { fontFamily -> fonts[ThemePresentationTargetV2.BUBBLE_ASSISTANT_FONT_URI] = fontFamily }
    return fonts
}

private suspend fun resolveThemePackageFontFamilyV2(
    context: Context,
    runtime: ThemePackageUiRuntimeV2,
    enabledTarget: ThemePresentationTargetV2,
    fontTarget: ThemePresentationTargetV2,
): FontFamily? {
    val enabled = requireNotNull(runtime.booleanPresentation(enabledTarget))
    val uri = runtime.fontUriPresentation(fontTarget)
    if (!enabled || uri == null) return null
    return withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(context.cacheDir, "theme-package-fonts-v4/${uri.sha256Hex()}.font")
            if (!cacheFile.isFile) {
                cacheFile.parentFile?.mkdirs()
                val temporary = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_FONT_BYTES) { "Theme font resource exceeds the 16 MB limit." }
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("Theme font resource cannot be opened.")
                require(temporary.isThemeFontFile()) { "Theme font resource is not a supported font file." }
                check(temporary.renameTo(cacheFile)) { "Theme font cache cannot be finalized." }
            }
            FontFamily(Font(cacheFile))
        } catch (error: Throwable) {
            AppLogger.e(TAG, "Unable to materialize theme font resource: $uri", error)
            throw error
        }
    }
}

private fun File.isThemeFontFile(): Boolean {
    inputStream().use { input ->
        val header = ByteArray(4)
        if (input.read(header) != header.size) return false
        return (header[0] == 0x00.toByte() && header[1] == 0x01.toByte() && header[2] == 0x00.toByte() && header[3] == 0x00.toByte()) ||
            (header[0] == 'O'.code.toByte() && header[1] == 'T'.code.toByte() && header[2] == 'T'.code.toByte() && header[3] == 'O'.code.toByte())
    }
}

private fun String.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> String.format(Locale.US, "%02x", byte.toInt() and 0xff) }

private const val TAG = "ThemePackageFont"
private const val MAX_FONT_BYTES = 16L * 1024 * 1024
