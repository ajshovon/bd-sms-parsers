# bd-sms-parsers

A standalone Kotlin/JVM library that turns Bangladeshi bank and mobile-financial-service
SMS messages into structured transactions.

No Android dependencies — it is a plain JVM library, so it runs and tests anywhere.

## Supported providers

**Mobile financial services**

| Provider | Senders matched |
|---|---|
| bKash | `BKASH`, `16247`, DLT-wrapped variants (`AD-BKASH`, …) |
| Nagad | `NAGAD`, `16167` |
| Rocket (DBBL) | `ROCKET`, `DBBL`, `16216` |
| Upay | `UPAY`, `16268` |
| Tap | `TAP`, `TRUST AXIATA` |

**Banks**

| Provider | Senders matched |
|---|---|
| City Bank | `CITYBANK`, `CITY BANK`, `CITYTOUCH`, `XX-CITYBK-X` |
| BRAC Bank | `BRACBANK`, `BRAC BANK`, `BBL`, `XX-BRACBK-X` |
| Eastern Bank (EBL) | `EBL`, `EASTERNBANK`, `XX-EBL-X` |
| Mutual Trust Bank (MTB) | `MTB`, `MTBL`, `MUTUALTRUST`, `XX-MTBL-X` |

Sender matching normalises to letters and digits and uppercases, so DLT operator
prefixes and suffixes match. Tokens of four characters or fewer require a whole-segment
match, so `TAP` does not match `WHATSAPP` or `STAPLES`.

## Usage

```kotlin
val parser = BankParserFactory.getParser(sender, smsBody)
val txn = parser?.parse(smsBody, sender, timestampMillis)

txn?.let {
    println("${it.bankName}: ${it.type} ${it.amount} ${it.currency} @ ${it.merchant}")
}
```

`ParsedTransaction.generateTransactionId()` returns a stable SHA-256 derived from
the sender, amount, and message body — useful as a deduplication key.

To filter which providers are active, build a registry instead of using the factory
singleton:

```kotlin
val registry = BankParserRegistry(
    BankParserFactory.getAllParsers().filter { it.getBankName() in enabledBanks }
)
```

## Credit-card balance semantics

Bangladeshi card SMS usually report **available credit**, not outstanding balance —
the figure falls on a purchase and rises when you pay the bill. Parsers signal this
via `creditCardBalanceIsAvailableCredit()`, which is `true` for EBL and MTB and `false`
elsewhere. Consumers must compute `outstanding = creditLimit - balance` when it is set.
Getting this backwards inverts every card figure, so it is covered by its own test.

## Build

```bash
./gradlew test
```

Requires a JDK that your Gradle version supports; output is pinned to Java 11 bytecode
regardless of the JDK used, so local and CI builds are identical.

## License

GNU AGPL-3.0. This library is derived from the `parser-core` module of
[Cashiro](https://github.com/ritesh-kanwar/Cashiro) by Ritesh Kanwar, which is
itself derived from PennyWise. See [NOTICE](NOTICE) for the full attribution and
the list of modifications, and [LICENSE](LICENSE) for the license text.
