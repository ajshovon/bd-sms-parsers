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

/**
 * Utility to filter and identify transaction messages.
 */
object SmsFilter {

    /**
     * Checks if the message is a transaction message (not OTP, promotional, etc.)
     */
    fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP messages
        if (lowerMessage.contains("otp") ||
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code")
        ) {
            return false
        }

        // Skip promotional messages unless message carries an explicit transaction confirmation
        val hasExplicitTxn = lowerMessage.contains("trxid") ||
            lowerMessage.contains("txnid") ||
            lowerMessage.contains("ref no") ||
            lowerMessage.contains("reference no") ||
            lowerMessage.contains("ref:") ||
            (lowerMessage.contains("debited") && (lowerMessage.contains("a/c") || lowerMessage.contains("acct") || lowerMessage.contains("card"))) ||
            (lowerMessage.contains("credited") && (lowerMessage.contains("a/c") || lowerMessage.contains("acct") || lowerMessage.contains("card")))

        if (!hasExplicitTxn) {
            if (lowerMessage.contains("offer") ||
                lowerMessage.contains("discount") ||
                lowerMessage.contains("cashback offer") ||
                lowerMessage.contains("win ")
            ) {
                return false
            }
        }

        // Skip payment request messages (common across banks)
        if (lowerMessage.contains("has requested") ||
            lowerMessage.contains("payment request") ||
            lowerMessage.contains("collect request") ||
            lowerMessage.contains("requesting payment") ||
            lowerMessage.contains("requests rs") ||
            lowerMessage.contains("ignore if already paid")
        ) {
            return false
        }

        // Skip merchant payment acknowledgments
        if (lowerMessage.contains("have received payment")) {
            return false
        }

        // Skip payment reminder/due messages
        if (lowerMessage.contains("is due") ||
            lowerMessage.contains("min amount due") ||
            lowerMessage.contains("minimum amount due") ||
            lowerMessage.contains("in arrears") ||
            lowerMessage.contains("is overdue") ||
            lowerMessage.contains("ignore if paid") ||
            (lowerMessage.contains("pls pay") && lowerMessage.contains("min of"))
        ) {
            return false
        }

        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "debited", "credited", "withdrawn", "withdrawal", "withdrawing", "deposited",
            "spent", "received", "transferred", "paid", "payment", "purchase", "credit", "debit"
        )

        return transactionKeywords.any { lowerMessage.contains(it) }
    }
}
