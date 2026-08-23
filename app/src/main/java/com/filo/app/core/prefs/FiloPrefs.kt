package com.filo.app.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The one Preferences DataStore in the app. It holds the auth session, who we are paired
 * with, and the JSON snapshot the widgets render from, so neither the app nor a widget
 * needs a network round trip to know who it belongs to.
 */
val Context.filoDataStore: DataStore<Preferences> by preferencesDataStore(name = "filo")

object PrefKeys {
    val Session = stringPreferencesKey("auth_session")
    val CoupleId = stringPreferencesKey("couple_id")
    val MemberId = stringPreferencesKey("member_id")
    val InviteCode = stringPreferencesKey("invite_code")
    val DisplayName = stringPreferencesKey("display_name")
    val Locale = stringPreferencesKey("locale")
    val WidgetSnapshot = stringPreferencesKey("widget_snapshot")
    val OnboardingDone = booleanPreferencesKey("onboarding_done")
    val AskedLocation = booleanPreferencesKey("asked_location")
    val AskedNotifications = booleanPreferencesKey("asked_notifications")
    val LastPingAt = longPreferencesKey("last_ping_at")
    val ClockFormat = stringPreferencesKey("clock_format")
    val RecentEmojis = stringPreferencesKey("recent_emojis")
}

/**
 * The emojis this person actually uses, newest first, so the composer can offer them back
 * as one-tap choices instead of a canned preset row.
 */
object RecentEmojis {
    private const val MAX = 8

    suspend fun load(context: Context): List<String> =
        (context.filoDataStore.data.first()[PrefKeys.RecentEmojis] ?: "")
            .split('\n').filter { it.isNotBlank() }

    /** Fire-and-forget: a lost write here costs nothing. */
    fun record(context: Context, emoji: String) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                app.filoDataStore.edit { prefs ->
                    val current = (prefs[PrefKeys.RecentEmojis] ?: "")
                        .split('\n').filter { it.isNotBlank() }
                    prefs[PrefKeys.RecentEmojis] =
                        (listOf(emoji) + current.filterNot { it == emoji })
                            .take(MAX).joinToString("\n")
                }
            }
        }
    }
}

data class PairingState(
    val coupleId: String? = null,
    val memberId: String? = null,
    val inviteCode: String? = null,
    val displayName: String? = null,
    val locale: String = "en",
) {
    val isPaired: Boolean get() = coupleId != null
}

class FiloPrefs(private val context: Context) {

    val pairing: Flow<PairingState> = context.filoDataStore.data.map { prefs ->
        PairingState(
            coupleId = prefs[PrefKeys.CoupleId],
            memberId = prefs[PrefKeys.MemberId],
            inviteCode = prefs[PrefKeys.InviteCode],
            displayName = prefs[PrefKeys.DisplayName],
            locale = prefs[PrefKeys.Locale] ?: "en",
        )
    }

    suspend fun currentPairing(): PairingState = pairing.first()

    suspend fun savePairing(
        coupleId: String,
        memberId: String,
        inviteCode: String,
        displayName: String,
        locale: String,
    ) {
        context.filoDataStore.edit { prefs ->
            prefs[PrefKeys.CoupleId] = coupleId
            prefs[PrefKeys.MemberId] = memberId
            prefs[PrefKeys.InviteCode] = inviteCode
            prefs[PrefKeys.DisplayName] = displayName
            prefs[PrefKeys.Locale] = locale
        }
    }

    suspend fun setLocale(locale: String) {
        context.filoDataStore.edit { it[PrefKeys.Locale] = locale }
    }

    suspend fun setDisplayName(name: String) {
        context.filoDataStore.edit { it[PrefKeys.DisplayName] = name }
    }

    val localeFlow: Flow<String> = context.filoDataStore.data.map { it[PrefKeys.Locale] ?: "en" }

    /**
     * "12" or "24". Absent means follow the phone, which is the sensible first run default
     * and the reason this is not a plain boolean.
     */
    val clock24h: Flow<Boolean> = context.filoDataStore.data.map { prefs ->
        when (prefs[PrefKeys.ClockFormat]) {
            "24" -> true
            "12" -> false
            else -> android.text.format.DateFormat.is24HourFormat(context)
        }
    }

    suspend fun currentClock24h(): Boolean = clock24h.first()

    suspend fun setClock24h(use24: Boolean) {
        context.filoDataStore.edit { it[PrefKeys.ClockFormat] = if (use24) "24" else "12" }
    }

    val askedLocation: Flow<Boolean> = context.filoDataStore.data.map { it[PrefKeys.AskedLocation] ?: false }
    val askedNotifications: Flow<Boolean> = context.filoDataStore.data.map { it[PrefKeys.AskedNotifications] ?: false }

    val onboardingDone: Flow<Boolean> =
        context.filoDataStore.data.map { it[PrefKeys.OnboardingDone] ?: false }

    suspend fun markOnboardingDone() {
        context.filoDataStore.edit { it[PrefKeys.OnboardingDone] = true }
    }

    suspend fun markAskedLocation() {
        context.filoDataStore.edit { it[PrefKeys.AskedLocation] = true }
    }

    suspend fun markAskedNotifications() {
        context.filoDataStore.edit { it[PrefKeys.AskedNotifications] = true }
    }

    /** Wipes the pairing but leaves the auth session alone. */
    suspend fun clearPairing() {
        context.filoDataStore.edit { prefs ->
            prefs.remove(PrefKeys.CoupleId)
            prefs.remove(PrefKeys.InviteCode)
            prefs.remove(PrefKeys.WidgetSnapshot)
        }
    }
}
