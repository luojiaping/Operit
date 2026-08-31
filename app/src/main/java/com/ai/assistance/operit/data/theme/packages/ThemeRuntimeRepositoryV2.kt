package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicReference

internal data class ThemeRuntimeLinkFailureV2(
    val coordinate: ThemePackageCoordinateV2,
    val error: Exception,
)

internal data class ThemeRuntimeLinkIndexV2(
    val linked: Map<ThemePackageCoordinateV2, LinkedThemeRuntimeV2>,
    val failures: List<ThemeRuntimeLinkFailureV2>,
) {
    val linkedCoordinates: Set<ThemePackageCoordinateV2>
        get() = linked.keys
}

/**
 * Process-local immutable runtime index. Installation and application startup refresh this index
 * off the UI composition path; Compose only looks up already linked package data here.
 */
internal object ThemeRuntimeRepositoryV2 {
    private val snapshot = AtomicReference<Map<ThemePackageCoordinateV2, LinkedThemeRuntimeV2>>(emptyMap())

    fun refresh(context: Context): ThemeRuntimeLinkIndexV2 {
        val catalog = ThemePackageInstallerV2.getInstance(context).catalog()
        val index = linkThemeCatalogV2(catalog)
        index.failures.forEach { failure ->
            AppLogger.e(
                TAG,
                "V2 theme package is unavailable for the current runtime: " +
                    "${failure.coordinate.packageId.value}",
                failure.error,
            )
        }
        snapshot.set(index.linked)
        return index
    }

    fun require(coordinate: ThemePackageCoordinateV2): LinkedThemeRuntimeV2 =
        snapshot.get()[coordinate]
            ?: error("Active V2 theme package has not been linked: ${coordinate.packageId.value}")

    fun isLinked(coordinate: ThemePackageCoordinateV2): Boolean = coordinate in snapshot.get()

    private const val TAG = "ThemeRuntimeRepositoryV2"
}

internal fun linkThemeCatalogV2(catalog: PublishedThemeCatalogV2): ThemeRuntimeLinkIndexV2 {
    val linked = linkedMapOf<ThemePackageCoordinateV2, LinkedThemeRuntimeV2>()
    val failures = mutableListOf<ThemeRuntimeLinkFailureV2>()
    catalog.installations.forEach { installation ->
        try {
            linked[installation.coordinate] = ThemePackageRuntimeLinkerV2.link(installation, catalog)
        } catch (error: Exception) {
            failures += ThemeRuntimeLinkFailureV2(installation.coordinate, error)
        }
    }
    return ThemeRuntimeLinkIndexV2(linked = linked, failures = failures)
}
