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

import me.shovon.bdparser.TransactionType
import java.math.BigDecimal

/**
 * Parser for City Bank (Bangladesh) SMS messages.
 *
 * City Bank's transaction alerts share one compact shape - a channel label, a "Tk." amount,
 * an action word, and the resulting account balance, e.g.:
 * "10-Aug-26 ATM TXN Tk. 5,000.00 Withdrawal Tk. 45,320.00 Balance A/C: 12345**6789 NPSB Fee: Tk 15.00"
 *
 * Supported formats (channel label optional in one legacy variant):
 * - "<date> ATM TXN Tk. X Deposit Tk. Y Balance A/C: <masked>"
 * - "<date> ATM TXN Tk. X Withdrawal Tk. Y Balance A/C: <masked> [NPSB Fee: Tk Z] [Please rate us: ...]"
 * - "<date> CITYTOUCH TXN Tk. X Deposit Tk. Y Balance A/C: <masked>"
 * - "<date> CITYTOUCH TXN Tk. X Withdrawal Tk. Y Balance A/C: <masked> [NPSB Fee: Tk Z]"
 * - "<date> E-COMM/POS TXN Tk. X Deposit Tk. Y Balance A/C: <masked>"
 * - "<date> E-COMM/POS TXN Tk. X Purchased Tk. Y Balance A/C: <masked>"
 * - "<date> Tk. X Deposit Tk. Y Balance A/C: <masked>" (no channel label)
 *
 * The NPSB Fee and "Please rate us" suffixes are optional trailing noise and must never be
 * confused with the transaction amount or the account balance, both of which are anchored
 * precisely by this parser's patterns.
 *
 * Common senders: CITYBANK, City Bank, CITYTOUCH
 * Currency: BDT (Bangladeshi Taka)
 */
class CityBankParser : BangladeshBankParser() {

    override fun getBankName() = "City Bank"

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase().trim()
        return upper.contains("CITYBANK") ||
                upper.contains("CITY BANK") ||
                upper.contains("CITYTOUCH") ||
                upper.matches(Regex("""^[A-Z]{2}-CITYBK-[A-Z]$"""))
    }

    /**
     * Body-marker fallback for when Mobile Number Portability has rewritten the sender ID away
     * from a recognisable City Bank sender: matches when the body mentions "citytouch" (City
     * Bank's digital banking channel) or "City Bank" by name.
     */
    override fun canHandleMessage(sender: String, message: String): Boolean {
        if (canHandle(sender)) return true
        val lower = message.lowercase()
        return lower.contains("citytouch") || lower.contains("city bank")
    }

    // Groups: 1 = amount, 2 = action word (Deposit/Withdrawal/Purchased), 3 = balance
    private val channelTxnPattern = Regex(
        """(?:ATM TXN|CITYTOUCH TXN|E-COMM/POS TXN)\s+Tk\.\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s+(Deposit|Withdrawal|Purchased)\s+Tk\.\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s+Balance""",
        RegexOption.IGNORE_CASE
    )

    // Legacy/no-channel-label variant: "Tk. X Deposit Tk. Y Balance A/C:" without a preceding
    // "... TXN" channel label immediately before the "Tk."
    private val bareDepositPattern = Regex(
        """(?<!TXN\s)\bTk\.\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s+(Deposit)\s+Tk\.\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s+Balance""",
        RegexOption.IGNORE_CASE
    )

    private fun match(message: String) = channelTxnPattern.find(message) ?: bareDepositPattern.find(message)

    override fun isTransactionMessage(message: String): Boolean {
        if (match(message) != null) return true
        return super.isTransactionMessage(message)
    }

    override fun extractAmount(message: String): BigDecimal? =
        match(message)?.let { parseTakaAmount(it.groupValues[1]) }

    override fun extractBalance(message: String): BigDecimal? =
        match(message)?.let { parseTakaAmount(it.groupValues[3]) }

    override fun extractTransactionType(message: String): TransactionType? {
        return when (match(message)?.groupValues?.get(2)?.lowercase()) {
            "deposit" -> TransactionType.INCOME
            "withdrawal", "purchased" -> TransactionType.EXPENSE
            else -> null
        }
    }

    // No counterparty/merchant name is present in any City Bank ATM/CityTouch/POS alert -
    // only a channel + action label, which is not a merchant.
    override fun extractMerchant(message: String, sender: String): String? = null
}
