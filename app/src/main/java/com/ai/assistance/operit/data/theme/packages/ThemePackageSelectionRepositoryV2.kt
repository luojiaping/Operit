package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.preferences.missingPreferencesSchemaMigration
import com.ai.assistance.operit.data.preferences.preferenceSchemaMigration
import com.ai.assistance.operit.data.preferences.versionedPreferencesDataStore
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val THEME_INSTANCE_V2_KEY = stringPreferencesKey("theme_instance_v2_json")

private val themeSelectionJsonV2 = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    explicitNulls = false
}

private val THEME_OWNED_IMAGE_URIS_V3_KEY = stringPreferencesKey("theme_owned_image_uris_v3_json")
private val THEME_PENDING_IMAGE_GRANTS_V3_KEY = stringPreferencesKey("theme_pending_image_grants_v3_json")
private val THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY =
    stringPreferencesKey("theme_pending_image_revocations_v3_json")

private val Context.themePackageSelectionV2DataStore by versionedPreferencesDataStore(
    name = "theme_package_selection_v2",
    currentVersion = 2,
    createMigration = {
        preferenceSchemaMigration { version, preferences ->
            migrateThemePackageSelectionSchemaV2(version, preferences)
        }
    },
)

internal fun migrateThemePackageSelectionSchemaV2(
    version: Int,
    preferences: androidx.datastore.preferences.core.MutablePreferences,
) {
    when (version) {
        0,
        1,
        -> {
            // Schema 3 removes the unpublished string-based parameter representation.
            preferences[THEME_INSTANCE_V2_KEY] =
                themeSelectionJsonV2.encodeToString(ThemeInstanceV2.defaultBundled())
            preferences.remove(THEME_OWNED_IMAGE_URIS_V3_KEY)
            preferences.remove(THEME_PENDING_IMAGE_GRANTS_V3_KEY)
            preferences.remove(THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY)
        }

        else -> missingPreferencesSchemaMigration(version)
    }
}

/** A V2-only global theme selection with no reader for the unpublished V1 record. */
internal class ThemePackageSelectionRepositoryV2 private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.themePackageSelectionV2DataStore

    val selectionFlow: Flow<ThemeInstanceV2> =
        dataStore.data.map { preferences -> preferences.decodeThemeInstanceV2() }

    suspend fun replace(instance: ThemeInstanceV2) {
        dataStore.edit { preferences ->
            preferences[THEME_INSTANCE_V2_KEY] = themeSelectionJsonV2.encodeToString(instance)
        }
    }

    suspend fun replaceParameter(
        expectedCoordinate: ThemePackageCoordinateV2,
        parameterId: String,
        value: ThemeParameterValueV2,
    ) {
        dataStore.edit { preferences ->
            val current = preferences.decodeThemeInstanceV2()
            check(current.reference.coordinate == expectedCoordinate) {
                "Theme selection changed before parameter $parameterId was written."
            }
            preferences[THEME_INSTANCE_V2_KEY] =
                themeSelectionJsonV2.encodeToString(
                    current.copy(parameterValues = current.parameterValues + (parameterId to value)),
                )
        }
    }

    suspend fun clearParameter(
        expectedCoordinate: ThemePackageCoordinateV2,
        parameterId: String,
    ) {
        dataStore.edit { preferences ->
            val current = preferences.decodeThemeInstanceV2()
            check(current.reference.coordinate == expectedCoordinate) {
                "Theme selection changed before parameter $parameterId was reset."
            }
            preferences[THEME_INSTANCE_V2_KEY] =
                themeSelectionJsonV2.encodeToString(
                    current.copy(parameterValues = current.parameterValues - parameterId),
                )
        }
    }

    suspend fun replaceSelection(instance: ThemeInstanceV2) {
        dataStore.edit { preferences ->
            val ownedUris = preferences.decodeThemeImageUrisV3(THEME_OWNED_IMAGE_URIS_V3_KEY)
            val retainedUris = instance.themeImageUris()
            val releasedUris = ownedUris - retainedUris
            preferences[THEME_INSTANCE_V2_KEY] = themeSelectionJsonV2.encodeToString(instance)
            preferences.writeThemeImageUrisV3(THEME_OWNED_IMAGE_URIS_V3_KEY, ownedUris - releasedUris)
            preferences.writeThemeImageUrisV3(
                THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY,
                (preferences.decodeThemeImageUrisV3(THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY) - retainedUris) +
                    releasedUris,
            )
        }
    }

    suspend fun currentSelection(): ThemeInstanceV2 = selectionFlow.first()

    suspend fun markPendingImageGrant(uri: String) {
        dataStore.edit { preferences ->
            val pendingUris = preferences.decodeThemeImageUrisV3(THEME_PENDING_IMAGE_GRANTS_V3_KEY)
            preferences.writeThemeImageUrisV3(THEME_PENDING_IMAGE_GRANTS_V3_KEY, pendingUris + uri)
        }
    }

    suspend fun hasThemeImageGrantOwnership(uri: String): Boolean {
        val preferences = dataStore.data.first()
        return uri in preferences.decodeThemeImageUrisV3(THEME_OWNED_IMAGE_URIS_V3_KEY) ||
            uri in preferences.decodeThemeImageUrisV3(THEME_PENDING_IMAGE_GRANTS_V3_KEY) ||
            uri in preferences.decodeThemeImageUrisV3(THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY)
    }

    suspend fun replaceImageParameter(
        expectedCoordinate: ThemePackageCoordinateV2,
        parameterId: String,
        value: ThemeParameterValueV2.ImageUriValue,
        trackUriOwnership: Boolean,
    ) {
        dataStore.edit { preferences ->
            val current = preferences.decodeThemeInstanceV2()
            check(current.reference.coordinate == expectedCoordinate) {
                "Theme selection changed before image parameter $parameterId was written."
            }
            val updated =
                current.copy(
                    parameterValues = current.parameterValues + (parameterId to value),
                )
            val ownedUris = preferences.decodeThemeImageUrisV3(THEME_OWNED_IMAGE_URIS_V3_KEY)
            val nextOwnedUris = if (trackUriOwnership) ownedUris + value.uri else ownedUris
            val retainedUris = updated.themeImageUris()
            val releasedUris = nextOwnedUris - retainedUris
            preferences[THEME_INSTANCE_V2_KEY] = themeSelectionJsonV2.encodeToString(updated)
            preferences.writeThemeImageUrisV3(
                THEME_OWNED_IMAGE_URIS_V3_KEY,
                nextOwnedUris - releasedUris,
            )
            preferences.writeThemeImageUrisV3(
                THEME_PENDING_IMAGE_GRANTS_V3_KEY,
                preferences.decodeThemeImageUrisV3(THEME_PENDING_IMAGE_GRANTS_V3_KEY) - value.uri,
            )
            preferences.writeThemeImageUrisV3(
                THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY,
                (preferences.decodeThemeImageUrisV3(THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY) - retainedUris) +
                    releasedUris,
            )
        }
    }

    suspend fun clearImageParameter(
        expectedCoordinate: ThemePackageCoordinateV2,
        parameterId: String,
    ) {
        dataStore.edit { preferences ->
            val current = preferences.decodeThemeInstanceV2()
            check(current.reference.coordinate == expectedCoordinate) {
                "Theme selection changed before image parameter $parameterId was reset."
            }
            val updated =
                current.copy(
                    parameterValues = current.parameterValues - parameterId,
                )
            val ownedUris = preferences.decodeThemeImageUrisV3(THEME_OWNED_IMAGE_URIS_V3_KEY)
            val retainedUris = updated.themeImageUris()
            val releasedUris = ownedUris - retainedUris
            preferences[THEME_INSTANCE_V2_KEY] = themeSelectionJsonV2.encodeToString(updated)
            preferences.writeThemeImageUrisV3(
                THEME_OWNED_IMAGE_URIS_V3_KEY,
                ownedUris - releasedUris,
            )
            preferences.writeThemeImageUrisV3(
                THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY,
                preferences.decodeThemeImageUrisV3(THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY) + releasedUris,
            )
        }
    }

    suspend fun reconcileThemeImageGrants() {
        val candidates = collectThemeImageGrantCandidates()
        val resolvedUris = candidates.filterTo(mutableSetOf()) { rawUri ->
            val uri = Uri.parse(rawUri)
            val persisted =
                appContext.contentResolver.persistedUriPermissions.any { permission ->
                    permission.uri == uri && permission.isReadPermission
                }
            if (!persisted) {
                true
            } else {
                try {
                    appContext.contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    true
                } catch (error: SecurityException) {
                    AppLogger.e(TAG, "Unable to release theme image URI permission: $rawUri", error)
                    false
                }
            }
        }
        if (resolvedUris.isEmpty()) return
        dataStore.edit { preferences ->
            preferences.writeThemeImageUrisV3(
                THEME_PENDING_IMAGE_GRANTS_V3_KEY,
                preferences.decodeThemeImageUrisV3(THEME_PENDING_IMAGE_GRANTS_V3_KEY) - resolvedUris,
            )
            preferences.writeThemeImageUrisV3(
                THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY,
                preferences.decodeThemeImageUrisV3(THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY) - resolvedUris,
            )
        }
    }

    private suspend fun collectThemeImageGrantCandidates(): Set<String> {
        var candidates = emptySet<String>()
        dataStore.edit { preferences ->
            val current = preferences.decodeThemeInstanceV2()
            val retainedUris = current.themeImageUris()
            val ownedUris = preferences.decodeThemeImageUrisV3(THEME_OWNED_IMAGE_URIS_V3_KEY)
            val newlyRevokedUris = ownedUris - retainedUris
            if (newlyRevokedUris.isNotEmpty()) {
                preferences.writeThemeImageUrisV3(
                    THEME_OWNED_IMAGE_URIS_V3_KEY,
                    ownedUris - newlyRevokedUris,
                )
                preferences.writeThemeImageUrisV3(
                    THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY,
                    preferences.decodeThemeImageUrisV3(THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY) +
                        newlyRevokedUris,
                )
            }
            val pendingGrants =
                preferences.decodeThemeImageUrisV3(THEME_PENDING_IMAGE_GRANTS_V3_KEY) - retainedUris
            val pendingRevocations =
                preferences.decodeThemeImageUrisV3(THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY) - retainedUris
            preferences.writeThemeImageUrisV3(THEME_PENDING_IMAGE_GRANTS_V3_KEY, pendingGrants)
            preferences.writeThemeImageUrisV3(
                THEME_PENDING_IMAGE_REVOCATIONS_V3_KEY,
                pendingRevocations,
            )
            candidates = pendingGrants + pendingRevocations
        }
        return candidates
    }

    /**
     * Repairs a persisted selection whose exact immutable runtime is unavailable. The whole
     * record is replaced so parameters from that package cannot leak into the bundled default
     * package.
     */
    suspend fun repairUnavailableSelection(
        installedCoordinates: Set<ThemePackageCoordinateV2>,
    ): ThemePackageCoordinateV2? {
        var repairedCoordinate: ThemePackageCoordinateV2? = null
        dataStore.edit { preferences ->
            val current = preferences.decodeThemeInstanceV2()
            if (current.reference.coordinate !in installedCoordinates) {
                repairedCoordinate = current.reference.coordinate
                preferences[THEME_INSTANCE_V2_KEY] =
                    themeSelectionJsonV2.encodeToString(
                        current.repairUnavailableSelection(installedCoordinates),
                    )
            }
        }
        return repairedCoordinate
    }

    companion object {
        private const val TAG = "ThemePackageSelection"

        @Volatile
        private var instance: ThemePackageSelectionRepositoryV2? = null

        fun getInstance(context: Context): ThemePackageSelectionRepositoryV2 =
            instance ?: synchronized(this) {
                instance ?: ThemePackageSelectionRepositoryV2(context).also { created -> instance = created }
            }
    }
}

internal fun ThemeInstanceV2.repairUnavailableSelection(
    installedCoordinates: Set<ThemePackageCoordinateV2>,
): ThemeInstanceV2 =
    if (reference.coordinate in installedCoordinates) {
        this
    } else {
        ThemeInstanceV2.defaultBundled()
    }

internal fun Preferences.decodeThemeInstanceV2(): ThemeInstanceV2 {
    val raw =
        this[THEME_INSTANCE_V2_KEY]
            ?: throw IllegalStateException("Global V2 theme selection record is missing.")
    return themeSelectionJsonV2.decodeFromString(raw)
}

private fun ThemeInstanceV2.themeImageUris(): Set<String> =
    parameterValues.values
        .mapNotNull { value -> (value as? ThemeParameterValueV2.ImageUriValue)?.uri }
        .toSet()

private fun Preferences.decodeThemeImageUrisV3(key: Preferences.Key<String>): Set<String> {
    val raw = this[key] ?: return emptySet()
    return themeSelectionJsonV2.decodeFromString<List<String>>(raw).toSet()
}

private fun androidx.datastore.preferences.core.MutablePreferences.writeThemeImageUrisV3(
    key: Preferences.Key<String>,
    uris: Set<String>,
) {
    if (uris.isEmpty()) {
        remove(key)
    } else {
        this[key] = themeSelectionJsonV2.encodeToString(uris.sorted())
    }
}
