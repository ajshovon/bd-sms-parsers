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

class MutualTrustBankParserTest {

    @TestFactory
    fun `test Mutual Trust Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = MutualTrustBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "A/C Debited - amount must not be the balance",
                message = "Dear Customer, Your A/C XXXXX000000 has been Debited by BDT 15,000.00 on 01/01/24. Available balance BDT 1,00,000.00. MTB Hotline 16000",
                sender = "MTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("100000.00"),
                    accountLast4 = "0000",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "A/C Credited",
                message = "Dear Customer, Your A/C XXXXX000000 has been Credited by BDT 750.00 on 02/01/24. Available Balance BDT 1,50,000.00. MTB Hotline 16000",
                sender = "MTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("150000.00")
                )
            ),
            ParserTestCase(
                name = "Successful MOTO transaction - amount must not be card number or balance",
                message = "Successful MOTO transaction of BDT 415.00 from Sample Delivery Service by card- 4600***0000 on 03-Jan-24 at 09:30 pm. Current Balance BDT 80,000.00. Helpline 16000",
                sender = "MTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("415.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Sample Delivery Service",
                    balance = BigDecimal("80000.00"),
                    accountLast4 = "0000",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Successful purchase transaction with double space before merchant",
                message = "Successful purchase transaction of BDT 590.00 from  Sample Store Limited by MTB card- 4600***0000 on 04-Jan-24 at 10:45 am. Current balance BDT 90,000.00. Helpline 16000",
                sender = "MTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("590.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Sample Store Limited",
                    balance = BigDecimal("90000.00"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Payment received on MTB Card",
                message = "Payment of BDT 500.00 received on MTB Card-460041***0000 on 05-Jan-24 at 11:00 AM",
                sender = "MTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Payment credited to Card",
                message = "Payment of BDT 15000.00 credited to Card- 460041***0000 on 05-Jan-24 at 05:00 PM. Helpline 16000",
                sender = "MTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Generic account debit without A/C label",
                message = "Your account has been debited by BDT 2,250.00 on 06-Jan-24 at 12:00 am. Current balance BDT 60,000.00. Helpline 16000",
                sender = "MTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2250.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("60000.00")
                )
            ),
            ParserTestCase(
                name = "ATM Withdrawal using card - merchant is the ATM/branch location",
                message = "Withdrawal of BDT 8000 from Sample Branch ATM using card-460000***0000 on 07-Jan-24 at 08:00 pm. Current Balance BDT 40,000.00. Helpline 16000",
                sender = "MTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("8000"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Sample Branch ATM",
                    balance = BigDecimal("40000.00"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Merchant return posted to card - refund is INCOME",
                message = "Dear Cardholder: Merchant return amounting to BDT 55.00 has been posted to your MTB card 460041***0000 on 01-01-2020. Query- 16000",
                sender = "MTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("55.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    merchant = "Merchant Return"
                )
            ),

            // Negative cases - OTP (redacted codes, not real device data)
            ParserTestCase(
                name = "Plain OTP message is rejected",
                message = "123456 is your OTP (One Time Password) for online transaction. This OTP will be valid for next 3 minutes. Helpline-16000",
                sender = "MTB",
                shouldParse = false
            ),
            ParserTestCase(
                name = "IBFT fund transfer OTP is rejected",
                message = "<#> 000000 is your OTP for IBFT Fund Transfer and is valid for next 2 minutes. DO NOT SHARE your OTP with ANYONE. UID:REDACTED",
                sender = "MTB",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Remove-beneficiary OTP is rejected",
                message = "000000 is your OTP for Remove Beneficiary and is valid for next 2 minutes. DO NOT SHARE your OTP with ANYONE.",
                sender = "MTB",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message is rejected",
                message = "Enjoy a special cashback offer on your MTB card this festive season!",
                sender = "MTB",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Credit-card monthly bill/statement is rejected",
                message = "Your bill for card 460041***0000 for JAN 2020 BDT 15000.0 Min due: BDT 2000.0 Payment Due Date: 09-FEB-2020",
                sender = "MTB",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Balance-enquiry-only message is rejected",
                message = "Your MTB A/C XXXXX000000 balance is BDT 30,000.00 as of 22-Jan-2024.",
                sender = "MTB",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Failed transaction message is rejected",
                message = "Your MTB card transaction of BDT 500.00 was declined. Please try again.",
                sender = "MTB",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "MTB" to true,
            "MTBL" to true,
            "Mutual Trust Bank" to true,
            "AD-MTBL-S" to true,
            "bKash" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Mutual Trust Bank Parser Tests")
    }

    @TestFactory
    fun `test Mutual Trust Bank statement notice balance update parsing`(): List<DynamicTest> {
        val parser = MutualTrustBankParser()
        val message = "Your bill for card 460041***0000 for JAN 2020 BDT 15000.00 " +
            "Min due: BDT 2000.00 Payment Due Date: 09-FEB-2020"

        return listOf(
            dynamicTest("Statement notice is rejected by parse()") {
                assertNull(parser.parse(message, "MTB", System.currentTimeMillis()))
            },
            dynamicTest("Statement notice is detected as a balance update") {
                assertTrue(parser.isBalanceUpdateNotification(message))
            },
            dynamicTest("Statement notice parses into BalanceUpdateInfo") {
                val info = parser.parseBalanceUpdate(message)
                assertNotNull(info)
                info!!

                assertEquals("Mutual Trust Bank", info.bankName)
                assertEquals("0000", info.accountLast4)
                assertTrue(info.isCreditCard)
                // Total Due is the CC outstanding anchor, mirrored into balance
                assertEquals(BigDecimal("15000.00"), info.balance)
                assertEquals(BigDecimal("15000.00"), info.totalDue)
                assertEquals(BigDecimal("2000.00"), info.minDue)
                assertEquals(LocalDate.of(2020, 2, 9), info.dueDate)
                assertEquals(LocalDate.of(2020, 1, 1), info.statementDate)
                assertNull(info.creditLimit)
            },
            dynamicTest("Non-statement message is not a balance update") {
                assertNull(
                    parser.parseBalanceUpdate(
                        "Dear Customer, Your A/C XXXXX000000 has been Debited by BDT 100.00 on 01/01/20. Available balance BDT 500.00. MTB Hotline 16000"
                    )
                )
            }
        )
    }
}
