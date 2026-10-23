/*
 * Aufgabe: Haptik prüfen: Zuordnung je Stärke, „aus“ ohne Vibration, Rückfall vor Android 11 und Auslösung durch Bausteine.
 * Ein-/Ausgabe: Einstellungen und synthetische Bedienung -> aufgezeichnete Systemkonstanten.
 * Nebenwirkungen: Keine; aufzeichnender Ersatz statt echter Vibration.
 * Nicht zuständig: tatsächliches Vibrationsgefühl (nur auf dem Gerät prüfbar).
 */
package com.tequila.tumcalendarcontrol.ui.design

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DesignHapticsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `三档强度对应逐级增强的系统效果`() {
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstant(HapticEvent.SELECTION, HapticLevel.LIGHT, 36))
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, hapticConstant(HapticEvent.SELECTION, HapticLevel.STANDARD, 36))
        assertEquals(HapticFeedbackConstants.CONTEXT_CLICK, hapticConstant(HapticEvent.SELECTION, HapticLevel.STRONG, 36))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstant(HapticEvent.ACTION, HapticLevel.STRONG, 36))
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstant(HapticEvent.CONFIRM, HapticLevel.STANDARD, 36))
        assertEquals(HapticFeedbackConstants.REJECT, hapticConstant(HapticEvent.REJECT, HapticLevel.STRONG, 36))
    }

    @Test fun `安卓11以下与轻档的确认退回为操作效果`() {
        assertEquals(HapticFeedbackConstants.CONTEXT_CLICK, hapticConstant(HapticEvent.CONFIRM, HapticLevel.STANDARD, 29))
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, hapticConstant(HapticEvent.REJECT, HapticLevel.LIGHT, 36))
    }

    @Test fun `关闭时不震动 预览仍可试`() {
        val calls = mutableListOf<Int>()
        val off = DesignHaptics(HapticSettings(enabled = false)) { calls += it }
        off.perform(HapticEvent.ACTION)
        assertTrue(calls.isEmpty())
        off.preview(HapticLevel.STRONG)
        assertEquals(listOf(HapticFeedbackConstants.LONG_PRESS), calls)
    }

    /** Auswahl, Schaltfläche und Bestätigung lösen jeweils ihr Ereignis aus; gefährliche Bestätigung das Ablehnungsmuster. */
    @Test fun `设计组件按交互类型触发`() {
        val calls = mutableListOf<Int>()
        val haptics = DesignHaptics(HapticSettings(level = HapticLevel.STANDARD)) { calls += it }
        composeRule.setContent {
            CompositionLocalProvider(LocalDesignHaptics provides haptics) {
                Column {
                    DesignChoice("Synthetik Auswahl", selected = false) {}
                    DesignPillButton("Synthetik Aktion") {}
                    DesignConfirmDialog(DesignConfirmRequest("k", "t", "b", "", "Abbrechen", "Löschen", danger = true, onCancel = {}, onConfirm = {}))
                }
            }
        }
        composeRule.onNodeWithText("Synthetik Auswahl").performClick()
        composeRule.onNodeWithText("Synthetik Aktion").performClick()
        composeRule.onNodeWithText("Löschen").performClick()
        assertEquals(listOf(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.CONTEXT_CLICK, HapticFeedbackConstants.REJECT), calls)
    }
}
