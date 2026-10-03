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

/**
 * Parser for Tap (Trust Axiata Digital, Bangladesh) mobile financial service SMS messages.
 *
 * Shares the same shape as Upay/bKash: received / send money / cash out / payment,
 * with a Fee/Balance/TrxID trailer.
 *
 * Common senders: TAP, Tap, "trust axiata", and DLT-prefixed/suffixed variants (e.g.
 * "AD-TAP", "TAP-BD").
 * Currency: BDT (Bangladeshi Taka)
 *
 * "TAP" is a short, generic token that could otherwise collide with unrelated sender IDs
 * (e.g. "TAPZO", "STAPLES") if matched with plain substring containment. It is matched via
 * [BangladeshMfsParser.matchesSenderTokens], which requires short tokens like this one to
 * appear as a whole, separator-delimited segment of the sender - so DLT-prefixed variants
 * ("AD-TAP") match while unrelated words that merely contain "tap" do not.
 */
class TapParser : BangladeshMfsParser() {

    override fun getBankName() = "Tap"

    override val senderTokens = listOf("TAP", "TRUST AXIATA")

    /**
     * Body-marker fallback for when Mobile Number Portability has rewritten the sender ID away
     * from a recognisable Tap sender: matches when the body mentions "Trust Axiata" by name, or
     * "Tap" as a whole word. "Tap" alone is short and generic, so it is matched with a
     * word-boundary regex to avoid false positives inside unrelated words (e.g. "WhatsApp",
     * "tapped").
     */
    override fun canHandleMessage(sender: String, message: String): Boolean {
        if (canHandle(sender)) return true
        val lower = message.lowercase()
        if (lower.contains("trust axiata")) return true
        return tapWordPattern.containsMatchIn(message)
    }

    /** Compiled once; [canHandleMessage] runs for every parser against every unmatched SMS. */
    private val tapWordPattern = Regex("""\btap\b""", RegexOption.IGNORE_CASE)
}
