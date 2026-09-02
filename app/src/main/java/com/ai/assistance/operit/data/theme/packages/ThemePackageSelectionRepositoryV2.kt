package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.preferences.preferenceSchemaMigration
import com.ai.assistance.operit.data.preferences.versionedPreferencesDataStore
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val THEME_INSTANCE_V2_KEY = stringPreferencesKey("theme_instance_v4_json")

private val themeSelectionJsonV2 = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    explicitNulls = false
}

private val THEME_OWNED_RESOURCE_URIS_V4_KEY = stringPreferencesKey("theme_owned_resource_uris_v4_json")
private val THEME_PENDING_RESOURCE_GRANTS_V4_KEY =
    stringPreferencesKey("theme_pending_resource_grants_v4_json")
private val THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY =
    stringPreferencesKey("theme_pending_resource_revocations_v4_json")

private val Context.themePackageSelectionV2DataStore by versionedPreferencesDataStore(
    name = "theme_package_selection_v4",
    currentVersion = 1,
    createMigration = {
        preferenceSchemaMigration { version, preferences ->
            require(version == 0) { "Theme package selection v4 has no prior record version." }
            preferences[THEME_INSTANCE_V2_KEY] =
                themeSelectionJsonV2.encodeToString(ThemeInstanceV2.defaultBundled())
        }
    },
)

/** A schema-4-only global theme selection with no reader for unpublished prior records. */
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
            val ownedUris = preferences.decodeThemeResourceUrisV4(THEME_OWNED_RESOURCE_URIS_V4_KEY)
            val retainedUris = instance.themeResourceUris()
            val releasedUris = ownedUris - retainedUris
            preferences[THEME_INSTANCE_V2_KEY] = themeSelectionJsonV2.encodeToString(instance)
            preferences.writeThemeResourceUrisV4(THEME_OWNED_RESOURCE_URIS_V4_KEY, ownedUris - releasedUris)
            preferences.writeThemeResourceUrisV4(
                THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY,
                (preferences.decodeThemeResourceUrisV4(THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY) - retainedUris) +
                    releasedUris,
            )
        }
    }

    suspend fun currentSelection(): ThemeInstanceV2 = selectionFlow.first()

    suspend fun markPendingResourceGrant(uri: String) {
        dataStore.edit { preferences ->
            val pendingUris = preferences.decodeThemeResourceUrisV4(THEME_PENDING_RESOURCE_GRANTS_V4_KEY)
            preferences.writeThemeResourceUrisV4(THEME_PENDING_RESOURCE_GRANTS_V4_KEY, pendingUris + uri)
        }
    }

    suspend fun hasThemeResourceGrantOwnership(uri: String): Boolean {
        val preferences = dataStore.data.first()
        return uri in preferences.decodeThemeResourceUrisV4(THEME_OWNED_RESOURCE_URIS_V4_KEY) ||
            uri in preferences.decodeThemeResourceUrisV4(THEME_PENDING_RESOURCE_GRANTS_V4_KEY) ||
            uri in preferences.decodeThemeResourceUrisV4(THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY)
    }

    suspend fun replaceResourceParameter(
        expectedCoordinate: ThemePackageCoordinateV2,
        parameterId: String,
        value: ThemeParameterValueV2,
        trackUriOwnership: Boolean,
    ) {
        dataStore.edit { preferences ->
            val current = preferences.decodeThemeInstanceV2()
            check(current.reference.coordinate == expectedCoordinate) {
                "Theme selection changed before resource parameter $parameterId was written."
            }
            val updated =
                current.copy(
                    parameterValues = current.parameterValues + (parameterId to value),
                )
            val ownedUris = preferences.decodeThemeResourceUrisV4(THEME_OWNED_RESOURCE_URIS_V4_KEY)
            val resourceUri = value.requireResourceUri()
            val nextOwnedUris = if (trackUriOwnership) ownedUris + resourceUri else ownedUris
            val retainedUris = updated.themeResourceUris()
            val releasedUris = nextOwnedUris - retainedUris
            preferences[THEME_INSTANCE_V2_KEY] = themeSelectionJsonV2.encodeToString(updated)
            preferences.writeThemeResourceUrisV4(
                THEME_OWNED_RESOURCE_URIS_V4_KEY,
                nextOwnedUris - releasedUris,
            )
            preferences.writeThemeResourceUrisV4(
                THEME_PENDING_RESOURCE_GRANTS_V4_KEY,
                preferences.decodeThemeResourceUrisV4(THEME_PENDING_RESOURCE_GRANTS_V4_KEY) - resourceUri,
            )
            preferences.writeThemeResourceUrisV4(
                THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY,
                (preferences.decodeThemeResourceUrisV4(THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY) - retainedUris) +
                    releasedUris,
            )
        }
    }

    suspend fun clearResourceParameter(
        expectedCoordinate: ThemePackageCoordinateV2,
        parameterId: String,
    ) {
        dataStore.edit { preferences ->
            val current = preferences.decodeThemeInstanceV2()
            check(current.reference.coordinate == expectedCoordinate) {
                "Theme selection changed before resource parameter $parameterId was reset."
            }
            val updated =
                current.copy(
                    parameterValues = current.parameterValues - parameterId,
                )
            val ownedUris = preferences.decodeThemeResourceUrisV4(THEME_OWNED_RESOURCE_URIS_V4_KEY)
            val retainedUris = updated.themeResourceUris()
            val releasedUris = ownedUris - retainedUris
            preferences[THEME_INSTANCE_V2_KEY] = themeSelectionJsonV2.encodeToString(updated)
            preferences.writeThemeResourceUrisV4(
                THEME_OWNED_RESOURCE_URIS_V4_KEY,
                ownedUris - releasedUris,
            )
            preferences.writeThemeResourceUrisV4(
                THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY,
                preferences.decodeThemeResourceUrisV4(THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY) + releasedUris,
            )
        }
    }

    suspend fun reconcileThemeResourceGrants() {
        val candidates = collectThemeResourceGrantCandidates()
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
                preferences.writeThemeResourceUrisV4(
                    THEME_PENDING_RESOURCE_GRANTS_V4_KEY,
                    preferences.decodeThemeResourceUrisV4(THEME_PENDING_RESOURCE_GRANTS_V4_KEY) - resolvedUris,
                )
            preferences.writeThemeResourceUrisV4(
                THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY,
                preferences.decodeThemeResourceUrisV4(THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY) - resolvedUris,
            )
        }
    }

    private suspend fun collectThemeResourceGrantCandidates(): Set<String> {
        var candidates = emptySet<String>()
        dataStore.edit { preferences ->
            val current = preferences.decodeThemeInstanceV2()
            val retainedUris = current.themeResourceUris()
            val ownedUris = preferences.decodeThemeResourceUrisV4(THEME_OWNED_RESOURCE_URIS_V4_KEY)
            val newlyRevokedUris = ownedUris - retainedUris
            if (newlyRevokedUris.isNotEmpty()) {
                preferences.writeThemeResourceUrisV4(
                    THEME_OWNED_RESOURCE_URIS_V4_KEY,
                    ownedUris - newlyRevokedUris,
                )
                preferences.writeThemeResourceUrisV4(
                    THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY,
                    preferences.decodeThemeResourceUrisV4(THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY) +
                        newlyRevokedUris,
                )
            }
            val pendingGrants =
                preferences.decodeThemeResourceUrisV4(THEME_PENDING_RESOURCE_GRANTS_V4_KEY) - retainedUris
            val pendingRevocations =
                preferences.decodeThemeResourceUrisV4(THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY) - retainedUris
            preferences.writeThemeResourceUrisV4(THEME_PENDING_RESOURCE_GRANTS_V4_KEY, pendingGrants)
            preferences.writeThemeResourceUrisV4(
                THEME_PENDING_RESOURCE_REVOCATIONS_V4_KEY,
                pendingRevocations,
            )
            candidates = pendingGrants + pendingRevocations
        }
        return candidates
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

internal fun Preferences.decodeThemeInstanceV2(): ThemeInstanceV2 {
    val raw =
        this[THEME_INSTANCE_V2_KEY]
            ?: throw IllegalStateException("Global schema-4 theme selection record is missing.")
    return themeSelectionJsonV2.decodeFromString(raw)
}

private fun ThemeInstanceV2.themeResourceUris(): Set<String> =
    parameterValues.values
        .mapNotNull(ThemeParameterValueV2::resourceUriOrNull)
        .toSet()

private fun ThemeParameterValueV2.resourceUriOrNull(): String? =
    when (this) {
        is ThemeParameterValueV2.ImageUriValue -> uri
        is ThemeParameterValueV2.VideoUriValue -> uri
        is ThemeParameterValueV2.FontUriValue -> uri
        else -> null
    }

private fun ThemeParameterValueV2.requireResourceUri(): String =
    requireNotNull(resourceUriOrNull()) { "Theme parameter value must be a resource URI." }

private fun Preferences.decodeThemeResourceUrisV4(key: Preferences.Key<String>): Set<String> {
    val raw = this[key] ?: return emptySet()
    return themeSelectionJsonV2.decodeFromString<List<String>>(raw).toSet()
}

private fun androidx.datastore.preferences.core.MutablePreferences.writeThemeResourceUrisV4(
    key: Preferences.Key<String>,
    uris: Set<String>,
) {
    if (uris.isEmpty()) {
        remove(key)
    } else {
        this[key] = themeSelectionJsonV2.encodeToString(uris.sorted())
    }
}
