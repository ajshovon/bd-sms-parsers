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
 * Parser for Rocket (Dutch-Bangla Bank / DBBL) mobile financial service SMS messages.
 *
 * Rocket frequently omits the space between the "Tk" currency token and the figure
 * (e.g. "Tk1,000.00"), which the shared [CompiledPatterns.TakaAmount] patterns already
 * tolerate.
 *
 * Supported formats:
 * - "Tk1,000.00 received from A/C 01XXXXXXXXX. Fee Tk0.00. Balance Tk1,234.56. TxnId:XXXX Date:..."
 * - "Cash Out Tk500.00 from A/C ... Fee Tk9.00 ... Balance Tk700.00. TxnId:..."
 * - "Your A/C 01XXXXXXXXX has been debited Tk 300.00 ... Balance Tk ..."
 *
 * Common senders: Rocket, 16216, DBBL
 * Currency: BDT (Bangladeshi Taka)
 */
class RocketParser : BangladeshMfsParser() {

    override fun getBankName() = "Rocket (DBBL)"

    override val senderTokens = listOf("ROCKET", "DBBL", "16216")

    /**
     * Body-marker fallback for when Mobile Number Portability has rewritten the sender ID away
     * from a recognisable Rocket sender: matches when the body mentions "Rocket" or "DBBL"
     * (Rocket's issuing bank) by name.
     */
    override fun canHandleMessage(sender: String, message: String): Boolean {
        if (canHandle(sender)) return true
        val lower = message.lowercase()
        if (lower.contains("rocket")) return true
        return dbblWordPattern.containsMatchIn(message)
    }

    /** Compiled once; [canHandleMessage] runs for every parser against every unmatched SMS. */
    private val dbblWordPattern = Regex("""\bdbbl\b""", RegexOption.IGNORE_CASE)
}
