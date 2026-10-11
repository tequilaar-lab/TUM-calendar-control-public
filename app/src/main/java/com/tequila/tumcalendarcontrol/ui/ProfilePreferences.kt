/*
 * Aufgabe: Lokalen Namen und Avatarfarbe in app-privaten SharedPreferences speichern.
 * Ein-/Ausgabe: UserProfile -> wiederhergestelltes Profil oder neutraler Standard.
 * Nebenwirkungen: Nur app-private Profilpräferenzen.
 * Nicht zuständig: Zugangsdatenverschlüsselung, Bilddateien, geräteübergreifende Synchronisierung oder echte Identitätsprüfung.
 */
package com.tequila.tumcalendarcontrol.ui

import android.content.Context

class ProfilePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): UserProfile {
        val savedName = preferences.getString(KEY_NAME, null)?.trim().orEmpty()
        val savedTone = preferences.getString(KEY_TONE, null)
        return UserProfile(
            displayName = savedName.ifBlank { UserProfile().displayName },
            avatarTone = AvatarTone.entries.firstOrNull { it.name == savedTone } ?: AvatarTone.TUM_BLUE,
        )
    }

    fun save(profile: UserProfile) {
        preferences.edit()
            .putString(KEY_NAME, profile.displayName.trim().ifBlank { UserProfile().displayName })
            .putString(KEY_TONE, profile.avatarTone.name)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "local_profile"
        const val KEY_NAME = "display_name"
        const val KEY_TONE = "avatar_tone"
    }
}
