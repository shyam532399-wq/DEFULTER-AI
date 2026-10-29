# Maya — Hinglish Personal Voice & Phone Assistant

Maya ek real-action Hinglish voice aur phone assistant hai jo Kotlin, Jetpack Compose (Material 3), Firebase AI Logic, aur Cloud Firestore par bana hai. Maya sirf baatein nahi karti — phone hardware controls, confirmations, multi-step missions, aur live research/business board sab sambhalti hai.

---

## Key Highlights

- **3 Distinct Personas & Moods:** Switch live between **Maya** (warm Hinglish default), **Friday** (crisp tactical), and **Venom** (bold witty) with `Calm`, `Excited`, or `Soft` voice tones.
- **Wake-Word & Listen Modes:** Hands-free `"Hey Maya"` / `"Hello Maya"` / `"Wake up Maya"` loop plus `"Sirf meri suno"` vs `"Sabki suno"` modes.
- **Deterministic Confirmation Gate (Golden Rule #5):** Risky actions (WhatsApp, SMS, Email, Document/Skill delete, Skill save) never execute silently — Maya previews the exact payload and waits for explicit Boss confirmation (`Haan`).
- **2–8 Step Mission Stepper:** Visual step progress with `Undo Last Step` and `Cancel Mission`.
- **Maya Board, Image Studio & Business Suite:**
  - Cloud-synced **Maya Board** for long reports, emails, and deep research (never reading long URLs or walls of text aloud).
  - **Gemini Image Studio** for runtime visual generation and direct MediaStore Gallery export.
  - **Business Suite** for GST/tax breakdowns, unit economics, marketing plans, and pitch drafts.
- **Guardian & Memory Vault:** Persistent Boss preferences, app nicknames, reminders, and an interactive **Touch Guard** alert trap.
- **Real Android Hardware & Live Feeds:** Camera torch, battery telemetry, media volume/ringer, system alarms, calendar events, clipboard, Maps navigation, plus live Open-Meteo weather, CoinGecko crypto, Frankfurter forex, and Hacker News headlines.

---

## Tech Stack

- **UI:** Kotlin + Jetpack Compose (Material 3, adaptive navigation for phones & tablets)
- **AI:** Firebase AI Logic + Firebase App Check
- **Auth & Cloud Persistence:** Google Sign-In (`CredentialManager`) + Cloud Firestore (strict zero-trust `firestore.rules`)
- **Testing:** Node.js `@firebase/rules-unit-testing` + Robolectric JVM & Compose UI Critical User Journey tests

---

## GitHub Workflows (CI/CD)

This repository includes two ready-to-use GitHub Actions workflows inside `.github/workflows/`:

1. **`android-ci.yml` (`Android CI — Build & Test`):**
   - Runs automatically on every `push` and `pull_request` to `main` / `master`.
   - Starts local Firebase Auth & Firestore emulators, runs `firestore.test.js` security rules tests, executes all Robolectric unit & Compose UI tests (`gradle :app:testDebugUnitTest`), and uploads `Maya-debug-apk` as a downloadable build artifact.

2. **`android-release.yml` (`Publish Android Release`):**
   - Triggers automatically when you push a version tag (e.g. `v1.0.0`) **or** manually via **Actions → Publish Android Release → Run workflow**.
   - Publishes a public GitHub Release with downloadable `Maya-<version>-debug.apk` (and signed `release.apk` + `release.aab` if release signing secrets are configured).

---

## Publishing & Secrets Setup Guide

### 1. Push from AI Studio to Public GitHub Repo
1. Top bar me **GitHub / Export** option kholein.
2. Repository visibility **Public** chunein aur push karein.
3. Push hote hi GitHub ke **Actions** tab me `Android CI — Build & Test` apne aap chal jayega.

### 2. Downloadable Public Release Banana (1-Click)
- GitHub repo me **Actions → Publish Android Release → Run workflow** par click karein, version `v1.0.0` dalein, aur run karein.
- Kuch hi minute me repo ke **Releases** section me `Maya-v1.0.0-debug.apk` public download ke liye live ho jayega.

### 3. Optional GitHub Repository Secrets (Signed Play Store / Release Builds ke liye)
Agar aap signed production Release APK aur `.aab` bundle bhi publish karna chahte hain, to GitHub repo ke **Settings → Secrets and variables → Actions** me ye secrets add kar sakte hain:

| Secret Name | Purpose |
| :--- | :--- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded upload keystore (`base64 -w 0 my-upload-key.jks`, key alias `upload`) |
| `STORE_PASSWORD` | Keystore password for `my-upload-key.jks` |
| `KEY_PASSWORD` | Key password for alias `upload` |
| `GOOGLE_SERVICES_JSON` | *(Optional)* Custom `app/google-services.json` content override |

---

## Local Verification Commands

```bash
# Run Firestore Security Rules tests + Robolectric Unit/CUJ tests
npx firebase emulators:exec --only auth,firestore --project demo-no-project \
  "FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 node --test firestore.test.js && gradle :app:testDebugUnitTest"

# Build Debug APK
gradle :app:assembleDebug
```
