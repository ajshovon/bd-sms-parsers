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
 * Parser for Upay (Bangladesh) mobile financial service SMS messages.
 *
 * Supported formats:
 * - "You have received Tk 500.00 from 01XXXXXXXXX. TxnID: ABC. Balance: Tk 1,000.00"
 * - "Cash Out Tk 500.00 successful. Fee Tk 8.50. Balance Tk 400.00. TxnID: ..."
 *
 * Common senders: upay, UPAY, 16268
 * Currency: BDT (Bangladeshi Taka)
 */
class UpayParser : BangladeshMfsParser() {

    override fun getBankName() = "Upay"

    override val senderTokens = listOf("UPAY", "16268")

    /**
     * Body-marker fallback for when Mobile Number Portability has rewritten the sender ID away
     * from a recognisable Upay sender: matches when the body mentions "Upay" by name.
     */
    override fun canHandleMessage(sender: String, message: String): Boolean {
        if (canHandle(sender)) return true
        return message.contains("upay", ignoreCase = true)
    }
}
