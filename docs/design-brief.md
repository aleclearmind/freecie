# Design brief: OpenCIE

An Android app for logging in on a computer with the Italian electronic ID card (CIE), level 3.

## Purpose

A single-purpose, unofficial, open-source Android app. The user starts a login on their
computer ("Entra con CIE" → continue with smartphone, level 3). The computer shows a QR code.
The phone app scans it, asks for the card PIN, reads the CIE over NFC (card held against the
back of the phone), and shows a 4-digit code that the user types on the computer.
Nothing else: no accounts, no settings, no card registration, nothing stored.

## Platform and style

- Android phone, portrait, Material 3 (Jetpack Compose), light and dark themes, edge-to-edge.
- Calm, trustworthy, government-service feel, but clearly **not official**. Don't use the CIE
  logo, the Italian Republic emblem, the "CieID" name or IPZS/Ministero branding. A neutral deep
  blue accent (`#0B5394`) is fine. App icon: a generic ID card with contactless waves.
- Primary language Italian; every string also needs an English version (below).
- Accessibility: large touch targets, TalkBack labels, works at 200% font size, never rely on
  colour alone (errors get an icon and text).

## Flow

```
Home → (scan QR | paste link) → Request + PIN → Hold card (live progress) → Code
                                      ↑ wrong PIN (attempts left)          ↘ Error
```

## Screens

### 1. Home

- Title: "Accedi dal computer con la tua CIE"
- Four numbered steps:
  1. "Sul computer scegli “Entra con CIE” e prosegui con lo smartphone (livello 3). La pagina mostra un codice QR."
  2. "Inquadra il codice QR con questa app."
  3. "Inserisci il PIN della CIE (8 cifre) e appoggia la carta sul retro del telefono."
  4. "Digita sul computer il codice di 4 cifre mostrato qui."
- Primary button: "Inquadra il codice QR".
  Secondary text button: "Inserisci il link del QR a mano" (opens a sheet with one URL field and "Continua").
- NFC banner, only when needed:
  - "L’NFC è disattivato." with button "Attiva l’NFC";
  - or "Questo telefono non ha l’NFC, necessario per leggere la CIE." (blocking, no button).
- Footer, small: "App open source non ufficiale, non affiliata a IPZS né al Ministero
  dell’Interno. Il PIN serve solo a sbloccare la carta e non viene mai salvato."

### 2. QR scanner

Full-screen camera (standard scanner component). Only an overlay prompt
"Inquadra il codice QR mostrato sul computer" and a back/close control.

### 3. Request + PIN

- Title: "Richiesta di accesso"
- Card: label "Servizio", value = service name (often a host such as `iam.example.it`; can be
  long, so wrap or ellipsize), chip "Livello 3: carta CIE e PIN".
- Warning callout: "Prosegui solo se hai appena avviato tu questo accesso dal tuo computer."
- PIN field: "PIN della CIE (8 cifre)", numeric keypad, masked, 8-digit counter.
- Inline error states: "Il PIN deve essere di 8 cifre." / "PIN errato. Tentativi rimasti: 2."
  (attempts left can be 2 or 1; make the "1 attempt left" case feel more serious).
- Buttons: "Continua" (enabled only at 8 digits), "Annulla".

### 4. Hold card (NFC in progress)

- Illustration: a card held flat against the back/top of a phone, contactless waves.
- Title: "Appoggia la CIE sul retro del telefono"
- Body: "Tienila ferma finché non compare il codice. Servono pochi secondi e una connessione a internet."
- Live step list, current step highlighted, completed steps checked:
  1. "Connessione alla carta…"
  2. "Apertura del canale sicuro e verifica del PIN…"
  3. "Lettura del certificato di autenticazione…"
  4. "Firma della richiesta di accesso…"
  5. "Contatto con il server di identità CIE…"
- Before the card is detected, only the first step is pending. Button: "Annulla".

### 5. Code (success)

- Title: "Digita questo codice sul computer"
- The 4-digit code, very large, monospaced, spaced digits (e.g. "4 8 1 5"), high contrast,
  readable at arm's length.
- Body: "Puoi allontanare la carta. Inserisci il codice nella pagina di accesso aperta sul
  computer per completare l’accesso."
- Button: "Fatto".

### 6. Error

Title "Accesso non riuscito", a message, and buttons "Riprova" (where it makes sense) and
"Annulla". Messages to design for (some are long):

- Not a CIE login QR: "Questo non è un codice QR di accesso CIE."
- Level 2 QR: "Questo codice QR è per un accesso di livello 2 (credenziali CieID). OpenCIE
  supporta solo il livello 3 con la carta: sul computer scegli l’opzione di livello 3 con carta
  e smartphone."
- PIN blocked (no retry): "Il PIN è bloccato dopo troppi tentativi errati. Sbloccalo con il PUK
  (ad esempio con l’app ufficiale CieID o il middleware CIE)."
- Card moved away: "La carta è stata allontanata troppo presto. Riprova e tienila ferma finché
  non compare il codice."
- Not a CIE: "La carta non è una CIE 3.0."
- Phone NFC unsupported: "L’NFC di questo telefono non supporta i comandi estesi richiesti dalla CIE."
- Network: "Impossibile raggiungere il server di identità CIE. Controlla la connessione a internet."
- Expired QR or server refusal: "Il server di identità ha risposto con HTTP 403. Il codice QR
  potrebbe essere scaduto: ricarica la pagina sul computer e inquadralo di nuovo."

## English copy (same order)

- Home: "Log in on your computer with your CIE"
  - "On the computer, choose “Entra con CIE” and continue with the smartphone option (level 3). The page shows a QR code."
  - "Scan the QR code with this app."
  - "Enter your 8-digit CIE PIN and hold the card against the back of the phone."
  - "Type the 4-digit code shown here into the computer."
  - "Scan QR code", "Enter the QR link manually", "NFC is off.", "Turn on NFC"
- Request: "Login request", "Service", "Level 3: CIE card and PIN",
  "Continue only if you just started this login on your own computer.",
  "CIE PIN (8 digits)", "Wrong PIN. Attempts left: 2.", "Continue", "Cancel"
- Hold card: "Hold your CIE against the back of the phone",
  "Keep it still until the code appears. This takes a few seconds and needs an internet connection."
  - Steps: "Connecting to the card…", "Opening a secure channel and checking the PIN…",
    "Reading the authentication certificate…", "Signing the login request…",
    "Contacting the CIE identity server…"
- Code: "Type this code on the computer",
  "You can remove the card. Enter the code on the login page you opened on the computer to complete the login.",
  "Done"
- Error: "Login failed", "Try again", "Cancel"

## Deliverables

Mockups for all six screens in light and dark, including:

- Home with the NFC-off banner;
- PIN screen with "1 attempt left";
- Hold card at step 3 of 5;
- Code screen;
- Error screen with the long level-2 message.

Plus a small component sheet: buttons, step-list item, warning callout, code display.
