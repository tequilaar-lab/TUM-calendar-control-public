/*
 * Aufgabe: Regelbestand in app-privaten SharedPreferences speichern.
 * Ein-/Ausgabe: Regelbestand -> tatsächlich gespeicherter/wiederhergestellter privater Bestand.
 * Nebenwirkungen: Nur app-private SharedPreferences, synchronisierte Checkpoints.
 * Nicht zuständig: Zugangsdatenverschlüsselung, Kalenderberechtigungen, geräteübergreifende Synchronisierung oder Kontozugriffe.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import android.content.Context

class RuleLedgerPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    fun load(): RuleLedger = synchronized(LOCK) { loadUnlocked() }

    /** Vollständiges Ergebnis nach externer Synchronisierung zurückschreiben; fehlgeschlagenes commit muss gemeldet werden. */
    fun save(ledger: RuleLedger): Boolean = synchronized(LOCK) { saveUnlocked(ledger) }

    /** Alte Speicherzuordnungen dürfen Hintergrund-Checkpoints nicht überschreiben. Anzeige und Regeln atomar speichern; bei zweitem Präferenzfehler ursprüngliche Regeln wiederherstellen. */
    fun saveBusinessState(ledger: RuleLedger, savePresentations: () -> Boolean): RuleLedger? = synchronized(LOCK) {
        val before = loadUnlocked()
        val merged = ledger.copy(mappings = before.mappings, lastSummary = before.lastSummary)
        if (!saveUnlocked(merged)) return@synchronized null
        if (savePresentations()) return@synchronized merged
        saveUnlocked(before)
        null
    }
    /** Hintergrund-Checkpoints sofort speichern; bereits ausgeführte externe Schreibidentitäten bleiben bei Prozessabbruch erhalten. */
    fun checkpointMappings(mappings: List<StoredEventMapping>) {
        synchronized(LOCK) {
            check(saveUnlocked(loadUnlocked().copy(mappings = mappings))) { message(121) }
        }
    }
    private fun loadUnlocked(): RuleLedger = RuleLedgerCodec.decode(preferences.getString(KEY_DATA, null))
    private fun saveUnlocked(ledger: RuleLedger): Boolean = preferences.edit().putString(KEY_DATA, RuleLedgerCodec.encode(ledger)).commit()

    private companion object {
        const val FILE_NAME = "rule_ledger"
        const val KEY_DATA = "serialized_ledger"
        val LOCK = Any()
    }
}
