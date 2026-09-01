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
 * Verifies [BankParser.canHandleMessage] body-marker detection for the Bangladesh parsers,
 * which is the fallback dispatch path used when Mobile Number Portability has rewritten the
 * SMS sender ID away from a recognisable bank sender (see [BankParser.canHandleMessage] KDoc).
 *
 * Sender used throughout is a sender that no parser recognises ("UNKNOWN-123"), so every
 * positive match below is coming from the message BODY, not the sender.
 *
 * Sample bodies are reused, already-redacted placeholder messages taken directly from the
 * corresponding parser's own test file (BkashParserTest, NagadParserTest, RocketParserTest,
 * UpayParserTest, TapParserTest, CityBankParserTest, BracBankParserTest,
 * EasternBankParserTest, MutualTrustBankParserTest) - no new PII is introduced here.
 */
class BankBodyDetectionTest {

    private val unknownSender = "UNKNOWN-123"

    // Representative bodies, one per BD parser, each carrying that parser's body marker(s).
    private val bkashBody =
        "Cash In Tk 1,000.00 from 01700000000 successful. Fee Tk 0.00. Balance Tk 1,500.00. " +
            "TrxID AB12CD34EF at 01/01/2024 10:30"
    private val nagadBody = "Your Nagad OTP is 456789. Valid for 5 minutes."
    private val rocketBody = "Your Rocket OTP is 112233. Do not share."
    private val upayBody = "Your Upay OTP is 998877."
    private val tapBody = "Your Tap OTP is 445566."
    private val cityBankBody =
        "03-Jan-24 CITYTOUCH TXN Tk. 1,200.00 Withdrawal Tk. 30,000.00 Balance A/C: 98765**4321"
    private val bracBankBody =
        "Tk 5,500.00 (incl. charges) has been debited from your BBL A/C: 5*1234 to A/C: 9*4321 " +
            "for NPSB transfer. available balance Tk 15,000.00. Helpline: 16221"
    private val easternBankBody =
        "AC 12345**6789 is debited with BDT 3,000.00 as EBL Account Transfer on 02-Jan-24 09:00:00 AM BST"
    private val mutualTrustBankBody =
        "Dear Customer, Your A/C XXXXX000000 has been Debited by BDT 15,000.00 on 01/01/24. " +
            "Available balance BDT 1,00,000.00. MTB Hotline 16000"

    @TestFactory
    fun `own body marker is detected via canHandleMessage`(): List<DynamicTest> {
        val cases = listOf(
            Triple("bKash", BkashParser(), bkashBody),
            Triple("Nagad", NagadParser(), nagadBody),
            Triple("Rocket", RocketParser(), rocketBody),
            Triple("Upay", UpayParser(), upayBody),
            Triple("Tap", TapParser(), tapBody),
            Triple("City Bank", CityBankParser(), cityBankBody),
            Triple("BRAC Bank", BracBankParser(), bracBankBody),
            Triple("Eastern Bank", EasternBankParser(), easternBankBody),
            Triple("Mutual Trust Bank", MutualTrustBankParser(), mutualTrustBankBody)
        )

        return cases.map { (name, parser, body) ->
            dynamicTest("$name canHandleMessage(unknownSender, ownBody) is true") {
                assertTrue(
                    parser.canHandleMessage(unknownSender, body),
                    "$name should detect its own body marker with sender '$unknownSender'"
                )
            }
        }
    }

    @TestFactory
    fun `cross-institution bodies do not trigger unrelated parsers`(): List<DynamicTest> {
        val bodiesByInstitution = linkedMapOf(
            "bKash" to bkashBody,
            "Nagad" to nagadBody,
            "Rocket" to rocketBody,
            "Upay" to upayBody,
            "Tap" to tapBody,
            "City Bank" to cityBankBody,
            "BRAC Bank" to bracBankBody,
            "Eastern Bank" to easternBankBody,
            "Mutual Trust Bank" to mutualTrustBankBody
        )

        val parsersByInstitution: Map<String, BankParser> = linkedMapOf(
            "bKash" to BkashParser(),
            "Nagad" to NagadParser(),
            "Rocket" to RocketParser(),
            "Upay" to UpayParser(),
            "Tap" to TapParser(),
            "City Bank" to CityBankParser(),
            "BRAC Bank" to BracBankParser(),
            "Eastern Bank" to EasternBankParser(),
            "Mutual Trust Bank" to MutualTrustBankParser()
        )

        val tests = mutableListOf<DynamicTest>()

        parsersByInstitution.forEach { (parserName, parser) ->
            // Every OTHER institution's body must NOT be detected by this parser (at least 3,
            // as required; in practice all 8 others are checked since none should collide).
            bodiesByInstitution
                .filterKeys { it != parserName }
                .forEach { (otherInstitution, otherBody) ->
                    tests.add(
                        dynamicTest(
                            "$parserName canHandleMessage(unknownSender, ${otherInstitution}Body) is false"
                        ) {
                            assertFalse(
                                parser.canHandleMessage(unknownSender, otherBody),
                                "$parserName should NOT match $otherInstitution's body marker"
                            )
                        }
                    )
                }
        }

        return tests
    }

    @TestFactory
    fun `short markers do not false-positive on unrelated words`(): List<DynamicTest> {
        val falsePositiveBodies = listOf(
            "WhatsApp" to "Please open WhatsApp to view your latest offer summary.",
            "tapped" to "The delivery courier tapped the door twice before leaving the package.",
            "assembly" to "The annual general assembly meeting has been rescheduled to next week.",
            "nimble" to "Our new app update makes navigation faster and more nimble than before."
        )

        val shortMarkerParsers: Map<String, BankParser> = linkedMapOf(
            "Tap" to TapParser(),
            "Eastern Bank" to EasternBankParser(),
            "Mutual Trust Bank" to MutualTrustBankParser()
        )

        val tests = mutableListOf<DynamicTest>()

        shortMarkerParsers.forEach { (parserName, parser) ->
            falsePositiveBodies.forEach { (label, body) ->
                tests.add(
                    dynamicTest("$parserName canHandleMessage(unknownSender, \"$label\" body) is false") {
                        assertFalse(
                            parser.canHandleMessage(unknownSender, body),
                            "$parserName should NOT false-positive on the '$label' body"
                        )
                    }
                )
            }
        }

        return tests
    }
}
