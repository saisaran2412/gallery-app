package com.example.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "folio_settings")

enum class SortOrder(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME_ASC("Name (A-Z)"),
    SIZE_DESC("Largest first"),
    DURATION_DESC("Longest duration")
}

enum class MediaFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos")
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.LIQUID_GLASS,
    val reducedMotion: Boolean = false,
    val shareAnimStyle: ShareAnimStyle = ShareAnimStyle.STREAM,
    val gridColumns: Int = 3,
    val showLocationMetadata: Boolean = true,
    val trashAutoCleanDays: Int = 30,
    val hasSeenIntro: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val mediaFilter: MediaFilter = MediaFilter.ALL,
    val hasVaultPin: Boolean = false,
    val vaultPinHash: String = "",
    val vaultPinSalt: String = "",
    val vaultBiometricEnabled: Boolean = false,
    val vaultDbKeyBlob: String = "",
    val vaultBiometricKeyBlob: String = "",
    val offlineFaceGroupingEnabled: Boolean = true,
    val importedAiModelName: String = ""
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val SHARE_ANIM_STYLE = stringPreferencesKey("share_anim_style")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val SHOW_LOCATION = booleanPreferencesKey("show_location_metadata")
        val TRASH_AUTO_CLEAN_DAYS = intPreferencesKey("trash_auto_clean_days")
        val HAS_SEEN_INTRO = booleanPreferencesKey("has_seen_intro")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val MEDIA_FILTER = stringPreferencesKey("media_filter")
        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        val VAULT_PIN_SALT = stringPreferencesKey("vault_pin_salt")
        val VAULT_BIOMETRIC = booleanPreferencesKey("vault_biometric_enabled")
        val VAULT_DB_KEY_BLOB = stringPreferencesKey("vault_db_key_blob")
        val VAULT_BIOMETRIC_KEY_BLOB = stringPreferencesKey("vault_biometric_key_blob")
        val VAULT_PIN_FAILS = intPreferencesKey("vault_pin_fails")
        val VAULT_PIN_LOCK_UNTIL = longPreferencesKey("vault_pin_lock_until")
        val FACE_GROUPING = booleanPreferencesKey("offline_face_grouping")
        val IMPORTED_AI_MODEL = stringPreferencesKey("imported_ai_model_name")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.LIQUID_GLASS.name
        val sortName = prefs[Keys.SORT_ORDER] ?: SortOrder.NEWEST.name
        val filterName = prefs[Keys.MEDIA_FILTER] ?: MediaFilter.ALL.name
        val pinHash = prefs[Keys.VAULT_PIN_HASH].orEmpty()

        UserSettings(
            themeMode = ThemeMode.entries.find { it.name == themeName } ?: ThemeMode.LIQUID_GLASS,
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
            shareAnimStyle = ShareAnimStyle.entries.find { it.name == prefs[Keys.SHARE_ANIM_STYLE] } ?: ShareAnimStyle.STREAM,
            gridColumns = (prefs[Keys.GRID_COLUMNS] ?: 3).coerceIn(3, 4),
            showLocationMetadata = prefs[Keys.SHOW_LOCATION] ?: true,
            trashAutoCleanDays = prefs[Keys.TRASH_AUTO_CLEAN_DAYS] ?: 30,
            hasSeenIntro = prefs[Keys.HAS_SEEN_INTRO] ?: false,
            sortOrder = SortOrder.entries.find { it.name == sortName } ?: SortOrder.NEWEST,
            mediaFilter = MediaFilter.entries.find { it.name == filterName } ?: MediaFilter.ALL,
            hasVaultPin = pinHash.isNotEmpty(),
            vaultPinHash = pinHash,
            vaultPinSalt = prefs[Keys.VAULT_PIN_SALT].orEmpty(),
            vaultBiometricEnabled = prefs[Keys.VAULT_BIOMETRIC] ?: false,
            vaultDbKeyBlob = prefs[Keys.VAULT_DB_KEY_BLOB].orEmpty(),
            vaultBiometricKeyBlob = prefs[Keys.VAULT_BIOMETRIC_KEY_BLOB].orEmpty(),
            offlineFaceGroupingEnabled = prefs[Keys.FACE_GROUPING] ?: true,
            importedAiModelName = prefs[Keys.IMPORTED_AI_MODEL].orEmpty()
        )
    }

    /** Persisted PIN attempt guard so force-stopping the app cannot reset the lockout. */
    suspend fun readPinGuard(): Pair<Int, Long> {
        val p = context.dataStore.data.first()
        return (p[Keys.VAULT_PIN_FAILS] ?: 0) to (p[Keys.VAULT_PIN_LOCK_UNTIL] ?: 0L)
    }

    suspend fun writePinGuard(fails: Int, lockUntilMillis: Long) {
        context.dataStore.edit {
            it[Keys.VAULT_PIN_FAILS] = fails
            it[Keys.VAULT_PIN_LOCK_UNTIL] = lockUntilMillis
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setShareAnimStyle(style: ShareAnimStyle) {
        context.dataStore.edit { it[Keys.SHARE_ANIM_STYLE] = style.name }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
    }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { it[Keys.GRID_COLUMNS] = columns.coerceIn(3, 4) }
    }

    suspend fun setShowLocationMetadata(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_LOCATION] = show }
    }

    suspend fun setTrashAutoCleanDays(days: Int) {
        context.dataStore.edit { it[Keys.TRASH_AUTO_CLEAN_DAYS] = days }
    }

    suspend fun markIntroSeen() {
        context.dataStore.edit { it[Keys.HAS_SEEN_INTRO] = true }
    }

    suspend fun setSortOrder(order: SortOrder) {
        context.dataStore.edit { it[Keys.SORT_ORDER] = order.name }
    }

    suspend fun setMediaFilter(filter: MediaFilter) {
        context.dataStore.edit { it[Keys.MEDIA_FILTER] = filter.name }
    }

    suspend fun setVaultPin(hash: String, salt: String) {
        context.dataStore.edit {
            it[Keys.VAULT_PIN_HASH] = hash
            it[Keys.VAULT_PIN_SALT] = salt
        }
    }

    suspend fun setVaultBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.VAULT_BIOMETRIC] = enabled
            if (!enabled) {
                it.remove(Keys.VAULT_BIOMETRIC_KEY_BLOB)
            }
        }
    }

    suspend fun setVaultDbKeyBlob(blob: String) {
        context.dataStore.edit { it[Keys.VAULT_DB_KEY_BLOB] = blob }
    }

    suspend fun setVaultBiometricKeyBlob(blob: String) {
        context.dataStore.edit {
            it[Keys.VAULT_BIOMETRIC_KEY_BLOB] = blob
            it[Keys.VAULT_BIOMETRIC] = true
        }
    }

    suspend fun clearVaultBiometricBinding() {
        context.dataStore.edit {
            it.remove(Keys.VAULT_BIOMETRIC_KEY_BLOB)
            it[Keys.VAULT_BIOMETRIC] = false
        }
    }

    suspend fun setOfflineFaceGroupingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FACE_GROUPING] = enabled }
    }

    suspend fun setImportedAiModelName(name: String) {
        context.dataStore.edit { it[Keys.IMPORTED_AI_MODEL] = name }
    }
}
