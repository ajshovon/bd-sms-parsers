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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Coverage for [BankParser.creditCardBalanceIsAvailableCredit]: among the four Bangladeshi bank
 * parsers sharing [BangladeshBankParser] (City Bank, BRAC Bank, Eastern Bank, Mutual Trust
 * Bank), only Eastern Bank and Mutual Trust Bank have verified credit-card transaction alert
 * shapes whose "Current Balance"/"Balance" figure is confirmed to be the card's remaining
 * available credit, not outstanding - so only those two override the flag to `true`. City Bank
 * and BRAC Bank have no credit-card message shapes at all (only account-level "A/C:" forms), so
 * they - like every other parser - keep [BangladeshBankParser]'s safe `false` default, which
 * matches the base [BankParser] default used by existing outstanding-balance-reporting banks
 * (all ~44 Indian parsers plus every other supported country).
 */
class CreditCardBalanceIsAvailableCreditTest {

    @TestFactory
    fun `verified Bangladeshi card issuers report balance as available credit`(): List<DynamicTest> {
        val parsers: List<BankParser> = listOf(
            EasternBankParser(),
            MutualTrustBankParser()
        )
        return parsers.map { parser ->
            dynamicTest("${parser.getBankName()} reports creditCardBalanceIsAvailableCredit = true") {
                assertTrue(parser.creditCardBalanceIsAvailableCredit())
            }
        }
    }

    @TestFactory
    fun `Bangladeshi banks with no card shapes keep the default outstanding-balance semantics`(): List<DynamicTest> {
        val parsers: List<BankParser> = listOf(
            CityBankParser(),
            BracBankParser()
        )
        return parsers.map { parser ->
            dynamicTest("${parser.getBankName()} reports creditCardBalanceIsAvailableCredit = false") {
                assertFalse(parser.creditCardBalanceIsAvailableCredit())
            }
        }
    }
}
