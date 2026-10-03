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

import java.math.BigDecimal

/**
 * Shared base class for Bangladesh full-service BANK SMS parsers - as opposed to the Mobile
 * Financial Services (MFS) wallets covered by [BangladeshMfsParser]: City Bank, BRAC Bank
 * (BBL), Eastern Bank (EBL) and Mutual Trust Bank (MTB).
 *
 * Bank SMS in Bangladesh differ from MFS SMS in ways that justify a separate base rather than
 * reusing [BangladeshMfsParser]:
 *  - They carry a real bank account number (e.g. "A/C: 12345**6789", "AC 12345**6789") and/or
 *    a card number (e.g. "card- 4600***0000", "Card 532900**0000") that must be surfaced as
 *    [me.shovon.bdparser.ParsedTransaction.accountLast4]. [BangladeshMfsParser] deliberately
 *    nulls this field out because an MFS "A/C" denotes a mobile wallet number, not a bank
 *    account - the opposite of what we need here.
 *  - Each bank's amount/balance wording is distinct enough ("Deposit Tk. X ... Balance A/C:",
 *    "Debited by BDT X ... Available balance BDT Y", "is credited with BDT X as ...") that a
 *    shared generic amount extractor would be more error-prone than each subclass supplying
 *    its own small, explicit, label-anchored pattern - the same approach already used by
 *    parsers elsewhere in this library.
 *
 * This base therefore only centralises what is genuinely common to all four banks:
 *  - Currency (always BDT).
 *  - A helper to parse a comma-grouped Taka figure into [BigDecimal].
 *  - Account/card mask -> last4 extraction, and the matching "is this a card transaction"
 *    detection (a message is treated as a card transaction only when it names a card and does
 *    NOT also name a bank account - account wording always wins).
 *  - Reference/transaction-id suppression: none of the four banks' supported SMS formats
 *    carry a distinct reference/txn-id field of their own (unlike MFS TrxID/TxnID). Left to
 *    the base [BankParser.extractReference] implementation, the generic "Txn"/"Transaction"
 *    label pattern would false-positive on ordinary wording like "Purchase txn BDT 663" or
 *    "Successful MOTO transaction of BDT 100.00", capturing the currency token or a stray
 *    word as a bogus reference - so it is nulled out here explicitly.
 */
abstract class BangladeshBankParser : BankParser() {

    override fun getCurrency(): String = "BDT"

    /**
     * Brand/channel words that identify this bank when they appear in the message BODY, e.g.
     * "citytouch", "BBL A/C", "MTB Hotline", "skybanking". Subclasses override this instead of
     * [canHandleMessage]; see that method for how it is combined with [matchesKnownFormat].
     */
    protected open fun matchesBodyMarkers(message: String): Boolean = false

    /**
     * True when the body matches one of THIS parser's own supported SMS formats.
     *
     * Subclasses implement this by delegating to the same pattern set their extractors already
     * use, which makes it a far stronger ownership test than [matchesBodyMarkers]: a brand word
     * is incidental content that a bank may or may not include, whereas a format match means
     * this parser can actually produce a transaction from the message.
     */
    protected open fun matchesKnownFormat(message: String): Boolean = false

    /**
     * Body-based dispatch for when Mobile Number Portability has rewritten the sender ID away
     * from a recognisable sender for this bank (Bangladeshi bank SMS then arrive from a bare
     * mobile number, and two different banks' messages can arrive from the SAME number, so the
     * body is the only discriminator left).
     *
     * Brand markers alone are not sufficient: several supported formats carry no brand token at
     * all - MTB's "Successful MOTO transaction of BDT X ... by card- ... Helpline <shortcode>"
     * card alert names neither "MTB" nor anything else bank-specific, and City Bank's
     * "<date> ATM TXN Tk. X Withdrawal ..." and EBL's "AC <masked> is credited with BDT X as
     * NPSB FUND TRANSFER" are the same. Those messages were undispatchable under MNP even
     * though their parser parses them correctly once reached, so [matchesKnownFormat] is
     * consulted as well.
     *
     * Ties are broken by registry order in [BankParserFactory], where the brand-carrying
     * parser for a given format comes first.
     */
    override fun canHandleMessage(sender: String, message: String): Boolean =
        canHandle(sender) || matchesBodyMarkers(message) || matchesKnownFormat(message)

    /**
     * Safe default for the four Bangladesh full-service banks sharing this base: `false`
     * (balance is NOT assumed to be available credit). Only Eastern Bank (EBL) and Mutual
     * Trust Bank (MTB) have verified card-transaction alert shapes ("Current Balance BDT Y",
     * "Balance: BDT Y", "Available balance BDT Y" on a card-tagged purchase/withdrawal message)
     * confirmed to report the card's remaining AVAILABLE CREDIT - that figure decreasing as
     * purchases are made and increasing on a payment/refund credited to the card. Those two
     * parsers override this to `true`. City Bank and BRAC Bank (BBL) have no credit-card
     * message shapes at all (only account-level "A/C:" forms), so the blanket-true default
     * this base used to return would misread a genuine account balance as available credit if
     * a row from either bank were ever flagged as a credit card; `false` is the safe choice
     * for them. Statement/monthly-bill notices are a separate code path
     * ([BankParser.isBalanceUpdateNotification]/[BankParser.parseBalanceUpdate]) that already
     * reports true outstanding (Total Due) directly and is unaffected by this flag.
     */
    override fun creditCardBalanceIsAvailableCredit(): Boolean = false

    protected fun parseTakaAmount(rawAmount: String): BigDecimal? {
        val cleaned = rawAmount.replace(",", "")
        return try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * "A/C: 12345**6789", "AC 12345**6789", "Account XXXXX000000".
     *
     * The leading `(?<![A-Za-z])` is required: without it the unanchored "AC" alternative also
     * matches the tail of an unrelated word, so a merchant name like "FAC 12345 SHOP" in an
     * "EBL CARDS: Purchase txn ..." alert reads as an account number - yielding the wrong
     * [me.shovon.bdparser.ParsedTransaction.accountLast4] AND suppressing [detectIsCard],
     * because account wording always wins there.
     */
    protected open val bankAccountMaskPattern: Regex = Regex(
        """(?<![A-Za-z])(?:A/?C|Account)\s*:?\s*([0-9Xx*]{4,})""",
        RegexOption.IGNORE_CASE
    )

    /** "card- 4600***0000", "Card 532900**0000", "MTB card- 4600***0000" */
    protected open val bankCardMaskPattern: Regex = Regex(
        """(?<![A-Za-z])card[\s-]*:?\s*([0-9Xx*]{4,})""",
        RegexOption.IGNORE_CASE
    )

    override fun extractAccountLast4(message: String): String? {
        bankAccountMaskPattern.find(message)?.let { match ->
            extractLast4Digits(match.groupValues[1])?.let { return it }
        }
        bankCardMaskPattern.find(message)?.let { match ->
            extractLast4Digits(match.groupValues[1])?.let { return it }
        }
        return null
    }

    override fun detectIsCard(message: String): Boolean {
        val hasAccount = bankAccountMaskPattern.containsMatchIn(message)
        val hasCard = bankCardMaskPattern.containsMatchIn(message)
        return hasCard && !hasAccount
    }

    // None of City Bank / BBL / EBL / MTB's supported SMS formats carry their own
    // reference/txn-id field - see class doc for why this must not fall back to the
    // generic (Indian-oriented) Ref/Txn/Transaction label pattern.
    override fun extractReference(message: String): String? = null

    /**
     * Converts a 3-letter month abbreviation (e.g. "AUG", "Jul") to its 1-12 number.
     * Shared by subclasses that parse billing-cycle/statement dates such as "AUG2026"
     * or "JUL 2026". Returns null for anything that isn't a recognised abbreviation.
     */
    protected fun parseMonthAbbreviation(monthAbbr: String): Int? {
        return when (monthAbbr.trim().take(3).uppercase()) {
            "JAN" -> 1
            "FEB" -> 2
            "MAR" -> 3
            "APR" -> 4
            "MAY" -> 5
            "JUN" -> 6
            "JUL" -> 7
            "AUG" -> 8
            "SEP" -> 9
            "OCT" -> 10
            "NOV" -> 11
            "DEC" -> 12
            else -> null
        }
    }

    /**
     * Resolves a possibly-2-digit year to a full 4-digit year, assuming the 2000s.
     * Bangladeshi bank SMS commonly use 2-digit years (e.g. "26" for 2026).
     */
    protected fun resolveYear(rawYear: String): Int? {
        val trimmed = rawYear.trim()
        return when (trimmed.length) {
            2 -> trimmed.toIntOrNull()?.plus(2000)
            4 -> trimmed.toIntOrNull()
            else -> null
        }
    }

    /**
     * EMI/instalment keyword check shared by [BankParser.extractEmiInfo] implementations:
     * matches "EMI", "installment" or "instalment" as a whole word, case-insensitively.
     */
    private val emiKeywordPattern = Regex("""\b(?:emi|instal{1,2}ment)\b""", RegexOption.IGNORE_CASE)

    /** "3 of 12", "installment 3/12", "3rd of 12 EMIs" - captures (current, total). */
    private val emiInstallmentCountPattern = Regex(
        """(\d{1,3})(?:st|nd|rd|th)?\s*(?:of|/)\s*(\d{1,3})""",
        RegexOption.IGNORE_CASE
    )

    /** "EMI amount BDT 1,000.00", "monthly EMI of Tk. 1000", "EMI: BDT 1000" */
    private val emiMonthlyAmountPattern = Regex(
        """(?:emi|instal{1,2}ment)(?:\s+amount)?\s*(?:of|is|:)?\s*(?:BDT|Tk\.?|Rs\.?|INR|₹)\s*([0-9][0-9,]*(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    /** "BDT 1,000.00 per month", "BDT 1000/month", "BDT 1000 monthly" */
    private val emiPerMonthAmountPattern = Regex(
        """(?:BDT|Tk\.?|Rs\.?|INR|₹)\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:per\s*month|/\s*month|monthly)""",
        RegexOption.IGNORE_CASE
    )

    /** "tenure of 12 months", "for 12 months", "over 12 months" */
    private val emiTenurePattern = Regex(
        """(?:tenure\s*(?:of)?|for|over)\s*(\d{1,3})\s*months?""",
        RegexOption.IGNORE_CASE
    )

    /** "purchase of BDT 5,000.00 ... converted to EMI", "converted to EMI of BDT 5,000.00" */
    private val emiPrincipalPattern = Regex(
        """(?:purchase\s+of\s+(?:BDT|Tk\.?|Rs\.?|INR|₹)\s*([0-9][0-9,]*(?:\.\d{1,2})?))""" +
            """|(?:(?:BDT|Tk\.?|Rs\.?|INR|₹)\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s+(?:has been\s+)?converted\s+(?:to|into)\s+emi)""",
        RegexOption.IGNORE_CASE
    )

    /** "12.5% p.a.", "15% interest rate", "annual interest of 15%" */
    private val emiInterestRatePattern = Regex(
        """(?:([0-9]{1,2}(?:\.[0-9]{1,2})?)\s*%\s*(?:p\.?a\.?|interest|annual)|(?:interest|annual)\s*(?:rate)?\s*(?:of)?\s*([0-9]{1,2}(?:\.[0-9]{1,2})?)\s*%)""",
        RegexOption.IGNORE_CASE
    )

    /** "purchase of BDT X at MERCHANT NAME has been converted", "purchase at MERCHANT has been converted" */
    private val emiDescriptionPattern = Regex(
        """(?:purchase|transaction)(?:\s+of\s+(?:BDT|Tk\.?|Rs\.?|INR|₹)\s*[0-9][0-9,]*(?:\.\d{1,2})?)?\s+at\s+(.+?)\s+(?:has been|is|was)\s+converted""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Shared, conservative EMI/instalment detector for Bangladeshi bank SMS. Only recognises
     * messages that both (a) contain an EMI/installment keyword and (b) carry at least one
     * concrete EMI figure (an "N of M" installment count, a monthly amount, a tenure in
     * months, a principal-converted-to-EMI amount, or an interest rate) - plain mentions of
     * "EMI" (e.g. a promotional "convert your purchase to EMI!" message with no figures) are
     * deliberately rejected to avoid false positives, per [BankParser.extractEmiInfo]'s contract.
     *
     * NOTE: implemented against generic South-Asian EMI wording; no real Bangladeshi EMI SMS
     * samples were available to verify against at the time this was written.
     */
    override fun extractEmiInfo(message: String): BankParser.EmiInfo? {
        if (!emiKeywordPattern.containsMatchIn(message)) return null

        val installmentMatch = emiInstallmentCountPattern.find(message)
        val installmentNumber = installmentMatch?.groupValues?.get(1)?.toIntOrNull()
        val totalInstallments = installmentMatch?.groupValues?.get(2)?.toIntOrNull()

        val monthlyAmount = (emiMonthlyAmountPattern.find(message) ?: emiPerMonthAmountPattern.find(message))
            ?.groupValues?.get(1)?.let { parseTakaAmount(it) }

        val tenureMonths = emiTenurePattern.find(message)?.groupValues?.get(1)?.toIntOrNull()

        val principal = emiPrincipalPattern.find(message)?.let { match ->
            match.groupValues[1].ifEmpty { match.groupValues[2] }.let { parseTakaAmount(it) }
        }

        val interestRate = emiInterestRatePattern.find(message)?.let { match ->
            match.groupValues[1].ifEmpty { match.groupValues[2] }.toDoubleOrNull()
        }

        val hasConcreteFigure = listOfNotNull(
            monthlyAmount, tenureMonths, principal, interestRate, totalInstallments
        ).isNotEmpty()
        if (!hasConcreteFigure) return null

        val description = emiDescriptionPattern.find(message)?.groupValues?.get(1)?.trim()

        return BankParser.EmiInfo(
            description = description,
            monthlyAmount = monthlyAmount,
            tenureMonths = tenureMonths,
            principal = principal,
            installmentNumber = installmentNumber,
            totalInstallments = totalInstallments,
            interestRate = interestRate
        )
    }
}
