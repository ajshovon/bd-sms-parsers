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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

/**
 * Mobile Number Portability dispatch for formats that carry NO brand token.
 *
 * [BankBodyDetectionTest] covers the brand-marker half of body dispatch, using one
 * representative body per parser - and every one of those bodies happens to name its bank. The
 * formats below do not: MTB's card alerts trail only a bare "Helpline <shortcode>", City Bank's
 * ATM/POS alerts only a channel label, and EBL's NPSB credit only "AC <masked>". Those were
 * undispatchable from a ported (bare mobile number) sender even though their parser parses them
 * correctly once reached, which is why [BangladeshBankParser.matchesKnownFormat] consults each
 * parser's own format patterns as well as its brand words.
 *
 * All bodies are redacted placeholders in the style of the parsers' own test files - no real
 * message content, card digits, balances or merchant names.
 */
class MnpBodyDispatchTest {

    /** A ported number: no parser recognises it, so dispatch must come from the body alone. */
    private val portedSender = "01700000000"

    /**
     * MTB's MOTO card alert - the shape that first exposed this gap. Account-level MTB alerts
     * worked under MNP because they trail "MTB Hotline"; this one names no bank at all, only a
     * bare helpline shortcode.
     */
    private val mtbMotoCardBody =
        "Successful MOTO transaction of BDT 415.00 from Sample Delivery Service by card- " +
            "4600***0000 on 03-Jan-24 at 07:56 pm. Current Balance BDT 80,000.00. Helpline 16000"

    @Test
    fun `MTB MOTO card alert dispatches from a ported sender and parses`() {
        val parser = BankParserFactory.getParser(portedSender, mtbMotoCardBody)

        assertEquals("Mutual Trust Bank", parser?.getBankName())

        val parsed = parser!!.parse(mtbMotoCardBody, portedSender, 0L)
        assertNotNull(parsed)
        assertEquals(BigDecimal("415.00"), parsed!!.amount)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(BigDecimal("80000.00"), parsed.balance)
        assertEquals("0000", parsed.accountLast4)
        assertEquals("Sample Delivery Service", parsed.merchant)
        assertTrue(parsed.isFromCard)
    }

    @TestFactory
    fun `brand-less formats dispatch to their own bank from a ported sender`(): List<DynamicTest> {
        val cases = listOf(
            Triple(
                "MTB MOTO card", "Mutual Trust Bank", mtbMotoCardBody
            ),
            Triple(
                "MTB payment credited to card", "Mutual Trust Bank",
                "Payment of BDT 15000.00 credited to Card- 460041***0000 on 05-Jan-24 at 05:00 PM. Helpline 16000"
            ),
            Triple(
                "MTB generic account debit", "Mutual Trust Bank",
                "Your account has been debited by BDT 2,250.00 on 06-Jan-24 at 12:00 am. " +
                    "Current balance BDT 60,000.00. Helpline 16000"
            ),
            Triple(
                "MTB ATM withdrawal", "Mutual Trust Bank",
                "Withdrawal of BDT 8000 from Sample Branch ATM using card-460000***0000 on " +
                    "07-Jan-24 at 08:00 pm. Current Balance BDT 40,000.00. Helpline 16000"
            ),
            Triple(
                "City Bank ATM withdrawal", "City Bank",
                "03-Jan-24 ATM TXN Tk. 1,200.00 Withdrawal Tk. 30,000.00 Balance A/C: 98765**4321"
            ),
            Triple(
                "EBL NPSB credit", "Eastern Bank",
                "AC 12345**6789 is credited with BDT 5,000.00 as NPSB FUND TRANSFER on 01-Jan-24 10:00 BST"
            )
        )

        return cases.map { (label, expectedBank, body) ->
            dynamicTest("$label dispatches to $expectedBank and parses") {
                val parser = BankParserFactory.getParser(portedSender, body)
                assertEquals(expectedBank, parser?.getBankName(), "wrong bank for $label")
                assertNotNull(parser!!.parse(body, portedSender, 0L), "$label dispatched but did not parse")
            }
        }
    }

    /**
     * The credit-card statement notice is gated by the same [BankParser.canHandleMessage] call
     * before [BankParser.isBalanceUpdateNotification] is ever consulted, so it needs the same
     * format-based fallback - it names no bank either.
     */
    @TestFactory
    fun `card statement notices dispatch from a ported sender`(): List<DynamicTest> {
        val cases = listOf(
            Triple(
                "MTB", "Mutual Trust Bank",
                "Your bill for card 460041***0000 for JAN 2020 BDT 18500.4 Min due: BDT 2100.0 " +
                    "Payment Due Date: 09-FEB-2020"
            ),
            Triple(
                "EBL", "Eastern Bank",
                "Monthly bill 532900******0000 JAN2020; Total Due: BDT 10000.00, Min Due: BDT 1000.00, " +
                    "Last Pmt: 01-JAN-20. Statement link https://example.com/statement"
            )
        )

        return cases.map { (label, expectedBank, body) ->
            dynamicTest("$label statement dispatches to $expectedBank and yields a balance anchor") {
                val parser = BankParserFactory.getParser(portedSender, body)
                assertEquals(expectedBank, parser?.getBankName(), "wrong bank for $label statement")
                assertTrue(parser!!.isBalanceUpdateNotification(body))
                assertNotNull(parser.parseBalanceUpdate(body))
            }
        }
    }

    /**
     * MTB and EBL can arrive from the SAME ported number, so the format fallback must not let
     * either claim the other's messages. The near-miss worth pinning is the payment-credited
     * shape, which both banks have: MTB's requires "Card-" and EBL's requires "EBL CARDS:" with
     * "Card <digits>".
     */
    @TestFactory
    fun `MTB and EBL do not claim each other's formats`(): List<DynamicTest> {
        val mtbBodies = listOf(
            mtbMotoCardBody,
            "Payment of BDT 15000.00 credited to Card- 460041***0000 on 05-Jan-24 at 05:00 PM. Helpline 16000",
            "Your bill for card 460041***0000 for JAN 2020 BDT 18500.4 Min due: BDT 2100.0 " +
                "Payment Due Date: 09-FEB-2020"
        )
        val eblBodies = listOf(
            "AC 12345**6789 is credited with BDT 5,000.00 as NPSB FUND TRANSFER on 01-Jan-24 10:00 BST",
            "EBL CARDS: Payment of BDT 12,500.00 credited to Card 532900**0000 on 05-Jan-24 10:30 AM. " +
                "Balance: BDT 50000.00. Thank You. EBL Helpline 16001",
            "Monthly bill 532900******0000 JAN2020; Total Due: BDT 10000.00, Min Due: BDT 1000.00, " +
                "Last Pmt: 01-JAN-20. Statement link https://example.com/statement"
        )

        val tests = mutableListOf<DynamicTest>()
        mtbBodies.forEachIndexed { i, body ->
            tests.add(
                dynamicTest("EBL does not claim MTB body #$i") {
                    org.junit.jupiter.api.Assertions.assertFalse(
                        EasternBankParser().canHandleMessage(portedSender, body)
                    )
                }
            )
        }
        eblBodies.forEachIndexed { i, body ->
            tests.add(
                dynamicTest("MTB does not claim EBL body #$i") {
                    org.junit.jupiter.api.Assertions.assertFalse(
                        MutualTrustBankParser().canHandleMessage(portedSender, body)
                    )
                }
            )
        }
        return tests
    }
}
