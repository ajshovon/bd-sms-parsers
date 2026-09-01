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
 * Factory for creating bank-specific parsers based on SMS sender.
 */
object BankParserFactory {

    private val parsers = listOf(
        // Bangladesh MFS (Mobile Financial Services)
        BkashParser(),            // bKash
        NagadParser(),            // Nagad
        RocketParser(),           // Rocket / DBBL
        UpayParser(),             // Upay
        TapParser(),              // Tap / Trust Axiata Digital

        // Bangladesh full-service banks
        CityBankParser(),         // City Bank
        BracBankParser(),         // BRAC Bank / BBL
        EasternBankParser(),      // Eastern Bank / EBL
        MutualTrustBankParser()   // Mutual Trust Bank / MTB
    )

    /**
     * Returns the appropriate bank parser for the given sender.
     * Returns null if no specific parser is found.
     */
    fun getParser(sender: String): BankParser? {
        return parsers.firstOrNull { it.canHandle(sender) }
    }

    /**
     * Returns all bank parsers that can handle the given sender.
     * Multiple parsers may match the same sender (e.g., specialized parsers
     * before a general one), so callers should use firstNotNullOfOrNull
     * to let the content decide which parser produces a result.
     */
    fun getParsers(sender: String): List<BankParser> {
        return parsers.filter { it.canHandle(sender) }
    }

    /**
     * Returns all bank parsers that can handle the given sender/message pair, falling back to
     * body-marker detection when the sender alone doesn't identify a known bank (e.g. Mobile
     * Number Portability rewrites the sender ID so bank SMS no longer arrive from their usual
     * sender). Sender match always wins: if any parser matches [sender] via [BankParser.canHandle],
     * only sender-matching parsers are returned; the body-marker fallback via
     * [BankParser.canHandleMessage] is only used when no parser matches by sender.
     */
    fun getParsers(sender: String, message: String): List<BankParser> {
        val bySender = parsers.filter { it.canHandle(sender) }
        if (bySender.isNotEmpty()) return bySender
        return parsers.filter { it.canHandleMessage(sender, message) }
    }

    /**
     * Returns the bank parser for the given bank name.
     * Returns null if no specific parser is found.
     */
    fun getParserByName(bankName: String): BankParser? {
        return parsers.firstOrNull { it.getBankName() == bankName }
    }

    /**
     * Returns all available bank parsers.
     */
    fun getAllParsers(): List<BankParser> = parsers

    /**
     * Checks if the sender belongs to any known bank.
     */
    fun isKnownBankSender(sender: String): Boolean {
        return parsers.any { it.canHandle(sender) }
    }

    /**
     * Returns the appropriate bank parser for the given sender/message pair, falling back to
     * body-marker detection when the sender alone doesn't identify a known bank (e.g. Mobile
     * Number Portability rewrites the sender ID so bank SMS no longer arrive from their usual
     * sender). Sender match always wins: this tries [BankParser.canHandle] against every
     * parser first, and only if none match does it fall back to [BankParser.canHandleMessage].
     * In both passes, the first matching parser in [parsers] wins ties.
     * Returns null if no parser matches by either sender or body.
     */
    fun getParser(sender: String, message: String): BankParser? {
        return parsers.firstOrNull { it.canHandle(sender) }
            ?: parsers.firstOrNull { it.canHandleMessage(sender, message) }
    }

    /**
     * Checks if the sender/message pair belongs to any known bank, considering both sender
     * and body-marker detection (see [getParser]).
     */
    fun isKnownBankSender(sender: String, message: String): Boolean {
        return getParser(sender, message) != null
    }
}
