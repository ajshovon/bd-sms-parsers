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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal
import java.time.LocalDate

class EasternBankParserTest {

    @TestFactory
    fun `test Eastern Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = EasternBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "AC credited with NPSB fund transfer",
                message = "AC 12345**6789 is credited with BDT 5,000.00 as NPSB FUND TRANSFER on 01-Jan-24 11:15:00 AM BST",
                sender = "EBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    merchant = "NPSB Fund Transfer",
                    accountLast4 = "6789",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "AC debited with EBL Account Transfer",
                message = "AC 12345**6789 is debited with BDT 3,000.00 as EBL Account Transfer on 02-Jan-24 09:00:00 AM BST",
                sender = "EBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "EBL Account Transfer",
                    accountLast4 = "6789"
                )
            ),
            ParserTestCase(
                name = "AC debited with EBL Skybanking MFS Transfer-bKash",
                message = "AC 12345**6789 is debited with BDT 1,500.00 as EBL Skybanking MFS Transfer-bKash on 03-Jan-24 06:10:00 PM BST",
                sender = "EBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "bKash",
                    accountLast4 = "6789"
                )
            ),
            ParserTestCase(
                name = "EBL CARDS NPSB Fund Transfer using card - amount must not be card number",
                message = "EBL CARDS: NPSB Fund Transfer BDT 2,200.00 using Card 532900**0000 on 04-Jan-24 02:45:00 PM BST",
                sender = "EBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2200.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "NPSB Fund Transfer",
                    accountLast4 = "0000",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                // The trailing "Balance: BDT Y" on this shape reports the card's remaining
                // AVAILABLE CREDIT, same semantic as the purchase shape below (see
                // EasternBankParser class doc and the `match` comment on
                // cardsPaymentCreditedPattern), so it must be captured here too.
                name = "EBL CARDS Payment credited to card - amount must not be balance, and balance must be captured",
                message = "EBL CARDS: Payment of BDT 12,500.00 credited to Card 532900**0000 on 05-Jan-24 10:30:00 AM. Balance: BDT 50000.00. Thank You. EBL Helpline 16001",
                sender = "EBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12500.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("50000.00"),
                    accountLast4 = "0000",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "EBL CARDS Purchase txn - merchant and balance extraction",
                message = "EBL CARDS: Purchase txn BDT 480 from SAMPLE FOOD OUTLET BD. Card 532900**0000 on 06-Jan-24 04:50:00 PM BST. Balance: BDT 40000.00. EBL Helpline 16001",
                sender = "EBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("480"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "SAMPLE FOOD OUTLET BD",
                    balance = BigDecimal("40000.00"),
                    accountLast4 = "0000",
                    isFromCard = true
                )
            ),

            // Negative cases
            ParserTestCase(
                name = "OTP message is rejected",
                message = "112233 is your EBL OTP for card verification. Valid for 2 minutes.",
                sender = "EBL",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message is rejected",
                message = "EBL CARDS: Enjoy 15% cashback offer on your next purchase this month!",
                sender = "EBL",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Monthly bill/statement notice is rejected",
                message = "Monthly bill 532900******0000 JAN2020; Total Due: BDT 10000.00, Min Due: BDT 1000.00, Last Pmt: 01-JAN-20. Statement link https://example.com/statement",
                sender = "EBL",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Balance-enquiry-only message is rejected",
                message = "Your EBL AC 12345**6789 balance is BDT 50,000.00 as of 01-Jan-24.",
                sender = "EBL",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "EBL" to true,
            "Eastern Bank" to true,
            "AD-EBL-S" to true,
            "bKash" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Eastern Bank Parser Tests")
    }

    @TestFactory
    fun `test EBL CARDS balance capture is consistent between payment-credited and purchase shapes`(): List<DynamicTest> {
        val parser = EasternBankParser()
        val paymentCreditedMessage = "EBL CARDS: Payment of BDT 12,500.00 credited to Card 532900**0000 on " +
            "05-Jan-24 10:30:00 AM. Balance: BDT 50000.00. Thank You. EBL Helpline 16001"
        val purchaseMessage = "EBL CARDS: Purchase txn BDT 480 from SAMPLE FOOD OUTLET BD. Card 532900**0000 " +
            "on 06-Jan-24 04:50:00 PM BST. Balance: BDT 40000.00. EBL Helpline 16001"

        return listOf(
            dynamicTest("Payment-credited message parses the transaction and yields its available-credit balance") {
                val parsed = parser.parse(paymentCreditedMessage, "EBL", System.currentTimeMillis())
                assertNotNull(parsed)
                parsed!!
                assertEquals(BigDecimal("12500.00"), parsed.amount)
                assertEquals(TransactionType.INCOME, parsed.type)
                assertEquals("0000", parsed.accountLast4)
                assertEquals(BigDecimal("50000.00"), parsed.balance)
            },
            dynamicTest("Purchase message still yields its available-credit balance") {
                val parsed = parser.parse(purchaseMessage, "EBL", System.currentTimeMillis())
                assertNotNull(parsed)
                parsed!!
                assertEquals(BigDecimal("40000.00"), parsed.balance)
            }
        )
    }

    @TestFactory
    fun `test Eastern Bank statement notice balance update parsing`(): List<DynamicTest> {
        val parser = EasternBankParser()
        val message = "Monthly bill 532900******0000 JAN2020; Total Due: BDT 10000.00, " +
            "Min Due: BDT 1000.00, Last Pmt: 01-JAN-20. Statement link https://example.com/statement"

        return listOf(
            dynamicTest("Statement notice is rejected by parse()") {
                assertNull(parser.parse(message, "EBL", System.currentTimeMillis()))
            },
            dynamicTest("Statement notice is detected as a balance update") {
                assertTrue(parser.isBalanceUpdateNotification(message))
            },
            dynamicTest("Statement notice parses into BalanceUpdateInfo") {
                val info = parser.parseBalanceUpdate(message)
                assertNotNull(info)
                info!!

                assertEquals("Eastern Bank", info.bankName)
                assertEquals("0000", info.accountLast4)
                assertTrue(info.isCreditCard)
                // Total Due is the CC outstanding anchor, mirrored into balance
                assertEquals(BigDecimal("10000.00"), info.balance)
                assertEquals(BigDecimal("10000.00"), info.totalDue)
                assertEquals(BigDecimal("1000.00"), info.minDue)
                assertEquals(LocalDate.of(2020, 1, 1), info.dueDate)
                assertEquals(LocalDate.of(2020, 1, 1), info.statementDate)
                assertNull(info.creditLimit)
            },
            dynamicTest("Non-statement message is not a balance update") {
                assertNull(
                    parser.parseBalanceUpdate(
                        "AC 12345**6789 is debited with BDT 100.00 as EBL Account Transfer on 01-Jan-20 12:00:00 AM BST"
                    )
                )
            }
        )
    }
}
