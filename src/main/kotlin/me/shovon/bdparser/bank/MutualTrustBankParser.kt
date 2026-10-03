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
import me.shovon.bdparser.bank.BankParser.Companion.BalanceUpdateInfo
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Parser for Mutual Trust Bank Limited (MTB, Bangladesh) SMS messages.
 *
 * MTB sends several distinct alert shapes covering account debits/credits, card purchases
 * (including MOTO/e-commerce), card payments and ATM withdrawals.
 *
 * Supported formats:
 * - "Your A/C <masked> has been Debited by BDT X on <date>. Available balance BDT Y. MTB Hotline N"
 * - "Your A/C <masked> has been Credited by BDT X on <date>. Available Balance BDT Y. MTB Hotline N"
 * - "Successful MOTO transaction of BDT X from <merchant> by card- <masked> on <date> at <time>.
 *    Current Balance BDT Y. Helpline N"
 * - "Successful purchase transaction of BDT X from <merchant> by MTB card- <masked> on <date> at
 *    <time>. Current balance BDT Y. Helpline N"
 * - "Payment of BDT X received on MTB Card-<masked> on <date> at <time>"
 * - "Payment of BDT X credited to Card- <masked> on <date> at <time>"
 * - "Your account has been debited by BDT X on <date> at <time>. Current balance BDT Y. Helpline N"
 * - "Withdrawal of BDT X from <ATM/branch> using card-<masked> on <date> at <time>.
 *    Current Balance- BDT Y. Helpline N"
 * - "Dear Cardholder: Merchant return amounting to BDT X has been posted to your MTB card
 *    <masked> on <date>. Query- N" (a refund, so INCOME)
 *
 * Rejected: OTP / IBFT-OTP / BEFTN-OTP messages ("... is your OTP ...", "<#> ... is your OTP
 * for IBFT Fund Transfer ..."), credit-card monthly-bill/statement notices ("Your bill for
 * card ... Min due: ... Payment Due Date: ..."), and balance-enquiry-only messages - none of
 * these carry a debit/credit/transaction verb that any pattern below matches, and OTP wording
 * is additionally caught by the base OTP filter.
 *
 * The monthly-bill/statement notice ("Your bill for card <masked> for <MON> <YYYY> BDT
 * <total> Min due: BDT <min> Payment Due Date: <dd-MON-yyyy>") is NOT a transaction - it stays
 * rejected by [parse]/[isTransactionMessage] above - but is recognised by
 * [isBalanceUpdateNotification]/[parseBalanceUpdate] as a credit-card statement anchor. The
 * unlabeled figure straight after the billing month is the Total (Amount) Due, which is
 * surfaced as both [BalanceUpdateInfo.totalDue] and [BalanceUpdateInfo.balance] (the CC
 * outstanding balance). MTB's statement format does not expose a credit limit.
 *
 * Common senders: MTB, MTBL, Mutual Trust Bank
 * Currency: BDT (Bangladeshi Taka)
 */
class MutualTrustBankParser : BangladeshBankParser() {

    override fun getBankName() = "Mutual Trust Bank"

    /**
     * MTB's card purchase/MOTO/withdrawal alerts ("Current Balance BDT Y") report the card's
     * remaining AVAILABLE CREDIT (falls as purchases are made) - see
     * [BangladeshBankParser.creditCardBalanceIsAvailableCredit] for how this differs from the
     * base class's safe default. The payment patterns above map to [BalanceKind.NONE] and so
     * never capture a balance in the first place, but this flag still governs how any
     * card-tagged balance that does get captured (e.g. via [BalanceKind.CURRENT]) is
     * interpreted downstream.
     */
    override fun creditCardBalanceIsAvailableCredit(): Boolean = true

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase().trim()
        return upper == "MTB" ||
                upper.contains("MTBL") ||
                upper.contains("MUTUALTRUST") ||
                upper.contains("MUTUAL TRUST") ||
                upper.matches(Regex("""^[A-Z]{2}-MTBL-[A-Z]$"""))
    }

    /**
     * Body-marker fallback for when Mobile Number Portability has rewritten the sender ID away
     * from a recognisable Mutual Trust Bank sender: matches when the body mentions "MTB
     * Hotline" or "MTB card" (MTB's own wording), or "MTB" as a whole word. "MTB" alone is
     * short and generic, so it is matched with a word-boundary regex to avoid false positives
     * inside unrelated words (e.g. "assembly", "nimble").
     */
    override fun matchesBodyMarkers(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("mtb hotline") || lower.contains("mtb card")) return true
        return mtbWordPattern.containsMatchIn(message)
    }

    /**
     * Four of MTB's transaction formats - the MOTO card alert, "Payment of BDT X credited to
     * Card-", the generic "Your account has been debited by BDT X" and the ATM withdrawal -
     * carry no "MTB" token anywhere (their only trailer is a bare "Helpline <shortcode>"), as
     * does the monthly-bill statement notice. Under MNP those were undispatchable, so the
     * format patterns themselves serve as the ownership test.
     *
     * Note [accountDebitedGenericPattern] is the loosest of these; an unsupported bank sending
     * the same "Your account has been debited by BDT X" wording from a ported number would be
     * attributed to MTB. That is deliberate - dropping a real transaction silently is the worse
     * failure - but it is the first thing to tighten if a conflicting issuer shows up.
     */
    override fun matchesKnownFormat(message: String): Boolean =
        match(message) != null || statementPattern.containsMatchIn(message)

    /** Compiled once; [canHandleMessage] runs for every parser against every unmatched SMS. */
    private val mtbWordPattern = Regex("""\bmtb\b""", RegexOption.IGNORE_CASE)

    private val takaFigure = """([0-9][0-9,]*(?:\.\d{1,2})?)"""

    private val acDebitedPattern = Regex(
        """Your A/C\s+[0-9Xx*]+\s+has been Debited by BDT\s*$takaFigure""",
        RegexOption.IGNORE_CASE
    )
    private val acCreditedPattern = Regex(
        """Your A/C\s+[0-9Xx*]+\s+has been Credited by BDT\s*$takaFigure""",
        RegexOption.IGNORE_CASE
    )
    private val availableBalancePattern = Regex(
        """Available balance BDT\s*$takaFigure""",
        RegexOption.IGNORE_CASE
    )

    private val motoPattern = Regex(
        """Successful MOTO transaction of BDT\s*$takaFigure\s+from\s+(.+?)\s+by card-""",
        RegexOption.IGNORE_CASE
    )
    private val purchasePattern = Regex(
        """Successful purchase transaction of BDT\s*$takaFigure\s+from\s+(.+?)\s+by\s+MTB card-""",
        RegexOption.IGNORE_CASE
    )
    private val currentBalancePattern = Regex(
        """Current\s+Balance[-:]?\s*BDT\s*$takaFigure""",
        RegexOption.IGNORE_CASE
    )

    private val paymentReceivedPattern = Regex(
        """Payment of BDT\s*$takaFigure\s+received on MTB Card-""",
        RegexOption.IGNORE_CASE
    )
    private val paymentCreditedPattern = Regex(
        """Payment of BDT\s*$takaFigure\s+credited to Card-""",
        RegexOption.IGNORE_CASE
    )

    private val accountDebitedGenericPattern = Regex(
        """Your account has been debited by BDT\s*$takaFigure""",
        RegexOption.IGNORE_CASE
    )

    private val withdrawalPattern = Regex(
        """Withdrawal of BDT\s*$takaFigure\s+from\s+(.+?)\s+using\s+card-""",
        RegexOption.IGNORE_CASE
    )

    private val merchantReturnPattern = Regex(
        """Merchant return amounting to BDT\s*$takaFigure\s+has been posted to your MTB card""",
        RegexOption.IGNORE_CASE
    )

    private enum class BalanceKind { AVAILABLE, CURRENT, NONE }

    private data class Match(
        val amount: String,
        val type: TransactionType,
        val merchant: String?,
        val balanceKind: BalanceKind
    )

    private fun match(message: String): Match? {
        acDebitedPattern.find(message)?.let {
            return Match(it.groupValues[1], TransactionType.EXPENSE, null, BalanceKind.AVAILABLE)
        }
        acCreditedPattern.find(message)?.let {
            return Match(it.groupValues[1], TransactionType.INCOME, null, BalanceKind.AVAILABLE)
        }
        motoPattern.find(message)?.let {
            return Match(it.groupValues[1], TransactionType.EXPENSE, it.groupValues[2].trim(), BalanceKind.CURRENT)
        }
        purchasePattern.find(message)?.let {
            return Match(it.groupValues[1], TransactionType.EXPENSE, it.groupValues[2].trim(), BalanceKind.CURRENT)
        }
        paymentReceivedPattern.find(message)?.let {
            return Match(it.groupValues[1], TransactionType.INCOME, null, BalanceKind.NONE)
        }
        paymentCreditedPattern.find(message)?.let {
            return Match(it.groupValues[1], TransactionType.INCOME, null, BalanceKind.NONE)
        }
        withdrawalPattern.find(message)?.let {
            return Match(it.groupValues[1], TransactionType.EXPENSE, it.groupValues[2].trim(), BalanceKind.CURRENT)
        }
        accountDebitedGenericPattern.find(message)?.let {
            return Match(it.groupValues[1], TransactionType.EXPENSE, null, BalanceKind.CURRENT)
        }
        merchantReturnPattern.find(message)?.let {
            return Match(it.groupValues[1], TransactionType.INCOME, "Merchant Return", BalanceKind.NONE)
        }
        return null
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (match(message) != null) return true
        return super.isTransactionMessage(message)
    }

    override fun extractAmount(message: String): BigDecimal? =
        match(message)?.let { parseTakaAmount(it.amount) }

    override fun extractBalance(message: String): BigDecimal? {
        return when (match(message)?.balanceKind) {
            BalanceKind.AVAILABLE -> availableBalancePattern.find(message)?.let { parseTakaAmount(it.groupValues[1]) }
            BalanceKind.CURRENT -> currentBalancePattern.find(message)?.let { parseTakaAmount(it.groupValues[1]) }
            else -> null
        }
    }

    override fun extractTransactionType(message: String): TransactionType? = match(message)?.type

    override fun extractMerchant(message: String, sender: String): String? = match(message)?.merchant

    // "Your bill for card 460041***0000 for JAN 2020 BDT 18500.4 Min due: BDT 2100.0
    //  Payment Due Date: 09-FEB-2020"
    private val statementPattern = Regex(
        """Your bill for card\s+([0-9Xx*]+)\s+for\s+([A-Za-z]{3,9})\s+(\d{4})\s+BDT\s*$takaFigure\s+Min due:\s*BDT\s*$takaFigure\s+Payment Due Date:\s*(\d{1,2})-([A-Za-z]{3,9})-(\d{4})""",
        RegexOption.IGNORE_CASE
    )

    override fun isBalanceUpdateNotification(message: String): Boolean {
        return statementPattern.containsMatchIn(message)
    }

    /**
     * Parses MTB's credit-card monthly bill/statement notice. The unlabeled figure right after
     * the billing month is treated as the Total Due, mirrored into [BalanceUpdateInfo.balance]
     * as the CC outstanding anchor (see class doc). No credit limit is exposed by this format.
     */
    override fun parseBalanceUpdate(message: String): BalanceUpdateInfo? {
        val match = statementPattern.find(message) ?: return null

        val cardLast4 = extractLast4Digits(match.groupValues[1]) ?: return null
        val statementMonth = parseMonthAbbreviation(match.groupValues[2]) ?: return null
        val statementYear = match.groupValues[3].toIntOrNull() ?: return null
        val totalDue = parseTakaAmount(match.groupValues[4]) ?: return null
        val minDue = parseTakaAmount(match.groupValues[5])
        val dueDay = match.groupValues[6].toIntOrNull()
        val dueMonth = parseMonthAbbreviation(match.groupValues[7])
        val dueYear = match.groupValues[8].toIntOrNull()

        val statementDate = runCatching { LocalDate.of(statementYear, statementMonth, 1) }.getOrNull()
        val dueDate = if (dueDay != null && dueMonth != null && dueYear != null) {
            runCatching { LocalDate.of(dueYear, dueMonth, dueDay) }.getOrNull()
        } else null

        return BalanceUpdateInfo(
            bankName = getBankName(),
            accountLast4 = cardLast4,
            balance = totalDue,
            isCreditCard = true,
            totalDue = totalDue,
            minDue = minDue,
            dueDate = dueDate,
            statementDate = statementDate
        )
    }
}
