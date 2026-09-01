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

class CityBankParserTest {

    @TestFactory
    fun `test City Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = CityBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "ATM TXN Withdrawal with NPSB fee - amount must not be fee or balance",
                message = "01-Jan-24 ATM TXN Tk. 5,000.00 Withdrawal Tk. 20,000.00 Balance A/C: 12345**6789 NPSB Fee: Tk 15.00",
                sender = "CITYBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("20000.00"),
                    accountLast4 = "6789",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "ATM TXN Deposit with rate-us suffix",
                message = "02-Jan-24 ATM TXN Tk. 2,000.00 Deposit Tk. 25,000.00 Balance A/C: 12345**6789 Please rate us: bit.ly/xyz",
                sender = "CITYBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("25000.00"),
                    accountLast4 = "6789"
                )
            ),
            ParserTestCase(
                name = "CITYTOUCH TXN Withdrawal",
                message = "03-Jan-24 CITYTOUCH TXN Tk. 1,200.00 Withdrawal Tk. 30,000.00 Balance A/C: 98765**4321",
                sender = "CITYTOUCH",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1200.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("30000.00"),
                    accountLast4 = "4321"
                )
            ),
            ParserTestCase(
                name = "CITYTOUCH TXN Deposit",
                message = "04-Jan-24 CITYTOUCH TXN Tk. 800.00 Deposit Tk. 35,000.00 Balance A/C: 98765**4321",
                sender = "CITYBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("800.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("35000.00")
                )
            ),
            ParserTestCase(
                name = "E-COMM/POS TXN Purchased",
                message = "05-Jan-24 E-COMM/POS TXN Tk. 650.00 Purchased Tk. 40,000.00 Balance A/C: 98765**4321",
                sender = "CITYBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("650.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("40000.00")
                )
            ),
            ParserTestCase(
                name = "E-COMM/POS TXN Deposit (refund)",
                message = "06-Jan-24 E-COMM/POS TXN Tk. 300.00 Deposit Tk. 45,000.00 Balance A/C: 98765**4321",
                sender = "CITYBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("45000.00")
                )
            ),
            ParserTestCase(
                name = "No-channel-label Deposit variant",
                message = "07-Jan-24 Tk. 5,000.00 Deposit Tk. 50,000.00 Balance A/C: 98765**4321",
                sender = "CITYBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("50000.00")
                )
            ),

            // Negative cases
            ParserTestCase(
                name = "OTP message is rejected",
                message = "123456 is your City Bank OTP for online transaction. Valid for 3 minutes.",
                sender = "CITYBANK",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message is rejected",
                message = "Enjoy 20% discount on dining with your City Bank card this weekend. T&C apply.",
                sender = "CITYBANK",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Balance-enquiry-only message is rejected",
                message = "Your City Bank A/C: 12345**6789 balance is Tk. 45,000.00 as of 07-Jan-24.",
                sender = "CITYBANK",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Failed transaction message is rejected",
                message = "Your City Bank card transaction of Tk. 500.00 was declined due to insufficient funds.",
                sender = "CITYBANK",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "CITYBANK" to true,
            "City Bank" to true,
            "CITYTOUCH" to true,
            "AD-CITYBK-S" to true,
            "bKash" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "City Bank Parser Tests")
    }
}
