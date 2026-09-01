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

class UpayParserTest {

    @TestFactory
    fun `test Upay Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = UpayParser()

        ParserTestUtils.printTestHeader(
            parserName = "Upay",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Received",
                message = "You have received Tk 500.00 from 01700000000. TxnID: ABC. Balance: Tk 1,000.00",
                sender = "upay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("1000.00"),
                    reference = "ABC"
                )
            ),
            // Fee-vs-amount hazard: amount must be 500.00, NOT the fee (8.50) or balance (400.00)
            ParserTestCase(
                name = "Cash Out - fee and balance exclusion hazard",
                message = "Cash Out Tk 500.00 successful. Fee Tk 8.50. Balance Tk 400.00. TxnID: DEF",
                sender = "UPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("400.00"),
                    reference = "DEF"
                )
            ),
            ParserTestCase(
                name = "Payment to merchant",
                message = "Payment Tk 300.00 to SHOP UPAY successful. Fee Tk 0.00. Balance Tk 700.00. TxnID: GHI",
                sender = "16268",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "SHOP UPAY",
                    balance = BigDecimal("700.00"),
                    reference = "GHI"
                )
            ),

            // Fee appears BEFORE the amount in the message body - amount must still be 500.00
            ParserTestCase(
                name = "Fee-first ordering - fee before amount hazard",
                message = "Fee Tk 8.50 applies. Cash Out Tk 500.00 successful. Balance Tk 400.00. TxnID: JKL",
                sender = "upay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("400.00"),
                    reference = "JKL"
                )
            ),

            // Negative cases
            ParserTestCase(
                name = "OTP message is ignored",
                message = "Your Upay OTP is 998877.",
                sender = "upay",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message is ignored",
                message = "Win exciting prizes with Upay cashback offer!",
                sender = "upay",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Balance-enquiry-only message is ignored",
                message = "Your Upay Balance is Tk 400.00",
                sender = "upay",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Failed transaction is ignored",
                message = "Cash Out Tk 500.00 failed. Please try again.",
                sender = "upay",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "upay" to true,
            "UPAY" to true,
            "16268" to true,
            // DLT-prefixed/suffixed sender variants
            "AD-UPAY" to true,
            "UPAY-BD" to true,
            "bd-upay" to true,
            // short-token ("UPAY") false-positive guard: "UPAYTM" is a different, unrelated
            // single segment and must NOT match
            "UPAYTM" to false,
            "bKash" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Upay Parser Tests")
    }
}
