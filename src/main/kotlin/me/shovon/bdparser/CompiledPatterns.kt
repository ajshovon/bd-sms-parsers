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
package me.shovon.bdparser

object CompiledPatterns {
    object Amount {
        val RS_PATTERN = Regex("""Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val INR_PATTERN = Regex("""INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val RUPEE_SYMBOL_PATTERN = Regex("""₹\s*([0-9,]+(?:\.\d{2})?)""")
        val ALL_PATTERNS = listOf(RS_PATTERN, INR_PATTERN, RUPEE_SYMBOL_PATTERN)
    }

    object Reference {
        val GENERIC_REF = Regex(
            """(?:Ref|Reference|Txn|Transaction)(?:\s+No)?[:\s]+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        val UPI_REF = Regex("""UPI[:\s]+([0-9]+)""", RegexOption.IGNORE_CASE)
        val REF_NUMBER = Regex("""Reference\s+Number[:\s]+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        val ALL_PATTERNS = listOf(GENERIC_REF, UPI_REF, REF_NUMBER)
    }

    object Account {
        val AC_WITH_MASK = Regex(
            """(?:A/c|Account|Acct)(?:\s+No)?\.?\s+(?:[Xx\*]*\**)?(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val CARD_WITH_MASK = Regex("""Card\s+(?:[Xx\*]*\**)?(\d+)""", RegexOption.IGNORE_CASE)
        val GENERIC_ACCOUNT =
            Regex("""(?:A/c|Account).*?(\d+)(?:\s|$)""", RegexOption.IGNORE_CASE)
        val ALL_PATTERNS = listOf(AC_WITH_MASK, CARD_WITH_MASK, GENERIC_ACCOUNT)
    }

    object Balance {
        val AVL_BAL_RS = Regex("""(?:Bal|Balance|Avl Bal|Available Balance)[:\s]+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val AVL_BAL_INR = Regex("""(?:Bal|Balance|Avl Bal|Available Balance)[:\s]+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val AVL_BAL_RUPEE = Regex("""(?:Bal|Balance|Avl Bal|Available Balance)[:\s]+₹\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val AVL_BAL_NO_CURRENCY = Regex("""(?:Bal|Balance|Avl Bal|Available Balance)[:\s]+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val UPDATED_BAL_RS = Regex("""(?:Updated Balance|Remaining Balance)[:\s]+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val UPDATED_BAL_INR = Regex("""(?:Updated Balance|Remaining Balance)[:\s]+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val ALL_PATTERNS = listOf(AVL_BAL_RS, AVL_BAL_INR, AVL_BAL_RUPEE, AVL_BAL_NO_CURRENCY, UPDATED_BAL_RS, UPDATED_BAL_INR)
    }

    /**
     * Bangladesh Mobile Financial Services (bKash, Nagad, Rocket/DBBL, Upay, Tap) amount
     * patterns. Kept fully separate from [Amount] so Taka currency tokens (Tk, Tk., TK,
     * Taka, BDT, ৳) can never be matched against Indian bank SMS.
     *
     * NOTE: these patterns intentionally do NOT distinguish "Fee"/"Balance"/"Commission"/
     * "Charge" figures from the primary transaction amount - that exclusion logic lives in
     * BangladeshMfsParser.extractAmount(), which scans matches in order and skips any whose
     * preceding context looks like a fee/balance/commission/charge label.
     */
    object TakaAmount {
        /**
         * Case-insensitive Taka currency token: Tk, Tk., TK, Taka, BDT, ৳.
         * `(?<![A-Za-z])` keeps the token from matching the tail of a longer word: without it
         * a reference such as "TrxID BTK4567XY" yields a "TK4567" match that [GENERIC] would
         * report as a Taka amount of 4567.
         */
        private const val CURRENCY_TOKEN = """(?<![A-Za-z])(?:Tk\.?|Taka|BDT|৳)"""

        /** "Amount: Tk 500.00" / "Amount Tk500.00" - explicit amount label used by Nagad/Upay */
        val AMOUNT_LABEL = Regex(
            """Amount:?\s*$CURRENCY_TOKEN\s*([0-9][0-9,]*(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )

        /** Generic Taka figure, e.g. "Tk 1,000.00", "Tk500.00", "BDT 250", "৳500" */
        val GENERIC = Regex(
            """$CURRENCY_TOKEN\s*([0-9][0-9,]*(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )

        val ALL_PATTERNS = listOf(AMOUNT_LABEL, GENERIC)
    }

    object TakaBalance {
        private const val CURRENCY_TOKEN = """(?<![A-Za-z])(?:Tk\.?|Taka|BDT|৳)"""

        /** "Balance: Tk 1,000.00" / "Balance Tk1,234.56" */
        val BALANCE_LABEL = Regex(
            """Balance:?\s*$CURRENCY_TOKEN\s*([0-9][0-9,]*(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )

        val ALL_PATTERNS = listOf(BALANCE_LABEL)
    }

    object TakaFee {
        private const val CURRENCY_TOKEN = """(?<![A-Za-z])(?:Tk\.?|Taka|BDT|৳)"""

        /** "Fee: Tk 18.50" / "Fee Tk0.00" */
        val FEE_LABEL = Regex(
            """Fee:?\s*$CURRENCY_TOKEN\s*([0-9][0-9,]*(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )

        val ALL_PATTERNS = listOf(FEE_LABEL)
    }

    /**
     * Words that indicate a matched Taka figure is NOT the primary transaction amount
     * (fee/balance/commission/charge callouts). Checked against the text immediately
     * preceding a [TakaAmount.GENERIC] match.
     */
    object TakaContext {
        val EXCLUDE_BEFORE_AMOUNT = Regex(
            """\b(fee|charge|commission|balance|avl\.?\s*bal|available\s*balance)\s*[:.]?\s*$""",
            RegexOption.IGNORE_CASE
        )
    }

    /** TrxID / TxnID / TxnId / "Trx ID" / "Txn Id" reference used across BD MFS providers */
    object TakaReference {
        val TRX_TXN_ID = Regex(
            """(?:Trx|Txn)\s*Id[:\s]+([A-Za-z0-9]+)""",
            RegexOption.IGNORE_CASE
        )

        val ALL_PATTERNS = listOf(TRX_TXN_ID)
    }

    object Merchant {
        val TO_PATTERN =
            Regex("""to\s+([^\.\n]+?)(?:\s+on|\s+at|\s+Ref|\s+UPI)""", RegexOption.IGNORE_CASE)
        val FROM_PATTERN =
            Regex("""from\s+([^\.\n]+?)(?:\s+on|\s+at|\s+Ref|\s+UPI)""", RegexOption.IGNORE_CASE)
        val AT_PATTERN = Regex("""at\s+([^\.\n]+?)(?:\s+on|\s+Ref)""", RegexOption.IGNORE_CASE)
        val FOR_PATTERN =
            Regex("""for\s+([^\.\n]+?)(?:\s+on|\s+at|\s+Ref)""", RegexOption.IGNORE_CASE)
        val ALL_PATTERNS = listOf(TO_PATTERN, FROM_PATTERN, AT_PATTERN, FOR_PATTERN)
    }

    object HDFC {
        val DLT_PATTERNS = listOf(
            Regex("^[A-Z]{2}-HDFCBK.*$"),
            Regex("^[A-Z]{2}-HDFC.*$"),
            Regex("^HDFC-[A-Z]+$"),
            Regex("^[A-Z]{2}-HDFCB.*$")
        )

        val SALARY_PATTERN = Regex(
            """for\s+[^-]+-[^-]+-[^-]+\s+[A-Z]+\s+SALARY-([^\.\n]+)""",
            RegexOption.IGNORE_CASE
        )
        val SIMPLE_SALARY_PATTERN =
            Regex("""SALARY[- ]([^\.\n]+?)(?:\s+Info|$)""", RegexOption.IGNORE_CASE)
        val INFO_PATTERN =
            Regex("""Info:\s*(?:UPI/)?([^/\.\n]+?)(?:/|$)""", RegexOption.IGNORE_CASE)
        val VPA_WITH_NAME = Regex("""VPA\s+[^@\s]+@[^\s]+\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
        val VPA_PATTERN = Regex("""VPA\s+([^@\s]+)@""", RegexOption.IGNORE_CASE)
        val SPENT_PATTERN = Regex("""at\s+([^\.\n]+?)\s+on\s+\d{2}""", RegexOption.IGNORE_CASE)
        val DEBIT_FOR_PATTERN =
            Regex("""debited\s+for\s+([^\.\n]+?)\s+on\s+\d{2}""", RegexOption.IGNORE_CASE)
        val MANDATE_PATTERN =
            Regex("""To\s+([^\n]+?)\s*(?:\n|\d{2}/\d{2})""", RegexOption.IGNORE_CASE)

        val REF_SIMPLE = Regex("""Ref\s+(\d{9,12})""", RegexOption.IGNORE_CASE)
        val UPI_REF_NO = Regex("""UPI\s+Ref\s+No\s+(\d{12})""", RegexOption.IGNORE_CASE)
        val REF_NO = Regex("""Ref\s+No\.?\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        val REF_END = Regex(
            """(?:Ref|Reference)[:.\s]+([A-Z0-9]{6,})(?:\s*$|\s*Not\s+You)""",
            RegexOption.IGNORE_CASE
        )

        val ACCOUNT_DEPOSITED = Regex(
            """deposited\s+in\s+(?:HDFC\s+Bank\s+)?A/c\s+(?:XX+)?(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val ACCOUNT_FROM =
            Regex("""from\s+(?:HDFC\s+Bank\s+)?A/c\s+(?:XX+)?(\d+)""", RegexOption.IGNORE_CASE)
        val ACCOUNT_SIMPLE = Regex("""HDFC\s+Bank\s+A/c\s+(\d+)""", RegexOption.IGNORE_CASE)
        val ACCOUNT_GENERIC = Regex("""A/c\s+(?:XX+)?(\d+)""", RegexOption.IGNORE_CASE)

        val AMOUNT_WILL_DEDUCT = Regex(
            """Rs\.?\s*([0-9,]+(?:\.\d{2})?)\s+will\s+be\s+deducted""",
            RegexOption.IGNORE_CASE
        )
        val DEDUCTION_DATE = Regex(
            """deducted\s+on\s+(\d{2}/\d{2}/\d{2}),?\s*\d{2}:\d{2}:\d{2}""",
            RegexOption.IGNORE_CASE
        )
        val MANDATE_MERCHANT = Regex("""For\s+([^\n]+?)\s+mandate""", RegexOption.IGNORE_CASE)
        val UMN_PATTERN = Regex("""UMN\s+([a-zA-Z0-9@]+)""", RegexOption.IGNORE_CASE)
    }

    object Cleaning {
        val TRAILING_PARENTHESES = Regex("""\s*\(.*?\)\s*$""")
        val REF_NUMBER_SUFFIX = Regex("""\s+Ref\s+No.*""", RegexOption.IGNORE_CASE)
        val DATE_SUFFIX = Regex("""\s+on\s+\d{2}.*""")
        val UPI_SUFFIX = Regex("""\s+UPI.*""", RegexOption.IGNORE_CASE)
        val TIME_SUFFIX = Regex("""\s+at\s+\d{2}:\d{2}.*""")
        val TRAILING_DASH = Regex("""\s*-\s*$""")
        val PVT_LTD =
            Regex("""(\s+PVT\.?\s*LTD\.?|\s+PRIVATE\s+LIMITED)$""", RegexOption.IGNORE_CASE)
        val LTD = Regex("""(\s+LTD\.?|\s+LIMITED)$""", RegexOption.IGNORE_CASE)
    }
}
