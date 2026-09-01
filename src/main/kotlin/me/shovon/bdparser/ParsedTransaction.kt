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
package me.shovon.bdparser

import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Locale

data class ParsedTransaction(
    val amount: BigDecimal,
    val type: TransactionType,
    val merchant: String?,
    val reference: String?,
    val accountLast4: String?,
    val balance: BigDecimal?,
    val creditLimit: BigDecimal? = null,
    val smsBody: String,
    val sender: String,
    val timestamp: Long,
    val bankName: String,
    val transactionHash: String? = null,
    val isFromCard: Boolean = false,
    val currency: String = "BDT",
    val fromAccount: String? = null,
    val toAccount: String? = null,
    /**
     * True when [balance] - as reported by a regular (non-statement) card transaction SMS -
     * represents the card's remaining AVAILABLE CREDIT rather than the outstanding amount owed.
     * Mirrors [me.shovon.bdparser.bank.BankParser.creditCardBalanceIsAvailableCredit] at
     * parse time. Only meaningful once the instrument is confirmed to be a credit card
     * downstream; irrelevant for non-card/debit-card balances.
     */
    val creditCardBalanceIsAvailableCredit: Boolean = false
) {
    fun generateTransactionId(): String {
        val normalizedAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP)
        // Use SMS body hash for reliable deduplication across different timestamp sources
        // (BroadcastReceiver uses SC timestamp, ContentProvider uses device timestamp)
        val smsBodyHash = MessageDigest.getInstance("SHA-256")
            .digest(smsBody.toByteArray())
            .joinToString("") { String.format(Locale.ROOT, "%02x", it) }
            .take(16) // First 16 chars of SMS body hash
        val data = "$sender|$normalizedAmount|$smsBodyHash"
        return MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray())
            .joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }
}
