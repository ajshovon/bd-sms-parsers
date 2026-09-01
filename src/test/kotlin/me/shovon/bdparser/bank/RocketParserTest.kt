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

class RocketParserTest {

    @TestFactory
    fun `test Rocket Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = RocketParser()

        ParserTestUtils.printTestHeader(
            parserName = "Rocket (DBBL)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // No space between "Tk" and the figure - must still parse correctly
            ParserTestCase(
                name = "Received - no space after Tk",
                message = "Tk1,000.00 received from A/C 01700000000. Fee Tk0.00. Balance Tk1,234.56. TxnId:XXXX Date:01/01/2024 10:30 PM",
                sender = "Rocket",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("1234.56"),
                    reference = "XXXX"
                )
            ),
            // Fee-vs-amount hazard: amount must be 500.00, NOT the fee (9.00) or balance (700.00)
            ParserTestCase(
                name = "Cash Out - fee and balance exclusion hazard, no space after Tk",
                message = "Cash Out Tk500.00 from A/C 01700000000. Fee Tk9.00. Balance Tk700.00. TxnId:YYYY Date:01/01/2024 11:00 AM",
                sender = "16216",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("700.00"),
                    reference = "YYYY"
                )
            ),
            ParserTestCase(
                name = "Debited for bill payment",
                message = "Your A/C 01700000000 has been debited Tk 300.00 for bill payment. Balance Tk 950.00. TxnId:ZZZZ Date:01/01/2024 12:00 PM",
                sender = "DBBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("950.00"),
                    reference = "ZZZZ"
                )
            ),

            // Fee appears BEFORE the amount in the message body - amount must still be 500.00
            ParserTestCase(
                name = "Fee-first ordering - fee before amount hazard",
                message = "Fee Tk9.00. Cash Out Tk500.00 from A/C 01700000000. Balance Tk700.00. TxnId:QQQQ Date:01/01/2024 19:00 PM",
                sender = "Rocket",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("700.00"),
                    reference = "QQQQ"
                )
            ),
            // Currency token variant: "৳" instead of "Tk"
            ParserTestCase(
                name = "Currency token variant - Taka sign",
                message = "৳500.00 received from A/C 01800000000. Fee ৳0.00. Balance ৳1,000.00. TxnId:RRRR Date:01/01/2024 20:00 PM",
                sender = "Rocket",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("1000.00"),
                    reference = "RRRR"
                )
            ),

            // Negative cases
            ParserTestCase(
                name = "OTP message is ignored",
                message = "Your Rocket OTP is 112233. Do not share.",
                sender = "Rocket",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message is ignored",
                message = "Special discount offer on Rocket Cash Out fees this month!",
                sender = "Rocket",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Balance-enquiry-only message is ignored",
                message = "Your Rocket Balance is Tk 950.00",
                sender = "Rocket",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Failed/unsuccessful transaction is ignored",
                message = "Cash Out Tk500.00 unsuccessful due to insufficient balance.",
                sender = "Rocket",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "Rocket" to true,
            "DBBL" to true,
            "16216" to true,
            // DLT-prefixed/suffixed sender variants
            "AD-ROCKET" to true,
            "ROCKET-BD" to true,
            "AD-DBBL" to true,
            "DBBL-BD" to true,
            // short-token ("DBBL") false-positive guard: "DBBLE" is a different, unrelated
            // single segment and must NOT match
            "DBBLE" to false,
            "bKash" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Rocket Parser Tests")
    }
}
