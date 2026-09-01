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

class NagadParserTest {

    @TestFactory
    fun `test Nagad Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = NagadParser()

        ParserTestUtils.printTestHeader(
            parserName = "Nagad",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Money Received",
                message = "Money Received. Amount: Tk 500.00, Sender: 01700000000, Ref: xxx, TxnID: ABC123, Balance: Tk 1,000.00",
                sender = "NAGAD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("1000.00"),
                    // Must prefer the TxnID over the generic "Ref: xxx"
                    reference = "ABC123"
                )
            ),
            // Fee-vs-amount hazard: amount must be 1000.00, NOT the fee (14.50) or balance (500.00)
            ParserTestCase(
                name = "Cash Out Successful - fee and balance exclusion hazard",
                message = "Cash Out Successful. Amount: Tk 1,000.00, Fee: Tk 14.50, Balance: Tk 500.00, TxnID: DEF456",
                sender = "NAGAD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("500.00"),
                    reference = "DEF456"
                )
            ),
            ParserTestCase(
                name = "Send Money Successful",
                message = "Send Money Successful. Amount: Tk 200.00, Receiver: 01700000000, Balance: Tk 300.00 TxnID: GHI789",
                sender = "16167",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("300.00"),
                    reference = "GHI789"
                )
            ),
            ParserTestCase(
                name = "Payment Successful to merchant",
                message = "Payment Successful. Amount: Tk 250.00, Merchant: SHOP, Balance: Tk 750.00, TxnID: JKL012",
                sender = "NAGAD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "SHOP",
                    balance = BigDecimal("750.00"),
                    reference = "JKL012"
                )
            ),

            // Fee appears BEFORE Amount in the field list - the explicit "Amount:" label
            // must still win over the earlier "Fee:" figure
            ParserTestCase(
                name = "Fee-first ordering - fee before amount hazard",
                message = "Cash Out Successful. Fee: Tk 20.00, Amount: Tk 1,500.00, Balance: Tk 800.00, TxnID: MNO345",
                sender = "NAGAD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("800.00"),
                    reference = "MNO345"
                )
            ),
            // Currency token variant: "BDT" instead of "Tk"
            ParserTestCase(
                name = "Currency token variant - BDT",
                message = "Money Received. Amount: BDT 650.00, Sender: 01900000000, Ref: yyy, TxnID: PQR678, Balance: BDT 1,200.00",
                sender = "NAGAD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("650.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("1200.00"),
                    reference = "PQR678"
                )
            ),

            // Negative cases
            ParserTestCase(
                name = "OTP message is ignored",
                message = "Your Nagad OTP is 456789. Valid for 5 minutes.",
                sender = "NAGAD",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message is ignored",
                message = "Get 10% cashback offer on Nagad Send Money this week!",
                sender = "NAGAD",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Balance-enquiry-only message is ignored",
                message = "Your Nagad Balance is Tk 500.00",
                sender = "NAGAD",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Failed/unsuccessful transaction is ignored",
                message = "Cash Out Unsuccessful. Amount: Tk 1,000.00. Please try again.",
                sender = "NAGAD",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "NAGAD" to true,
            "Nagad" to true,
            "16167" to true,
            // DLT-prefixed/suffixed sender variants
            "AD-NAGAD" to true,
            "NAGAD-BD" to true,
            "nagad.official" to true,
            "bKash" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Nagad Parser Tests")
    }
}
