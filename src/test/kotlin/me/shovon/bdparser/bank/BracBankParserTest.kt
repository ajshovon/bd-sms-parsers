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

class BracBankParserTest {

    @TestFactory
    fun `test BRAC Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = BracBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "NPSB transfer debit - amount must not be the balance",
                message = "Tk 5,500.00 (incl. charges) has been debited from your BBL A/C: 5*1234 to A/C: 9*4321 for NPSB transfer. available balance Tk 15,000.00. Helpline: 16221",
                sender = "BRACBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "NPSB Transfer",
                    balance = BigDecimal("15000.00"),
                    accountLast4 = "1234",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Transfer to bKash wallet",
                message = "Tk 1,000.00 has been transferred from your BBL A/C: 5*1234 to BKASH wallet: ending 4321. Available balance is Tk 20,000.00. Queries: call 16221.",
                sender = "BRACBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "bKash",
                    balance = BigDecimal("20000.00"),
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Transfer to another A/C",
                message = "Tk 2,500.00 has been transferred from your BBL A/C: 5*1234 to A/C: 7*8765. Available balance is Tk 25,000.00. Queries: call 16221.",
                sender = "BBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("25000.00"),
                    accountLast4 = "1234"
                )
            ),

            // Negative cases
            ParserTestCase(
                name = "OTP message is rejected",
                message = "654321 is your BRAC Bank OTP for fund transfer. Do not share with anyone.",
                sender = "BRACBANK",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message is rejected",
                message = "Get a special discount offer on your next BRAC Bank credit card purchase!",
                sender = "BRACBANK",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Balance-enquiry-only message is rejected",
                message = "Your BBL A/C: 5*1234 balance is Tk 25,000.00 as on today.",
                sender = "BRACBANK",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Failed transaction message is rejected",
                message = "Your BBL fund transfer of Tk 1,000.00 could not be completed. Please try again later.",
                sender = "BRACBANK",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "BRACBANK" to true,
            "BRAC Bank" to true,
            "BBL" to true,
            "AD-BRACBK-S" to true,
            "bKash" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "BRAC Bank Parser Tests")
    }
}
