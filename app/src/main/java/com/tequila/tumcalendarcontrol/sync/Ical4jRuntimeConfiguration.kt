/*
 * Aufgabe: iCal4j vor erster Analyse mit prozessinternem Map-Zeitzonencache konfigurieren.
 * Ein-/Ausgabe: Prozesskonfiguration -> iCal4j-Map-Zeitzonencache ohne JCache-Anbieter.
 * Nebenwirkungen: Nur iCal4j-Systemeigenschaft im aktuellen Prozess setzen.
 * Nicht zuständig: Zeitzonendownloads, Geräteeinstellungen oder Überschreiben bereits gesetzter Cache-Implementierungen.
 */
package com.tequila.tumcalendarcontrol.sync

internal object Ical4jRuntimeConfiguration {
    private const val CACHE_PROPERTY = "net.fortuna.ical4j.timezone.cache.impl"
    private const val MAP_CACHE = "net.fortuna.ical4j.util.MapTimeZoneCache"

    /** iCal4j liest die Eigenschaft beim CalendarBuilder-Aufbau; vorher konfigurieren. */
    fun configureOfflineCache() {
        if (System.getProperty(CACHE_PROPERTY).isNullOrBlank()) {
            System.setProperty(CACHE_PROPERTY, MAP_CACHE)
        }
    }
}
