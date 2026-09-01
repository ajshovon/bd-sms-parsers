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
import me.shovon.bdparser.test.ExpectedTransaction
import me.shovon.bdparser.test.ParserTestCase
import me.shovon.bdparser.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BkashParserTest {

    @TestFactory
    fun `test bKash Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = BkashParser()

        ParserTestUtils.printTestHeader(
            parserName = "bKash",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Cash In",
                message = "Cash In Tk 1,000.00 from 01700000000 successful. Fee Tk 0.00. Balance Tk 1,500.00. TrxID AB12CD34EF at 01/01/2024 10:30",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("1500.00"),
                    reference = "AB12CD34EF"
                )
            ),
            // Fee-vs-amount hazard: amount must be 1000.00, NOT the fee (18.50) or balance (500.00)
            ParserTestCase(
                name = "Cash Out - fee and balance exclusion hazard",
                message = "Cash Out Tk 1,000.00 to 01700000000 successful. Fee Tk 18.50. Balance Tk 500.00. TrxID CD56EF78GH at 01/01/2024 11:00",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("500.00"),
                    reference = "CD56EF78GH"
                )
            ),
            ParserTestCase(
                name = "Received (push notification style)",
                message = "You have received Tk 500.00 from 01700000000. Ref 123. Fee Tk 0.00. Balance Tk 1,000.00. TrxID EF90GH12IJ at 01/01/2024 12:00",
                sender = "16247",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("1000.00"),
                    reference = "EF90GH12IJ"
                )
            ),
            // Another fee/balance exclusion hazard case
            ParserTestCase(
                name = "Send Money - fee and balance exclusion hazard",
                message = "Send Money Tk 200.00 to 01700000000 successful. Fee Tk 5.00. Balance Tk 800.00. TrxID GH34IJ56KL at 01/01/2024 13:00",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("800.00"),
                    reference = "GH34IJ56KL"
                )
            ),
            ParserTestCase(
                name = "Payment to merchant",
                message = "Payment Tk 350.00 to SHOP NAME successful. Ref 456. Fee Tk 0.00. Balance Tk 650.00. TrxID IJ78KL90MN at 01/01/2024 14:00",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("350.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "SHOP NAME",
                    balance = BigDecimal("650.00"),
                    reference = "IJ78KL90MN"
                )
            ),
            ParserTestCase(
                name = "Mobile recharge",
                message = "Your mobile recharge Tk 50.00 to 01700000000 successful. Balance Tk 600.00. TrxID KL12MN34OP at 01/01/2024 15:00",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("600.00"),
                    reference = "KL12MN34OP"
                )
            ),
            // Extra hazard case: Commission and Charge labels must also be excluded from amount
            ParserTestCase(
                name = "Commission and charge exclusion hazard",
                message = "Cash Out Tk 750.00 to 01700000000 successful. Commission Tk 12.00. Charge Tk 3.00. Balance Tk 900.00. TrxID MN56OP78QR at 01/01/2024 16:00",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("900.00"),
                    reference = "MN56OP78QR"
                )
            ),

            // Fee appears BEFORE the amount in the message body - amount must still be 600.00
            ParserTestCase(
                name = "Fee-first ordering - fee before amount hazard",
                message = "Fee Tk 15.00 has been deducted. Cash Out Tk 600.00 to 01900000000 successful. Balance Tk 900.00. TrxID OP90QR12ST at 01/01/2024 17:00",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("600.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("900.00"),
                    reference = "OP90QR12ST"
                )
            ),
            // Currency token variant: "Taka" instead of "Tk"
            ParserTestCase(
                name = "Currency token variant - Taka",
                message = "Cash In Taka 750.00 from 01800000000 successful. Fee Taka 0.00. Balance Taka 1,200.00. TrxID QR12ST34UV at 01/01/2024 18:00",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("1200.00"),
                    reference = "QR12ST34UV"
                )
            ),

            // Negative cases
            ParserTestCase(
                name = "OTP message is ignored",
                message = "Your bKash OTP is 123456. Do not share this with anyone.",
                sender = "bKash",
                shouldParse = false
            ),
            ParserTestCase(
                name = "PIN reset message is ignored",
                message = "Please reset your PIN immediately for security.",
                sender = "bKash",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message is ignored",
                message = "Cash Out with 5% cashback offer this Eid! T&C apply.",
                sender = "bKash",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Balance-enquiry-only message is ignored",
                message = "Your bKash Balance is Tk 500.00 as of 01/01/2024 10:00",
                sender = "bKash",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Failed/unsuccessful transaction is ignored",
                message = "Cash Out Tk 1,000.00 to 01700000000 unsuccessful. Please try again.",
                sender = "bKash",
                shouldParse = false
            ),

            // ------------------------------------------------------------
            // FORMAT 1 - P2P received, real-world shape (with and without "Ref" clause)
            // ------------------------------------------------------------
            ParserTestCase(
                name = "Format - Received with Ref clause",
                message = "You have received Tk 400.00 from 01XXXXXXXXX. Ref food bill. Fee Tk 0.00. Balance Tk 2,000.00. TrxID DH000AAA11 at 01/01/2024 19:00",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("400.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("2000.00"),
                    reference = "DH000AAA11"
                )
            ),
            ParserTestCase(
                name = "Format - Received without Ref clause",
                message = "You have received Tk 400.00 from 01XXXXXXXXX. Fee Tk 0.00. Balance Tk 2,000.00. TrxID DH000AAA12 at 01/01/2024 19:00",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("400.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("2000.00"),
                    reference = "DH000AAA12"
                )
            ),

            // ------------------------------------------------------------
            // FORMAT 2 - Multiline bill payment (main real-world gap)
            // ------------------------------------------------------------
            ParserTestCase(
                name = "Format - Multiline bill payment (biller name only)",
                message = "Bill successfully paid.\nBiller: Demo Utility \nMMYYYY/Contact: 012024\nA/C: ACC001 \nAmount: Tk 500.00 \nFee: Tk 0.00 \nTrxID: DH000AAA13 at 01/01/2024 19:05",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Demo Utility",
                    reference = "DH000AAA13"
                )
            ),
            ParserTestCase(
                name = "Format - Multiline bill payment (Contact holds a phone number, not amount)",
                message = "Bill successfully paid.\nBiller: Sample Biller \nMMYYYY/Contact: 01XXXXXXXXX\nA/C: 40600000000 \nAmount: Tk 1250.50 \nFee: Tk 0.00 \nTrxID: DH000AAA14 at 01/01/2024 19:05",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.50"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Sample Biller",
                    reference = "DH000AAA14"
                )
            ),
            ParserTestCase(
                name = "Format - Multiline bill payment (alphanumeric A/C, prepaid biller)",
                message = "Bill successfully paid.\nBiller: Example Utility \nMMYYYY/Contact: 022024\nA/C: ACC001 \nAmount: Tk 300.00 \nFee: Tk 0.00 \nTrxID: DH000AAA15 at 01/01/2024 19:05",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Example Utility",
                    reference = "DH000AAA15"
                )
            ),

            // ------------------------------------------------------------
            // FORMAT 3 - Merchant payment ("Payment of Tk X to Y is successful")
            //            with merchant-payment-ID suffix stripping
            // ------------------------------------------------------------
            ParserTestCase(
                name = "Format - Merchant payment, 'is successful' wording, -N-RM suffix",
                message = "Payment of Tk 450.00 to Example Store Limited-1-RM10001 is successful. Balance Tk 2,500.00. TrxID DH000AAA16 at 01/01/2024 19:10",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("450.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Example Store Limited",
                    balance = BigDecimal("2500.00"),
                    reference = "DH000AAA16"
                )
            ),
            ParserTestCase(
                name = "Format - Merchant payment, bare -RM suffix (no leading digit segment)",
                message = "Payment of Tk 150.00 to Sample Shop Limited-RM10002 is successful. Balance Tk 1,000.00. TrxID DH000AAA17 at 01/01/2024 19:10",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Sample Shop Limited",
                    balance = BigDecimal("1000.00"),
                    reference = "DH000AAA17"
                )
            ),
            ParserTestCase(
                name = "Format - Merchant payment, multi-word merchant with -N-RM suffix",
                message = "Payment of Tk 1000.00 to Demo Retail Limited-1-RM10003 is successful. Balance Tk 5,000.00. TrxID DH000AAA18 at 01/01/2024 19:10",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Demo Retail Limited",
                    balance = BigDecimal("5000.00"),
                    reference = "DH000AAA18"
                )
            ),

            // ------------------------------------------------------------
            // FORMAT 4 - Cashback (must be INCOME, not misread as Send Money expense)
            // ------------------------------------------------------------
            ParserTestCase(
                name = "Format - Cashback for Send Money is not misclassified as an expense",
                message = "Congratulations! You have received Cashback Tk 20.00. Balance Tk 1,000.00. TrxID DH000AAA19 at 01/01/2024 19:15. Cashback for Send Money!",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    merchant = "Cashback for Send Money",
                    balance = BigDecimal("1000.00"),
                    reference = "DH000AAA19"
                )
            ),

            // ------------------------------------------------------------
            // FORMAT 5 - Bank deposit via iBanking (two "from" clauses)
            // ------------------------------------------------------------
            ParserTestCase(
                name = "Format - iBanking deposit from Sample Bank (two 'from' clauses hazard)",
                message = "You have received deposit from iBanking of Tk 500.00 from Sample Bank. Fee Tk 0.00. Balance Tk 2,000.00. TrxID DH000AAA20 at 01/01/2024 19:20",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    merchant = "Sample Bank",
                    balance = BigDecimal("2000.00"),
                    reference = "DH000AAA20"
                )
            ),
            ParserTestCase(
                name = "Format - iBanking deposit from Example Bank",
                message = "You have received deposit from iBanking of Tk 750.00 from Example Bank. Fee Tk 0.00. Balance Tk 3,000.00. TrxID DH000AAA21 at 01/01/2024 19:20",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    merchant = "Example Bank",
                    balance = BigDecimal("3000.00"),
                    reference = "DH000AAA21"
                )
            ),

            // ------------------------------------------------------------
            // FORMAT 6 - Must be ignored (return null)
            // ------------------------------------------------------------
            ParserTestCase(
                name = "Ignored - discount coupon (highest-risk false positive: 'received' + amount)",
                message = "You received BDT 20 Mobile Recharge discount coupon! Enjoy discount by applying coupon through bKash app's Mobile Recharge. TCA. Validity: 01-01-2024",
                sender = "bKash",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Ignored - Account Binding request/authorization",
                message = "Your Account Binding request for Sample Merchant is successful. You have authorized Sample Merchant to debit your account for future purchases. For queries, please call 16247.",
                sender = "bKash",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Ignored - Account Binding cancelled",
                message = "Your Account Binding with Sample Merchant has been cancelled. For queries, please call 16247.",
                sender = "bKash",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Ignored - AUTO DEBIT enable OTP",
                message = "Do NOT share your OTP or PIN with anyone. Your bKash OTP to enable AUTO DEBIT in App or Website of Sample Merchant is 000111. Expires in 5 min.",
                sender = "bKash",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Ignored - Verification Code (short-code style)",
                message = "<#> NEVER share your Verification Code and PIN with anyone. bKash never asks for these. Verification Code: 000222. Expiry: 30 seconds. A0B1C2D3E4F5A6B",
                sender = "bKash",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Ignored - bKash verification code (plain wording)",
                message = "Your bKash verification code is 000333. The code will expire in 2 minutes. Please do NOT share your OTP or PIN with others.",
                sender = "bKash",
                shouldParse = false
            ),

            // ------------------------------------------------------------
            // FORMAT 7 - DPS Payment
            // ------------------------------------------------------------
            ParserTestCase(
                name = "Format - DPS Payment",
                message = "Congratulations! DPS Payment of Tk 500.00 to Example Bank DPS is successful. Balance Tk 2,500.00. TrxID DH000AAA22 at 01/01/2024 19:25",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Example Bank DPS",
                    balance = BigDecimal("2500.00"),
                    reference = "DH000AAA22"
                )
            ),

            // ------------------------------------------------------------
            // FORMAT 8 - Digital Loan Repayment (no "to <merchant>" clause)
            // ------------------------------------------------------------
            ParserTestCase(
                name = "Format - Digital Loan Repayment",
                message = "Your Digital Loan Repayment of Tk 2,000.00 is successful. Balance Tk 4,000.00. TrxID DH000AAA23 at 01/01/2024 19:30",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Digital Loan Repayment",
                    balance = BigDecimal("4000.00"),
                    reference = "DH000AAA23"
                )
            ),

            // ------------------------------------------------------------
            // FORMAT 9 - Mobile Recharge (no Fee/Balance/TrxID trailer at all; trailing
            //            promotional-sounding app footer must NOT be rejected as promo)
            // ------------------------------------------------------------
            ParserTestCase(
                name = "Format - Mobile Recharge with promotional app footer is still parsed",
                message = "Your bKash Mobile Recharge request of Tk 500.00 for ending 1234 was successful. Use bKash App for convenience & offers! TCA",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE
                )
            ),
            // A genuine promotional message using the same "offer" word (singular, standalone)
            // must still be rejected - proves the app-footer fix did not weaken promo detection.
            ParserTestCase(
                name = "Ignored - genuine promotional offer is still rejected",
                message = "Get a special Mobile Recharge offer this week only! T&C apply.",
                sender = "bKash",
                shouldParse = false
            ),

            // ------------------------------------------------------------
            // FORMAT 10 - Multiline bill payment, "MM/YYYY:"/"Account" label variant
            // ------------------------------------------------------------
            ParserTestCase(
                name = "Format - Multiline bill payment, MM/YYYY:/Account label variant",
                message = "Bill successfully paid.\nBiller: Demo Utility \nMM/YYYY: 032024\nAccount ACC002 \nAmount: Tk 750.00 \nFee: Tk 0.00 \nTrxID: DH000AAA24 at 01/01/2024 19:35",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Demo Utility",
                    reference = "DH000AAA24"
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "bKash" to true,
            "BKASH" to true,
            "16247" to true,
            // DLT-prefixed/suffixed sender variants
            "AD-BKASH" to true,
            "BKASH-BD" to true,
            "bd-bkash" to true,
            // punctuation/spacing variants
            "bKash.SMS" to true,
            "Nagad" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "bKash Parser Tests")
    }
}
