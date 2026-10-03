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

import me.shovon.bdparser.CompiledPatterns
import me.shovon.bdparser.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Regressions for four field-misattribution bugs, all caused by a pattern or keyword check
 * claiming text that belongs to a different field:
 *  - the base masked-card heuristic flagging an MFS wallet receipt as a card transaction,
 *  - the Taka currency token matching the tail of a longer word inside a reference,
 *  - the bank account/card mask matching the tail of a longer word inside a merchant name,
 *  - the MFS expense-keyword sweep claiming a refund receipt before "refund" can.
 */
class FieldMisattributionRegressionTest {

    // ------------------------------------------------------------------
    // isFromCard on an MFS wallet receipt
    // ------------------------------------------------------------------

    /**
     * bKash's Mobile Recharge receipt contains "for ending 1234" - the word "ending" plus a
     * 4-digit run, which is exactly what [BankParser.detectIsCard]'s masked-card check looks
     * for - yet names no card: BD MFS money always moves through a mobile wallet.
     */
    @Test
    fun `bKash mobile recharge receipt is not a card transaction`() {
        val message = "Your bKash Mobile Recharge request of Tk 500.00 for ending 1234 was " +
            "successful. Use bKash App for convenience & offers! TCA"

        val parsed = BkashParser().parse(message, "bKash", 0L)

        assertNotNull(parsed)
        assertFalse(parsed!!.isFromCard, "an MFS wallet receipt must never be flagged as a card")
    }

    @Test
    fun `masked-card detection requires an actual mask prefix`() {
        val parser = object : BankParser() {
            override fun getBankName() = "Test"
            override fun canHandle(sender: String) = true
            public override fun detectIsCard(message: String) = super.detectIsCard(message)
        }

        assertFalse(parser.detectIsCard("Purchase ending 1234 on 01-JAN-26"))
        assertTrue(parser.detectIsCard("Purchase on card ending xxxx1234"))
    }

    // ------------------------------------------------------------------
    // Taka currency token vs. a reference that happens to end in "TK"
    // ------------------------------------------------------------------

    /**
     * "TrxID BTK4567XY" contains the letters "TK" followed by digits. Without a left boundary
     * on the currency token this reads as a Taka figure of 4567, so a re-ordered or future MFS
     * format that puts the reference before the amount would report the reference digits as
     * the transaction amount.
     */
    @Test
    fun `Taka amount pattern does not match a currency token inside a longer word`() {
        val message = "Cash Out successful. TrxID BTK4567XY. Balance Tk 500.00"

        val amounts = CompiledPatterns.TakaAmount.GENERIC.findAll(message)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(listOf("500.00"), amounts)
    }

    @Test
    fun `Taka amount pattern still matches every supported currency token spelling`() {
        val parser = BkashParser()
        listOf(
            "Cash In Tk 1,000.00 from 01711111111 successful. TrxID ABC123 at x",
            "Cash In Tk.1,000.00 from 01711111111 successful. TrxID ABC123 at x",
            "Cash In Taka 1,000.00 from 01711111111 successful. TrxID ABC123 at x",
            "Cash In BDT 1,000.00 from 01711111111 successful. TrxID ABC123 at x",
            "Cash In ৳1,000.00 from 01711111111 successful. TrxID ABC123 at x"
        ).forEach { message ->
            assertEquals(
                BigDecimal("1000.00"),
                parser.parse(message, "bKash", 0L)?.amount,
                "failed for: $message"
            )
        }
    }

    // ------------------------------------------------------------------
    // Bank account mask vs. a merchant name ending in "AC"
    // ------------------------------------------------------------------

    /**
     * An EBL card purchase whose merchant name contains a token ending in "AC" followed by
     * digits ("FAC 12345 SHOP"). Without a left boundary on the account pattern that reads as
     * an account number, which both reports the wrong `accountLast4` and - because account
     * wording always beats card wording in
     * [BangladeshBankParser.detectIsCard] - suppresses `isFromCard` on a genuine card alert.
     */
    @Test
    fun `EBL card purchase does not read an account number out of the merchant name`() {
        val message = "EBL CARDS: Purchase txn BDT 663.00 from FAC 12345 SHOP. Card 532900**0000 " +
            "on 01-JAN-20 10:00 BST. Balance: BDT 1234.00."

        val parsed = EasternBankParser().parse(message, "EBL", 0L)

        assertNotNull(parsed)
        assertEquals("0000", parsed!!.accountLast4, "last4 must come from the card, not the merchant name")
        assertTrue(parsed.isFromCard, "a card-only alert must be flagged as a card transaction")
    }

    @Test
    fun `account and card masks still match their real wordings`() {
        val mtb = MutualTrustBankParser()
        val account = "Your A/C 123456**7890 has been Debited by BDT 500.00. Available balance BDT 1000.00"
        val accountParsed = mtb.parse(account, "MTB", 0L)
        assertEquals("7890", accountParsed?.accountLast4)
        assertFalse(accountParsed!!.isFromCard)

        val card = "Successful purchase transaction of BDT 663.00 from SHOP by MTB card- 4600***0001. " +
            "Current Balance BDT 1234.00"
        val cardParsed = mtb.parse(card, "MTB", 0L)
        assertEquals("0001", cardParsed?.accountLast4)
        assertTrue(cardParsed!!.isFromCard)
    }

    // ------------------------------------------------------------------
    // Refund vs. the expense-keyword sweep
    // ------------------------------------------------------------------

    /**
     * A refund receipt names the outgoing action it reverses ("payment"), which the
     * [BangladeshMfsParser] expense sweep would otherwise claim first - booking incoming money
     * as an expense. Same hazard the cashback check already guards against.
     */
    @Test
    fun `refund of a payment is income, not an expense`() {
        val message = "Your payment of Tk 500.00 to SHOP has been refunded. Balance Tk 1,000.00. " +
            "TrxID ABC123 at 01/01/2026"

        val parsed = BkashParser().parse(message, "bKash", 0L)

        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(BigDecimal("500.00"), parsed.amount)
    }

    @Test
    fun `ordinary payment and cash out stay expenses`() {
        val parser = BkashParser()
        listOf(
            "Payment of Tk 380.00 to SHOP NAME is successful. Balance Tk 3,039.39. TrxID ABC at x",
            "Cash Out Tk 1,000.00 to 01711111111 successful. Fee Tk 18.50. Balance Tk 500.00. TrxID ABC at x"
        ).forEach { message ->
            assertEquals(
                TransactionType.EXPENSE,
                parser.parse(message, "bKash", 0L)?.type,
                "failed for: $message"
            )
        }
    }
}
