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
 * Parser for BRAC Bank Limited (BBL, Bangladesh) SMS messages.
 *
 * BBL sends outbound-transfer alerts of the shape "Tk X has been {debited|transferred} from
 * your BBL A/C: <masked> to <destination> ... Available balance ... Tk Y":
 *
 * Supported formats:
 * - "Tk X (incl. charges) has been debited from your BBL A/C: <masked> to A/C: <masked> for
 *    NPSB transfer. available balance Tk Y. Helpline: <phone>"
 * - "Tk X has been transferred from your BBL A/C: <masked> to BKASH wallet: ending <digits>.
 *    Available balance is Tk Y. Queries: call <phone>."
 * - "Tk X has been transferred from your BBL A/C: <masked> to A/C: <masked>. Available balance
 *    is Tk Y. Queries: call <phone>."
 *
 * All three formats describe money leaving the customer's BBL account, so all three are
 * EXPENSE transactions.
 *
 * Common senders: BRACBANK, BBL, BRAC Bank
 * Currency: BDT (Bangladeshi Taka)
 */
class BracBankParser : BangladeshBankParser() {

    override fun getBankName() = "BRAC Bank"

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase().trim()
        return upper.contains("BRACBANK") ||
                upper.contains("BRAC BANK") ||
                upper.contains("BBL") ||
                upper.matches(Regex("""^[A-Z]{2}-BRACBK-[A-Z]$"""))
    }

    /** "BBL A/C" is BRAC Bank's own account-label wording; "BRAC Bank" is the brand itself. */
    override fun matchesBodyMarkers(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("bbl a/c") || lower.contains("brac bank")
    }

    override fun matchesKnownFormat(message: String): Boolean = match(message) != null

    private val takaFigure = """([0-9][0-9,]*(?:\.\d{1,2})?)"""
    private val balanceSuffix = Regex(
        """available balance(?:\s+is)?\s+(?:Tk\.?\s*)?$takaFigure""",
        RegexOption.IGNORE_CASE
    )

    // "Tk X (incl. charges) has been debited from your BBL A/C: <masked> to ... for NPSB transfer"
    private val npsbDebitPattern = Regex(
        """Tk\.?\s*$takaFigure\s*\(incl\.\s*charges\)\s+has been debited from your BBL A/C:\s*[0-9*]+\s+to\s+(?:A/C:\s*[0-9*]+|Account\s+\S+)\s+for\s+NPSB transfer""",
        RegexOption.IGNORE_CASE
    )

    // "Tk X has been transferred from your BBL A/C: <masked> to BKASH wallet: ending <digits>"
    private val bkashTransferPattern = Regex(
        """Tk\.?\s*$takaFigure\s+has been transferred from your BBL A/C:\s*[0-9*]+\s+to\s+BKASH wallet:\s*ending\s+\d+""",
        RegexOption.IGNORE_CASE
    )

    // "Tk X has been transferred from your BBL A/C: <masked> to A/C: <masked>"
    private val accountTransferPattern = Regex(
        """Tk\.?\s*$takaFigure\s+has been transferred from your BBL A/C:\s*[0-9*]+\s+to\s+A/C:\s*[0-9*]+""",
        RegexOption.IGNORE_CASE
    )

    private data class Match(val amount: String, val merchant: String?)

    private fun match(message: String): Match? {
        npsbDebitPattern.find(message)?.let { return Match(it.groupValues[1], "NPSB Transfer") }
        bkashTransferPattern.find(message)?.let { return Match(it.groupValues[1], "bKash") }
        accountTransferPattern.find(message)?.let { return Match(it.groupValues[1], null) }
        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (match(message) != null) return true
        return super.isTransactionMessage(message)
    }

    override fun extractAmount(message: String): BigDecimal? =
        match(message)?.let { parseTakaAmount(it.amount) }

    override fun extractBalance(message: String): BigDecimal? =
        balanceSuffix.find(message)?.let { parseTakaAmount(it.groupValues[1]) }

    // All three BBL formats describe money leaving the customer's own account.
    override fun extractTransactionType(message: String): TransactionType? =
        match(message)?.let { TransactionType.EXPENSE }

    override fun extractMerchant(message: String, sender: String): String? =
        match(message)?.merchant
}
