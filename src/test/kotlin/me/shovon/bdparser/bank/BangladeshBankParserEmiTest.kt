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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

/**
 * Tests for [BankParser.extractEmiInfo], shared by all Bangladeshi bank parsers via
 * [BangladeshBankParser]. Exercised through [MutualTrustBankParser] as a representative
 * concrete subclass since the detection logic itself lives in the shared base.
 *
 * NOTE: implemented against generic South-Asian EMI wording - no real Bangladeshi EMI SMS
 * samples were available to verify against, so these cases are illustrative, not verbatim
 * real-world messages.
 */
class BangladeshBankParserEmiTest {

    @TestFactory
    fun `test shared EMI detection for Bangladeshi bank parsers`(): List<DynamicTest> {
        val parser = MutualTrustBankParser()

        return listOf(
            dynamicTest("EMI installment N of M with monthly amount and tenure is detected") {
                val message = "Your purchase has been converted to EMI. Installment 3 of 12, " +
                    "EMI amount BDT 1,000.00, tenure of 12 months. Helpline 16000"
                val info = parser.extractEmiInfo(message)
                assertNotNull(info)
                info!!
                assertEquals(3, info.installmentNumber)
                assertEquals(12, info.totalInstallments)
                assertEquals(BigDecimal("1000.00"), info.monthlyAmount)
                assertEquals(12, info.tenureMonths)
            },
            dynamicTest("Purchase converted to EMI with principal and interest rate is detected") {
                val message = "Your purchase of BDT 5,000.00 at Sample Store has been converted " +
                    "to EMI for 6 months at 12.5% p.a. interest."
                val info = parser.extractEmiInfo(message)
                assertNotNull(info)
                info!!
                assertEquals("Sample Store", info.description)
                assertEquals(BigDecimal("5000.00"), info.principal)
                assertEquals(6, info.tenureMonths)
                assertEquals(12.5, info.interestRate)
            },
            dynamicTest("Monthly EMI-per-month wording is detected") {
                val message = "Reminder: your EMI of BDT 750.00 per month is due on 05-AUG-2020."
                val info = parser.extractEmiInfo(message)
                assertNotNull(info)
                info!!
                assertEquals(BigDecimal("750.00"), info.monthlyAmount)
            },
            dynamicTest("Plain EMI mention without any figures is rejected") {
                val message = "Convert your next purchase to EMI and enjoy easy payments! Ask us how."
                assertNull(parser.extractEmiInfo(message))
            },
            dynamicTest("Ordinary card purchase without EMI wording is rejected") {
                val message = "Successful purchase transaction of BDT 250.00 from  Sample Store Limited " +
                    "by MTB card- 4600***0000 on 08-Jan-20 at 02:13 pm. Current balance BDT 80,000.00. Helpline 16000"
                assertNull(parser.extractEmiInfo(message))
            },
            dynamicTest("Ordinary account debit without EMI wording is rejected") {
                val message = "Dear Customer, Your A/C XXXXX000000 has been Debited by BDT 15,000.00 " +
                    "on 20/01/20. Available balance BDT 1,00,000.00. MTB Hotline 16000"
                assertNull(parser.extractEmiInfo(message))
            }
        )
    }
}
