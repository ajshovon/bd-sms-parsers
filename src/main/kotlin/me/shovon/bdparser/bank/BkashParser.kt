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

/**
 * Parser for bKash (Bangladesh) mobile financial service SMS messages.
 *
 * Supported formats:
 * - "Cash In Tk 1,000.00 from 01XXXXXXXXX successful. Fee Tk 0.00. Balance Tk 1,500.00. TrxID ... at ..."
 * - "Cash Out Tk 1,000.00 to 01XXXXXXXXX successful. Fee Tk 18.50. Balance Tk 500.00. TrxID ... at ..."
 * - "You have received Tk 500.00 from 01XXXXXXXXX. Ref 123. Fee Tk 0.00. Balance Tk 1,000.00. TrxID ... at ..."
 *   (the "Ref <text>." clause is optional)
 * - "Send Money Tk 200.00 to 01XXXXXXXXX successful. Fee Tk 5.00. Balance Tk 800.00. TrxID ... at ..."
 * - "Payment of Tk 380.00 to SHOP NAME-1-RM12345 is successful. Balance Tk 3,039.39. TrxID ... at ..."
 *   (merchant-payment-ID suffix "-<digits>-RM<digits>"/"-RM<digits>" is stripped from the merchant name)
 * - "Your mobile recharge Tk 50.00 to 01XXXXXXXXX successful. Balance Tk 600.00. TrxID ..."
 * - Multiline bill payment receipt:
 *     "Bill successfully paid.\nBiller: SHOP \nMMYYYY/Contact: ...\nA/C: ... \nAmount: Tk 500.00 \n
 *      Fee: Tk 0.00 \nTrxID: ... at ..."
 * - "Congratulations! You have received Cashback Tk 10.00. Balance Tk 665.86. TrxID ... at ...
 *    Cashback for Send Money!" (the trailing "Cashback for <action>!" clause must not be
 *    misread as an outgoing Send Money)
 * - "You have received deposit from iBanking of Tk 500.00 from <BANK>. Fee Tk 0.00. Balance
 *    Tk 675.86. TrxID ..." (bank deposit via iBanking; source bank surfaced as merchant)
 * - "Congratulations! DPS Payment of Tk 500.00 to SHOP is successful. Balance Tk 3,000.00.
 *    TrxID ... at ..." (DPS instalment payment; reuses the shared "to X ... successful"
 *    merchant pattern, "payment" keyword drives EXPENSE classification)
 * - "Your Digital Loan Repayment of Tk 1,500.00 is successful. Balance Tk 2,000.00. TrxID ...
 *    at ..." (no "to X" clause, so the merchant/description is surfaced as "Digital Loan
 *    Repayment"; "repayment" containing "payment" drives EXPENSE classification)
 * - "Your bKash Mobile Recharge request of Tk 500.00 for ending 1234 was successful. Use
 *    bKash App for convenience & offers! TCA" (no Fee/Balance/TrxID trailer at all; the
 *    trailing "... offers!" app footer must NOT be misread as promotional content)
 * - Multiline bill payment receipt, "Contact"-line variant:
 *     "Bill successfully paid.\nBiller: SHOP \nMM/YYYY: ...\nAccount ... \nAmount: Tk 500.00 \n
 *      Fee: Tk 0.00 \nTrxID: ... at ..." (second real-world label pairing - "MM/YYYY:"/
 *      "Account" instead of "MMYYYY/Contact:"/"A/C:" - already covered by the same
 *      Biller/Amount/Fee/TrxID label patterns as the primary bill-pay shape)
 *
 * Ignored (return null): OTP/verification-code messages, PIN reset prompts, promotional/
 * discount-coupon messages, Account Binding request/cancellation notices, and
 * failed/unsuccessful transactions.
 *
 * Common senders: bKash, 16247
 * Currency: BDT (Bangladeshi Taka)
 */
class BkashParser : BangladeshMfsParser() {

    override fun getBankName() = "bKash"

    override val senderTokens = listOf("BKASH", "16247")

    /**
     * Body-marker fallback for when Mobile Number Portability has rewritten the sender ID away
     * from a recognisable bKash sender: matches when the body mentions "bKash" by name, or
     * carries both a "TrxID" reference and a Taka amount token - the two hallmarks of a bKash
     * transaction receipt.
     */
    override fun canHandleMessage(sender: String, message: String): Boolean {
        if (canHandle(sender)) return true
        val lower = message.lowercase()
        if (lower.contains("bkash")) return true
        val hasTrxId = lower.contains("trxid")
        val hasTaka = takaTokenPattern.containsMatchIn(message) || message.contains("৳")
        return hasTrxId && hasTaka
    }

    /** Compiled once; [canHandleMessage] runs for every parser against every unmatched SMS. */
    private val takaTokenPattern = Regex("""\b(tk|taka)\b""", RegexOption.IGNORE_CASE)

    // ------------------------------------------------------------------
    // Merchant-payment-ID suffix stripping ("-1-RM10001" / "-RM10002")
    // ------------------------------------------------------------------

    /**
     * bKash merchant-payment receipts append a payment-ID suffix to the merchant name, e.g.
     * "Example Store Limited-1-RM10001" or "Sample Shop Limited-RM10002". Strip it so the merchant
     * reads "Example Store Limited" / "Sample Shop Limited".
     *
     * Applied AFTER the base [cleanMerchantName] chain (via override, called on the already
     * base-cleaned string) so that the shared LTD/PVT_LTD trailing-word stripping - which only
     * matches when "LTD"/"LIMITED" is the very last token - never fires on names like
     * "... Limited-1-RM10001" (which does NOT end in "Limited") and "Limited" is preserved once
     * the numeric suffix is removed.
     */
    private val merchantPaymentIdSuffix = Regex(
        """-(?:\d+-)?RM\d+$""",
        RegexOption.IGNORE_CASE
    )

    override fun cleanMerchantName(merchant: String): String {
        return super.cleanMerchantName(merchant)
            .replace(merchantPaymentIdSuffix, "")
            .trim()
    }

    // ------------------------------------------------------------------
    // Cashback ("You have received Cashback Tk 10.00 ... Cashback for Send Money!")
    // ------------------------------------------------------------------

    /** Captures the trailing "Cashback for <original action>!" descriptive clause, if present. */
    private val cashbackForPattern = Regex(
        """Cashback\s+for\s+([^\n!.]+)""",
        RegexOption.IGNORE_CASE
    )

    // ------------------------------------------------------------------
    // Bank deposit via iBanking ("... deposit from iBanking of Tk 500.00 from Sample Bank.")
    // ------------------------------------------------------------------

    /**
     * The message contains TWO "from" clauses - "from iBanking" (the channel) and
     * "from <BANK>" (the actual source bank, after the amount). Only the second one is the
     * source bank; the lazy match over the amount portion skips past "from iBanking" safely
     * because that clause appears BEFORE "of Tk ...", outside the captured span.
     */
    private val ibankingDepositSourcePattern = Regex(
        """iBanking\s+of\s+.+?\s+from\s+([A-Za-z][^.\n]+)""",
        RegexOption.IGNORE_CASE
    )

    // ------------------------------------------------------------------
    // Digital Loan Repayment ("Your Digital Loan Repayment of Tk 1,500.00 is successful. ...")
    // ------------------------------------------------------------------

    /**
     * Unlike the other "of Tk X ... is successful" shapes (DPS Payment, merchant payment), a
     * Digital Loan Repayment receipt has no "to <merchant>" clause - the loan repayment itself
     * IS the description. Presence-only check (no capture group) since the wording is fixed;
     * the surfaced description is a constant, not text lifted from the message.
     */
    private val digitalLoanRepaymentPattern = Regex(
        """Digital\s+Loan\s+Repayment""",
        RegexOption.IGNORE_CASE
    )

    override fun extractMerchant(message: String, sender: String): String? {
        cashbackForPattern.find(message)?.let { match ->
            return "Cashback for ${match.groupValues[1].trim()}"
        }
        if (cashbackReceivedPattern.containsMatchIn(message)) {
            return "Cashback"
        }

        ibankingDepositSourcePattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) return merchant
        }

        if (digitalLoanRepaymentPattern.containsMatchIn(message)) {
            return "Digital Loan Repayment"
        }

        return super.extractMerchant(message, sender)
    }
}
