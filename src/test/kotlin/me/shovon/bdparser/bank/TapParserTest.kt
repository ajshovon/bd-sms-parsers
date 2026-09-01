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
import me.shovon.bdparser.test.ExpectedTransaction
import me.shovon.bdparser.test.ParserTestCase
import me.shovon.bdparser.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class TapParserTest {

    @TestFactory
    fun `test Tap Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = TapParser()

        ParserTestUtils.printTestHeader(
            parserName = "Tap",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Received",
                message = "You have received Tk 500.00 from 01700000000. TxnID: ABC. Balance: Tk 1,000.00",
                sender = "TAP",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("1000.00"),
                    reference = "ABC"
                )
            ),
            // Fee-vs-amount hazard: amount must be 200.00, NOT the fee (5.00) or balance (800.00)
            ParserTestCase(
                name = "Send Money - fee and balance exclusion hazard",
                message = "Send Money Tk 200.00 to 01700000000 successful. Fee Tk 5.00. Balance Tk 800.00. TxnID: DEF",
                sender = "Tap",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("800.00"),
                    reference = "DEF"
                )
            ),
            ParserTestCase(
                name = "Cash Out",
                message = "Cash Out Tk 500.00 successful. Fee Tk 8.50. Balance Tk 400.00. TxnID: GHI",
                sender = "trust axiata",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("400.00"),
                    reference = "GHI"
                )
            ),
            ParserTestCase(
                name = "Payment to merchant",
                message = "Payment Tk 350.00 to SHOP TAP successful. Fee Tk 0.00. Balance Tk 650.00. TxnID: JKL",
                sender = "TAP",
                expected = ExpectedTransaction(
                    amount = BigDecimal("350.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "SHOP TAP",
                    balance = BigDecimal("650.00"),
                    reference = "JKL"
                )
            ),

            // Fee appears BEFORE the amount in the message body - amount must still be 300.00
            ParserTestCase(
                name = "Fee-first ordering - fee before amount hazard",
                message = "Fee Tk 5.00 applies. Send Money Tk 300.00 to 01900000000 successful. Balance Tk 750.00. TxnID: UVW",
                sender = "TAP",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("750.00"),
                    reference = "UVW"
                )
            ),

            // Negative cases
            ParserTestCase(
                name = "OTP message is ignored",
                message = "Your Tap OTP is 445566.",
                sender = "TAP",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message is ignored",
                message = "Special discount offer on Tap this week!",
                sender = "TAP",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Balance-enquiry-only message is ignored",
                message = "Your Tap Balance is Tk 400.00",
                sender = "TAP",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Failed/unsuccessful transaction is ignored",
                message = "Cash Out Tk 500.00 unsuccessful.",
                sender = "TAP",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "TAP" to true,
            "Tap" to true,
            "trust axiata" to true,
            "TRUST AXIATA" to true,
            // DLT-prefixed/suffixed sender variants of the short "TAP" token now match, because
            // "TAP" appears as its own separator-delimited segment
            "AD-TAP" to true,
            "TAP-BD" to true,
            "BD-TAP" to true,
            "bd.tap" to true,
            // "TAPZO" must NOT match - guards against false positives on the short "TAP" token
            // (single segment "TAPZO" is not equal to "TAP")
            "TAPZO" to false,
            // "WHATSAPP" must NOT match either - an unrelated sender whose letters happen to
            // include t/a/p is not the same as containing "TAP" as a segment or substring
            "WHATSAPP" to false,
            "bKash" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Tap Parser Tests")
    }
}
