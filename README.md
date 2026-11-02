# FreeCIE

Unofficial, open-source Android app that does one thing: **log in on a computer
with your Italian electronic ID card (CIE) at level 3**, using the phone as the
card reader. It replaces the CieID app for the "computer + smartphone" flow:

1. On the computer, pick **Entra con CIE** and continue with the smartphone
   (level 3). The page shows a QR code.
2. Scan it with FreeCIE, type the 8-digit card PIN, hold the CIE against the
   back of the phone.
3. Type the 4-digit code the app shows into the computer.

Nothing else: no accounts, no card registration, no level 1/2 credentials,
nothing stored. The PIN is used once to unlock the card and then discarded.

> Not affiliated with IPZS or the Ministero dell'Interno.

## Status

- Builds and unit tests pass (`nix build`, `nix flake check`).
- **Not yet tried against a real card and the real identity server.** The card
  protocol is the one the official app runs (see below), but the first real
  login still has to be done by someone with a CIE and an NFC phone.
- Level 1/2 QR codes (`livello2QR?…&level=2`, CieID username/password) are
  rejected on purpose.

## How it works

The card protocol comes from
[pagopa/cie-sdk-android](https://github.com/pagopa/cie-sdk-android) (the SDK
the IO app uses). The computer flow comes from an interoperability analysis of
CieID 1.7.28 (`it.ipzs.cieid`), whose card layer is the same lineage of code
(italia/cieid-android-sdk).

| Step | Detail |
|---|---|
| QR | `https://idserver.servizicie.interno.gov.it/idp/login/…QR?opId=…&challenge=…&level=…&SPName=…`. `level` 1/2 = CieID credentials; anything else = level 3. |
| Card (NFC, ISO-DEP) | select IAS/CIE applets → read NIS → DH key exchange + device/chip authentication → 3DES/retail-MAC secure messaging → `VERIFY PIN` → read certificate (EF `1003`) |
| Signature | card signs `DigestInfo(SHA-256(<scanned QR text>))` |
| Server | `POST https://idserver.servizicie.interno.gov.it/idp/Authn/SSL/X509AuthIbrido` (or the `oidc.` host for OIDC QR codes), form `hashJsonFirmato=<HEX>&opId=<opId>`, over **TLS 1.2 with the card certificate as client certificate**; the handshake signature is also made by the card |
| Result | body `…:NNNN` → the one-time code typed on the computer |

Code map:

- `app/src/main/java/org/freecie/app/QrLoginRequest.kt`: QR parsing and validation (only the two identity-server hosts are accepted).
- `HybridAuth.kt`: what gets signed, the form body, response parsing.
- `HybridClient.kt`: OkHttp over mutual TLS with the card-backed key.
- `CieLogin.kt`: the whole card + server sequence and error classification.
- `LoginViewModel.kt`, `MainActivity.kt`: NFC reader mode, camera permission, screen state.
- `ui/`: Compose UI implementing the Claude Design mockups ("FreeCIE Mockups", brief in
  [docs/design-brief.md](docs/design-brief.md)), including the in-app ZXing scanner overlay and
  the hold-card illustration. `ScreenshotTest` renders every screen in light and dark.
- No third-party code is copied into this repository. See "Card layer" below.

## Card layer: pagopa/cie-sdk-android, pinned and patched by Nix

The flake fetches
[pagopa/cie-sdk-android](https://github.com/pagopa/cie-sdk-android) at commit
`5aed464` (v0.1.11, hash-pinned) and applies the patches in
[`patches/cie-sdk-android/`](patches/cie-sdk-android). It keeps only the
card-protocol sources (APDUs, secure messaging, PIN, certificate, signing and
the card-backed JCA key: `cieSdkFiles` in `flake.nix`) and passes their path to
Gradle as `FREECIE_CIE_SDK_SRC` (`nix build .#cieSdkSrc` shows the result).

The Maven Central artifact can't be used as is. Its public API covers the
mobile `Login2` flow, NIS and PACE, but not the computer flow. The classes we
need are Kotlin `internal`, so they're compiled into the app instead.

| Patch | Why |
|---|---|
| `0001` Sign through CieSigner, only with the card key | The JCA provider no longer needs the SDK's NFC session classes; it refuses other keys (it is inserted first) and honours offset/length |
| `0002` Take the PIN as bytes and record the VERIFY status | No `String` copy of the PIN; the real status lets the app tell "blocked" from an unexpected answer |
| `0003` Wipe plaintext command copies once encrypted | The plaintext VERIFY command holds the PIN |
| `0004` Never log | The SDK's logger could write card traffic to logcat |

To move to a newer pagoPA commit: update `cieSdkRev` and its hash in
`flake.nix`, then rebase the patches. To regenerate them, apply them to a
checkout and run `git format-patch <rev>`.

Diagnostic log (Home → log icon, or "Mostra registro diagnostico" after an
error): a persistent, timestamped record of every attempt that can be shared
as a text file or copied.

- **PIN events are highlighted:** entered, sent to the card, accepted, rejected with the attempts left,
  blocked, or not sent at all because the failure came first.
- **Requests:** every URL requested, with DNS, TLS and timing details.
- **Card commands:** headers and status words only.
- **Never recorded:** the PIN, the one-time code, QR parameter values, command data or personal data.
  The certificate subject (name and tax code) and the NIS are left out.

The app is in Italian only, whatever the phone's language.

Safety choices:

- The PIN is tried exactly once per user action, and a wrong PIN never triggers an automatic retry.
- The PIN is never written anywhere permanent (logs, files, preferences, saved state, clipboard,
  logcat). On submit it becomes an 8-byte array that goes to the card without `String` copies and is
  zero-filled right after the attempt (or on cancel). The card layer wipes its own plaintext copies
  once they are encrypted. Unavoidable in the JVM: the text field's own `String` (cleared on submit,
  then garbage-collected) and the platform cipher's internal buffers.
- The PIN field is a numeric password field: keyboards must not learn or suggest it, and copy is
  disabled. The app is also excluded from autofill and content capture.
- The screen is `FLAG_SECURE`.
- As a last line of defence, the log writer masks any run of 8 or more digits.
- The card-backed JCA provider only handles the card key (patch `0001`).

## Build

Everything goes through the Nix flake (same structure as a Flutter flake, minus Flutter):

```sh
nix build .#apk          # unsigned release APK (arm64-v8a), to be signed by CI
nix build .#apk-debug    # debug-signed APK, installable for testing
nix build .#screenshots  # every screen, light and dark (also screenshot-<screen>-<theme> release files)
nix flake check          # JVM unit tests + screenshot regression check
nix run                  # adb install the debug APK on a connected phone
nix develop              # JDK 21, Gradle, Android SDK 35, FREECIE_CIE_SDK_SRC; then: gradle assembleDebug
```

Unit tests cover QR parsing, the protocol format and the secure-messaging
crypto (against the ICAO 9303 worked example). Screenshots are rendered on the
JVM with [Paparazzi](https://github.com/cashapp/paparazzi); after an intended
UI change, refresh the references with `gradle recordPaparazziDebug` and commit
`app/src/test/snapshots/images`.

Debug builds are signed with the public key in `app/debug.keystore`
(password `android`), so each new debug build updates the previous one in place.

The Maven dependencies are a fixed-output derivation (`packages.gradleRepo`).
After changing dependencies, set its `outputHash` to `lib.fakeHash`, build
once, and paste the reported hash.

Requirements on the phone: Android 7+, NFC with extended-length APDU support
(most phones since ~2017), internet.

## Licence

App code and `patches/`: BSD-3-Clause. pagopa/cie-sdk-android (compiled into
the app, not stored here): MIT, © PagoPA S.p.A. ZXing Android Embedded, OkHttp,
AndroidX, Material Symbols (`res/drawable/ms_*.xml`) and Roboto Mono
(`res/font/`): Apache-2.0.
