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
import java.math.BigDecimal

/**
 * Shared base class for Bangladesh Mobile Financial Services (MFS) SMS parsers:
 * bKash, Nagad, Rocket (DBBL), Upay, Tap.
 *
 * All BD MFS providers share the same SMS shape - a Taka ("Tk"/"Taka"/"BDT"/"৳") amount,
 * followed (in the same message) by an optional "Fee Tk X" and a "Balance Tk Y" figure,
 * plus a TrxID/TxnID reference. This base class centralises:
 *  - Currency: always BDT.
 *  - Primary amount extraction that explicitly SKIPS fee/balance/commission/charge figures
 *    (see [extractAmount] - this is the main correctness hazard for BD MFS SMS).
 *  - Balance extraction ("Balance Tk ...").
 *  - A protected fee helper ([extractFee]) and reference helper ([extractMfsReference]) for
 *    TrxID/TxnID/TxnId.
 *  - Transaction type + transaction-message detection tuned to BD MFS wording
 *    (Cash In/Cash Out/Send Money/Payment/received, and rejection of OTP, promo, and
 *    failed/unsuccessful messages).
 *
 * Concrete parsers (BkashParser, NagadParser, RocketParser, UpayParser, TapParser) only need
 * to implement [getBankName] and declare [senderTokens]; [canHandle] is provided by this base
 * class via [matchesSenderTokens]. Merchant/reference extraction is only overridden where a
 * provider's format genuinely differs.
 */
abstract class BangladeshMfsParser : BankParser() {

    override fun getCurrency(): String = "BDT"

    // BD MFS "A/C" denotes the customer's mobile wallet number, not a bank account -
    // must not populate the bank-account field with a phone number.
    override fun extractAccountLast4(message: String): String? = null

    // ------------------------------------------------------------------
    // Sender matching
    // ------------------------------------------------------------------

    /**
     * The sender identifiers this provider is known by (brand words and/or numeric shortcodes),
     * e.g. `listOf("BKASH", "16247")`. Declared by each concrete parser and consumed by the
     * default [canHandle] implementation via [matchesSenderTokens].
     */
    protected abstract val senderTokens: List<String>

    override fun canHandle(sender: String): Boolean = matchesSenderTokens(sender, senderTokens)

    companion object {

        /** Real-world sender IDs are frequently wrapped in a DLT operator prefix/suffix, e.g.
         * "AD-BKASH", "BKASH-BD", "VM-NAGAD-S". Tokens this short and generic risk colliding
         * with unrelated words that merely happen to contain them as a substring (e.g. "TAP"
         * inside "STAPLES" or "TAPZO"). Tokens at or below this length are matched using
         * [segmentEqualsToken] instead of raw bidirectional containment; longer, more
         * distinctive tokens (e.g. "BKASH", "NAGAD", "ROCKET") are safe with plain
         * bidirectional containment. */
        private const val SHORT_TOKEN_MAX_LENGTH = 4

        /** Keeps only letters/digits and uppercases, so DLT prefixes, punctuation, spacing and
         * casing never prevent a match: "AD-CITYBANK", "ad citybank", "CITYBANK" all normalise
         * to "CITYBANK". */
        private fun normalizeToken(raw: String): String = raw.filter { it.isLetterOrDigit() }.uppercase()

        /**
         * True when [normalizedToken] appears as a whole, separator-delimited segment of
         * [rawSender] once split on any run of non-alphanumeric characters. This is the safe
         * variant used for short/generic tokens: "AD-TAP", "TAP-BD" and "BD-TAP-01" all yield a
         * "TAP" segment and match, while "TAPZO" and "WHATSAPP" yield "TAPZO"/"WHATSAPP" as a
         * single segment each - neither equals "TAP" - and correctly do NOT match.
         */
        private fun segmentEqualsToken(rawSender: String, normalizedToken: String): Boolean {
            val segments = rawSender.uppercase().split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }
            return segments.any { it == normalizedToken }
        }

        /**
         * Bidirectional containment sender match shared by all BD MFS parsers.
         *
         * Both [sender] and every entry in [tokens] are normalised via [normalizeToken] (strip
         * non-alphanumerics, uppercase), then matched with `normalisedSender.contains(token) ||
         * token.contains(normalisedSender)` - EXCEPT for tokens at or below
         * [SHORT_TOKEN_MAX_LENGTH], which use the stricter [segmentEqualsToken] check to avoid
         * false positives on longer, unrelated sender words.
         */
        fun matchesSenderTokens(sender: String, tokens: List<String>): Boolean {
            val normalizedSender = normalizeToken(sender)
            if (normalizedSender.isEmpty()) return false

            return tokens.any { rawToken ->
                val normalizedToken = normalizeToken(rawToken)
                if (normalizedToken.isEmpty()) {
                    false
                } else if (normalizedToken.length <= SHORT_TOKEN_MAX_LENGTH) {
                    segmentEqualsToken(sender, normalizedToken)
                } else {
                    normalizedSender.contains(normalizedToken) || normalizedToken.contains(normalizedSender)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Amount (the critical fee/balance exclusion hazard lives here)
    // ------------------------------------------------------------------

    /**
     * Extracts the primary transaction amount from a BD MFS SMS.
     *
     * BD MFS messages frequently contain MULTIPLE Taka figures in one SMS: the transaction
     * amount, a "Fee Tk X", and a "Balance Tk Y" (sometimes also "Commission"/"Charge").
     * A naive first-match extractor would be correct in the common case (the amount is
     * mentioned first), but to be robust against re-ordered formats, this walks every Taka
     * match in the message and explicitly skips any whose immediately preceding text looks
     * like a fee/balance/commission/charge label.
     */
    override fun extractAmount(message: String): BigDecimal? {
        // Priority 1: explicit "Amount: Tk X" label (Nagad/Upay style) - unambiguous.
        CompiledPatterns.TakaAmount.AMOUNT_LABEL.find(message)?.let { match ->
            return parseTakaAmount(match.groupValues[1])
        }

        // Priority 2: first generic Taka figure whose preceding context is NOT a
        // fee/balance/commission/charge label.
        for (match in CompiledPatterns.TakaAmount.GENERIC.findAll(message)) {
            val contextStart = maxOf(0, match.range.first - 25)
            val precedingContext = message.substring(contextStart, match.range.first)

            if (CompiledPatterns.TakaContext.EXCLUDE_BEFORE_AMOUNT.containsMatchIn(precedingContext)) {
                continue
            }

            return parseTakaAmount(match.groupValues[1])
        }

        return null
    }

    /**
     * Extracts the post-transaction balance, e.g. "Balance Tk 1,500.00" / "Balance: Tk1,234.56".
     */
    override fun extractBalance(message: String): BigDecimal? {
        CompiledPatterns.TakaBalance.BALANCE_LABEL.find(message)?.let { match ->
            return parseTakaAmount(match.groupValues[1])
        }
        return null
    }

    /**
     * Extracts the transaction fee, e.g. "Fee Tk 18.50" / "Fee: Tk0.00".
     * Not currently surfaced on [me.shovon.bdparser.ParsedTransaction], but kept as a
     * shared helper for subclasses/future use and to make the fee-exclusion behaviour testable.
     */
    protected fun extractFee(message: String): BigDecimal? {
        CompiledPatterns.TakaFee.FEE_LABEL.find(message)?.let { match ->
            return parseTakaAmount(match.groupValues[1])
        }
        return null
    }

    protected fun parseTakaAmount(rawAmount: String): BigDecimal? {
        val cleaned = rawAmount.replace(",", "")
        return try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Reference (TrxID / TxnID / TxnId)
    // ------------------------------------------------------------------

    /**
     * Extracts the TrxID/TxnID/TxnId reference shared by all BD MFS providers.
     */
    protected fun extractMfsReference(message: String): String? {
        CompiledPatterns.TakaReference.TRX_TXN_ID.find(message)?.let { match ->
            return match.groupValues[1].trim()
        }
        return null
    }

    override fun extractReference(message: String): String? {
        extractMfsReference(message)?.let { return it }
        return super.extractReference(message)
    }

    // ------------------------------------------------------------------
    // Transaction type / message filtering
    // ------------------------------------------------------------------

    private val expenseKeywords = listOf(
        "cash out", "send money", "payment", "paid", "debited",
        "recharge", "withdraw", "bill pay"
    )

    private val incomeKeywords = listOf(
        "received", "cash in", "money received", "credited", "add money", "refund"
    )

    /**
     * "You have received Cashback Tk 10.00 ... Cashback for Send Money!" - a received-cashback
     * message often carries a trailing "Cashback for <original action>!" clause (e.g. "Send
     * Money", "Payment") that would otherwise be misread as an outgoing expense by
     * [expenseKeywords] (e.g. "send money"). Checked with top priority so a cashback credit is
     * never reclassified as the expense it was a cashback FOR.
     */
    protected val cashbackReceivedPattern = Regex(
        """received\s+Cashback""",
        RegexOption.IGNORE_CASE
    )

    override fun extractTransactionType(message: String): TransactionType? {
        if (cashbackReceivedPattern.containsMatchIn(message)) return TransactionType.INCOME

        val lowerMessage = message.lowercase()

        val isExpense = expenseKeywords.any { lowerMessage.contains(it) }
        if (isExpense) return TransactionType.EXPENSE

        val isIncome = incomeKeywords.any { lowerMessage.contains(it) }
        if (isIncome) return TransactionType.INCOME

        return null
    }

    private val otpKeywords = listOf(
        "otp", "one time password", "verification code", "reset your pin",
        "security code", "pin reset", "your pin is"
    )

    private val promoKeywords = listOf(
        "discount", "cashback offer", "bonus offer", "campaign", "coupon"
    )

    /**
     * "offer"/"win" as whole words only - NOT plain substrings. Several legitimate transaction
     * confirmations carry a fixed promotional-sounding APP FOOTER that is not itself an offer
     * being advertised, e.g. bKash's Mobile Recharge receipt ends with "Use bKash App for
     * convenience & offers!". A substring check on "offer" would match "offers" in that footer
     * and wrongly reject a real, successful transaction. Word-boundary matching still rejects
     * genuine promotional copy ("... cashback offer this Eid!", "Win exciting prizes ...").
     */
    private val promoWordPattern = Regex("""\b(?:offer|win)\b""", RegexOption.IGNORE_CASE)

    private val failureKeywords = listOf(
        "unsuccessful", "failed", "declined", "not successful",
        "insufficient balance", "please try again", "transaction failed"
    )

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        if (otpKeywords.any { lowerMessage.contains(it) }) return false
        if (promoKeywords.any { lowerMessage.contains(it) }) return false
        if (promoWordPattern.containsMatchIn(message)) return false
        if (failureKeywords.any { lowerMessage.contains(it) }) return false

        return expenseKeywords.any { lowerMessage.contains(it) } ||
                incomeKeywords.any { lowerMessage.contains(it) }
    }

    // ------------------------------------------------------------------
    // Merchant
    // ------------------------------------------------------------------

    /** "Merchant: SHOP NAME" (Nagad payment style) */
    private val merchantLabelPattern = Regex(
        """Merchant:\s*([^,\n]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "Bill successfully paid.\nBiller: Demo Utility \nMMYYYY/Contact: ..." (multiline bill-pay
     * receipt shape). Trailing spaces after the biller name are trimmed by the caller.
     */
    private val billerLabelPattern = Regex(
        """Biller:\s*([^\n]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "Payment Tk 350.00 to SHOP NAME successful" / "Payment of Tk 380.00 to SHOP NAME is
     * successful" - only matches when the target is not a phone number. The optional "is"
     * accommodates the "... is successful" wording seen in real bKash merchant-payment SMS.
     */
    private val toSuccessfulPattern = Regex(
        """to\s+([A-Za-z][^\n]*?)\s+(?:is\s+)?successful""",
        RegexOption.IGNORE_CASE
    )

    override fun extractMerchant(message: String, sender: String): String? {
        billerLabelPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        merchantLabelPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        toSuccessfulPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        return null
    }
}
