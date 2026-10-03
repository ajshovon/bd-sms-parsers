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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TestSmsFilter {

    @Test
    fun `isTransactionMessage filters out OTPs`() {
        val otpMessages = listOf(
            "<#> 557026 is the One Time Password (OTP) for Phone Verification on NoBroker - World's largest brokerage free platform. A7jPtLVJWz3",
            "Your OTP for ICICI Bank is 1234. Do not share it with anyone.",
            "Verification code: 987654. Valid for 10 mins.",
            "Use 123456 as your one time password for Amazon Pay."
        )

        for (msg in otpMessages) {
            assertFalse(SmsFilter.isTransactionMessage(msg), "Should filter out OTP: $msg")
        }
    }

    @Test
    fun `isTransactionMessage filters out promotional messages`() {
        val promoMessages = listOf(
            "Get up to 50% discount on your next ride. Use code GO50.",
            "Special offer: Open your account and win up to Rs. 1000.",
            "Earn cashback offer on every UPI transaction."
        )

        for (msg in promoMessages) {
            assertFalse(SmsFilter.isTransactionMessage(msg), "Should filter out promo: $msg")
        }
    }

    @Test
    fun `isTransactionMessage filters out payment requests`() {
        val requestMessages = listOf(
            "SENDER NAME has requested Rs. 500 from you. Ignore if already paid.",
            "Payment request for Rs. 1200 is pending. Pay now to avoid late fee.",
            "Collect request of Rs.100 from VPA name@upi"
        )

        for (msg in requestMessages) {
            assertFalse(SmsFilter.isTransactionMessage(msg), "Should filter out request: $msg")
        }
    }

    @Test
    fun `isTransactionMessage accepts real transactions`() {
        val transactions = listOf(
            "Rs.500.00 debited from A/c XX1234. Avl Bal Rs.4500.00 Ref:REF001",
            "Rs.1000.00 credited to A/c XX1234 from SENDER NAME. Ref:TXN123",
            "You have spent Rs.250 at AMAZON via your Credit Card.",
            "ATM withdrawal of Rs.2000 successful for A/c XX5566."
        )

        for (msg in transactions) {
            assertTrue(SmsFilter.isTransactionMessage(msg), "Should accept transaction: $msg")
        }
    }

    @Test
    fun `isTransactionMessage accepts real transactions with promotional footers`() {
        val transactions = listOf(
            "Rs.500.00 debited from A/c XX1234. Avl Bal Rs.4500.00 Ref:REF001. Avail 10% discount on next ride!",
            "Payment Tk 150.00 successful. TrxID 8B7C. Offer valid till 31 Oct.",
            "Rs.1000.00 credited to A/c XX1234 from SENDER. Ref:TXN123. Win exciting prizes!"
        )

        for (msg in transactions) {
            assertTrue(SmsFilter.isTransactionMessage(msg), "Should accept transaction with footer: $msg")
        }
    }
}
