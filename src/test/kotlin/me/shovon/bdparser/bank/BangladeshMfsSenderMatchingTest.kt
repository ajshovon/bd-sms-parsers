/*
 * Derived from Cashiro (https://github.com/ritesh-kanwar/Cashiro),
 * Copyright (C) Ritesh Kanwar, licensed under the GNU AGPL v3.
 *
 * Modified 2026-09-01: extracted into the standalone bd-sms-parsers library and
 * repackaged as me.shovon.bdparser. See NOTICE for the full list of changes.
 *
 * This file is part of bd-sms-parsers, licensed under the GNU AGPL v3.
 * See LICENSE for the full text.
 */
package me.shovon.bdparser.bank

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Focused unit tests for [BangladeshMfsParser.matchesSenderTokens], the shared sender-matching
 * helper used by all five BD MFS parsers (bKash, Nagad, Rocket, Upay, Tap).
 *
 * Covers: DLT prefixes/suffixes, lowercase input, punctuation/spacing noise, short-token false
 * positives (the main safety hazard for generic tokens like "TAP"/"UPAY"/"DBBL"), and plain
 * non-matches.
 */
class BangladeshMfsSenderMatchingTest {

    private data class Case(val label: String, val sender: String, val tokens: List<String>, val expected: Boolean)

    @TestFactory
    fun `matchesSenderTokens sender matching cases`(): List<DynamicTest> {
        val cases = listOf(
            // --- DLT-prefixed/suffixed variants (long, distinctive tokens) ---
            Case("DLT prefix - AD-BKASH matches BKASH", "AD-BKASH", listOf("BKASH"), true),
            Case("DLT suffix - BKASH-BD matches BKASH", "BKASH-BD", listOf("BKASH"), true),
            Case("DLT prefix - VM-NAGAD matches NAGAD", "VM-NAGAD", listOf("NAGAD"), true),

            // --- DLT-prefixed/suffixed variants (SHORT, generic tokens) ---
            Case("Short token DLT prefix - AD-TAP matches TAP", "AD-TAP", listOf("TAP"), true),
            Case("Short token DLT suffix - TAP-BD matches TAP", "TAP-BD", listOf("TAP"), true),
            Case("Short token DLT prefix+suffix - BD-TAP-01 matches TAP", "BD-TAP-01", listOf("TAP"), true),
            Case("Short token DLT prefix - AD-UPAY matches UPAY", "AD-UPAY", listOf("UPAY"), true),
            Case("Short token DLT prefix - AD-DBBL matches DBBL", "AD-DBBL", listOf("DBBL"), true),

            // --- lowercase input ---
            Case("Lowercase sender matches", "bkash", listOf("BKASH"), true),
            Case("Lowercase short-token sender matches", "tap", listOf("TAP"), true),
            Case("Lowercase configured token matches", "BKASH", listOf("bkash"), true),

            // --- punctuation / spacing noise ---
            Case("Dots as separators", "AD.BKASH", listOf("BKASH"), true),
            Case("Spaces as separators", "AD BKASH", listOf("BKASH"), true),
            Case("Mixed punctuation and case", "ad-Bkash.sms", listOf("BKASH"), true),
            Case("Configured token has punctuation/spacing", "TRUSTAXIATA", listOf("trust axiata"), true),

            // --- short-token false positives (the core safety hazard) ---
            Case("TAPZO must NOT match TAP (single unrelated segment)", "TAPZO", listOf("TAP"), false),
            Case("STAPLES must NOT match TAP (TAP is a substring, not a segment)", "STAPLES", listOf("TAP"), false),
            Case("WHATSAPP must NOT match TAP", "WHATSAPP", listOf("TAP"), false),
            Case("UPAYTM must NOT match UPAY (single unrelated segment)", "UPAYTM", listOf("UPAY"), false),
            Case("DBBLE must NOT match DBBL (single unrelated segment)", "DBBLE", listOf("DBBL"), false),
            // ... but a short token IS still allowed to match when it is genuinely its own segment
            Case("Short token still matches as an exact whole sender", "TAP", listOf("TAP"), true),

            // --- non-matches ---
            Case("Unrelated bank name does not match", "HDFC", listOf("BKASH", "16247"), false),
            Case("Empty sender never matches", "", listOf("BKASH"), false),
            Case("Numeric shortcode does not match a different token list", "16247", listOf("NAGAD", "16167"), false),
            Case("Long token substring in unrelated word is still excluded when no overlap exists", "ROCKETSHIP", listOf("BKASH"), false)
        )

        return cases.map { case ->
            dynamicTest(case.label) {
                val actual = BangladeshMfsParser.matchesSenderTokens(case.sender, case.tokens)
                assertEquals(case.expected, actual, "sender='${case.sender}' tokens=${case.tokens}")
            }
        }
    }
}
