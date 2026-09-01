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
 * Parser for Nagad (Bangladesh) mobile financial service SMS messages.
 *
 * Supported formats:
 * - "Money Received. Amount: Tk 500.00, Sender: 01XXXXXXXXX, Ref: xxx, TxnID: ABC123, Balance: Tk 1,000.00"
 * - "Cash Out Successful. Amount: Tk 1,000.00, Fee: Tk 14.50, Balance: Tk 500.00, TxnID: ..."
 * - "Send Money Successful. Amount: Tk 200.00 ... Balance: Tk 300.00 TxnID: ..."
 * - "Payment Successful. Amount: Tk 250.00, Merchant: SHOP, Balance: Tk 750.00, TxnID: ..."
 *
 * Common senders: NAGAD, 16167
 * Currency: BDT (Bangladeshi Taka)
 */
class NagadParser : BangladeshMfsParser() {

    override fun getBankName() = "Nagad"

    override val senderTokens = listOf("NAGAD", "16167")

    /**
     * Body-marker fallback for when Mobile Number Portability has rewritten the sender ID away
     * from a recognisable Nagad sender: matches when the body mentions "Nagad" by name.
     */
    override fun canHandleMessage(sender: String, message: String): Boolean {
        if (canHandle(sender)) return true
        return message.contains("nagad", ignoreCase = true)
    }
}
