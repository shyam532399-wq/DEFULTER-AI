# Maya AI HUD & GitHub Skill Store Integration (`HunterisLive-1/maya-ui`)

Yeh upgrade **`HunterisLive-1/maya-ui`** ke saare futuristic HUD features — reactive AI Orb, neon mic/speaker frequency visualizer, live CPU/RAM/Temperature/Network speed monitoring, animated Quantum/Holographic backgrounds, aur draggable floating Mini-HUD — ko seedha **Maya** Android app aur voice agent me jodta hai, saath hi GitHub se live Maya skills import karne ka store bhi deta hai.

### User Review & Critical Decisions

> [!IMPORTANT]
> Niche diye gaye design aur integration decisions aapke diye hue **`HunterisLive-1/maya-ui`** repository aur Maya ke existing architecture ke hisaab se set kiye gaye hain. Agar aap sabhi defaults se sehmat hain to **Proceed** dabayein.

- **Confirmed Repository Scope (`HunterisLive-1/maya-ui` + Maya Skills Ecosystem)**: `maya-ui` ke har core capability — **Centerpiece Reactive AI Orb**, **Neon Audio/Mic Spectrum Visualizer**, **Live System Monitoring HUD (CPU, RAM, Temp, Net ↓/↑, Render FPS)**, **Holographic/Quantum Animated Backgrounds + Sci-Fi Corner Accents**, aur **Draggable Floating HUD Overlay** — ko 100% Maya ke andar add kiya jayega, bina purane 9-page Master Prompt ke kisi bhi feature ko hataye.
- **Recommended Integration Layout (Native Cockpit Merge + Draggable Overlay)**: Alag kachra tab banane ke bajaye **Quantum HUD Deck** seedha **Maya Cockpit** ke upar collapsible/expandable centerpiece ke roop me rahega, aur ek **Draggable Floating Mini-HUD** (`ESC`/Close button ke saath) poore app me kahin bhi drag kiya ja sakega.
- **Recommended GitHub Skill Store Sync**: Kyunki aapne *"sari skill add karna hai"* kaha hai, isliye HUD capabilities ko Maya ke **Voice/Agent Actions** me jodne ke saath-saath **Skills** tab me ek **GitHub Skill Store & Repo Importer** bhi milega jo `HunterisLive-1` ke Maya skill playbooks (`SKILL.md`) aur kisi bhi public GitHub repo se skills fetch karke (Boss ke confirmation ke baad) Firestore me install kar sakega.
- **Zero Extra API Keys Required**: Saare system metrics Android ke real on-device hardware APIs se aayenge aur AI routing Maya ke existing **Firebase AI Logic (`gemini-3.8-flash`)** aur **Cloud Firestore** par chalegi.

---

### 1. Overview & Core Concept

- **What It Does**: Maya ke conversational cockpit ko ek high-tech, 60 FPS **Sci-Fi AI HUD** me upgrade karta hai jisme Maya bolte aur sunte waqt live neon audio waves aur reactive quantum orb dikhati hai, phone ki real-time health (CPU load, RAM, battery temperature, live upload/download speed, FPS) monitor karti hai, aur GitHub se naye skill playbooks sync karti hai.
- **Target Audience / Persona**: **"Boss"** — power user jo chahte hain ki unki Hinglish voice assistant sirf chat box na lage, balki `maya-ui` jaisa zinda, futuristic cybernetic command center dikhe aur kaam kare.
- **Key Value**:
  - **Visual & Audio Presence**: Mic dB aur Maya ki awaaz ke saath real-time react karne wala **AI Orb** aur **Neon Frequency Spectrum**.
  - **Live Hardware Telemetry**: Voice ya ek nazar me phone ka live **CPU %, RAM GB, Battery/Device Temp (°C), Network ↓/↑ KB/s–MB/s**, aur **Render FPS** dekhna.
  - **Expandable Skill Ecosystem**: `maya-ui` ke built-in HUD skills + GitHub repository URL se naye Hinglish `SKILL.md` playbooks seedha Maya me install karne ki suvidha.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Cockpit Quantum HUD Mode**: Maya tab kholte hi Boss upar **HUD Toggle** se compact header aur full **Maya AI HUD** ke beech switch kar sakte hain — jahan animated Quantum/Holographic grid ke beech **Reactive AI Orb**, **Neon Mic/Speaker Waveform**, aur **Color-Shifting System Gauges** live chalte hain.
  2. **Voice Telemetry & HUD Control**: Boss bol sakte hain — *"Maya, system status aur network speed batao"*, *"Phone kitna garam hai?"*, *"Quantum HUD on karo"*, ya *"Floating HUD dikhao"* — Maya 1–2 crisp Hinglish lines me live stats batayegi aur zaroorat padne par poori diagnostic report **Maya Board** par save kar degi.
  3. **Draggable Floating Mini-HUD**: Boss **Float HUD** button on karke ek sleek glowing cyber-pill ko screen par kahin bhi ungli se drag karke rakh sakte hain (Board, Skills, ya Guardian tab dekhte hue bhi live Orb pulse, Net speed `↓/↑`, Temp, aur instant Mic/Close `×` control milega).
  4. **GitHub Skill Store & Repo Sync**: **Skills** tab me Boss ek tap me **Maya HUD Built-in Skills Pack** activate kar sakte hain ya koi bhi GitHub repo URL (default pre-filled `HunterisLive-1` Maya skill catalog / repo) daal kar available playbooks preview aur **Confirm & Install** kar sakte hain.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Cyber-Holographic Obsidian HUD** — deep space obsidian canvas ke upar subtle quantum particle grid, multi-layer color-shifting glow borders, aur sharp tactical L-bracket corner accents (`┌ ┐ └ ┘`).
  - *Color Palette & Mood*:
    - Persona-reactive primary auras: **Maya** (Warm Coral `#FF6B4A` + Solar Amber `#FFB547`), **Friday** (Arc Cyan `#00E5FF` + Sapphire `#2979FF`), **Venom** (Symbiote Crimson `#FF2E54` + Abyssal Violet `#9D4EDD`).
    - Dynamic Telemetry Status Progression: **Healthy/Cool** (`#10B981` Emerald, `<60%`), **Elevated/Warm** (`#F59E0B` Amber, `60%–82%`), aur **Critical/Hot** (`#EF4444` Ruby, `>82%` ya `>40°C`).
  - *Typography & Hierarchy*:
    - **Display & HUD Headers**: `Space Grotesk` (bold geometric tech presence).
    - **Conversational Hinglish Copy**: `Plus Jakarta Sans` (clean high-legibility body text).
    - **Live Telemetry Counters, Net Speeds & Spectrum Labels**: `JetBrains Mono` (`sp`-scaled tabular feel taaki live numbers hilte hue jitter na karein).
  - *Component Styling & Layout*: Edge-to-edge Material 3 layout (`WindowInsets.safeDrawing`), 48dp minimum touch targets, asymmetric HUD cards with glowing corner brackets, aur tablet/foldable (`>=600dp`) par `NavigationRail` ke saath wide two-zone HUD telemetry + chat view.

- **Interactive Feedback & Motion**:
  - **60 FPS Choreographer/Canvas Animations**: Multi-ring orbital rotation aur plasma core scaling jo `IDLE` (calm breathing), `LISTENING` (mic RMS-reactive spikes), `THINKING` (fast quantum spin), aur `SPEAKING` (TTS cadence wave) me turant badalti hai.
  - **Neon Waveform Bars**: 24-bar gradient spectrum visualizer jo mic amplitude aur speech output par spring physics ke saath react karta hai.
  - **Clean Exit & Docking**: Desktop `maya-ui` ke `ESC` shortcut ki tarah Floating Mini-HUD par instant `×` dismiss button aur edge-clamping drag gesture.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Desktop Python (`pynvml` / `psutil` / `PySide6`) se Native Android Hardware & Compose Canvas Translation**
  - *Chosen Approach*: Desktop NVIDIA GPU (`pynvml`) aur Windows `wmi` calls ke bajaye Android ke authentic, zero-root system APIs use karenge — `ActivityManager.MemoryInfo` (RAM), `/sys/devices/system/cpu/` frequency scaling + `Process.getElapsedCpuTime()` (CPU load), `BatteryManager.EXTRA_TEMPERATURE` + `PowerManager` thermal status (Temperature), `TrafficStats` RX/TX byte deltas (Live Download/Upload speed), aur `Choreographer` frame intervals (Live UI FPS).
  - *Why*: Android phones par NVIDIA `nvml` nahi hota aur बिना root ke `/proc/stat` Android 8+ par restricted hota hai. In official Android APIs se har real phone aur emulator par 100% asli, non-mocked live numbers milte hain bina kisi crash ya permission error ke.
  - *Alternatives Considered*: Fake random numbers dikhana (strictly rejected — Golden Rule 7 "Sach hi bolo" ke khilaaf hai).

- **Decision 2: Background Telemetry & Audio Worker Lifecycle Management**
  - *Chosen Approach*: `maya-ui` ke `workers/` architecture ki tarah Kotlin Coroutines me lifecycle-aware `SystemHudMonitor` aur `MayaVoiceEngine` RMS amplitude `StateFlow` chalayenge jo sirf screen active hone (`WhileSubscribed`) par 1.5s interval pe poll karega.
  - *Why*: UI hamesha butter-smooth 60 FPS rehti hai aur app background me jaane par battery bilkul drain nahi hoti.

- **Decision 3: Durable Skills in Cloud Firestore vs. Instant HUD Visual Preferences Locally**
  - *Chosen Approach*: GitHub se import kiye gaye saare naye skills/playbooks aur diagnostic reports existing zero-trust **Cloud Firestore** (`skills` aur `documents` collections) me save honge (Golden Rule 5 confirmation ke saath), jabki high-frequency HUD toggles (Quantum vs. Holographic background, Floating HUD position/visibility) lightweight local state me rahenge.
  - *Why*: Har drag gesture ya background effect switch par cloud write nahi hoti, jabki Boss ke saare installed skills Google account ke saath har device par सुरक्षित sync rehte hain.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```text
┌───────────────────────────────────────────────────────────────────────────┐
│                     Authenticated Maya Shell (Compose)                    │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │        Draggable Floating Mini-HUD Overlay (Cross-Tab Layer)        │  │
│  │   [Mini Quantum Orb]  [↓ Rx / ↑ Tx Speed]  [RAM / °C]  [Mic]  [×]   │  │
│  └──────────────────────────────────┬──────────────────────────────────┘  │
│                                     │                                     │
│  ┌──────────────────────┐   ┌───────▼────────────────┐   ┌─────────────┐  │
│  │  Maya Cockpit + HUD  │   │  Skills Deck & GitHub  │   │ Maya Board  │  │
│  │  • Holographic Grid  │   │  Skill Store Importer  │   │ & Guardian  │  │
│  │  • Centerpiece Orb   │   │  • Built-in HUD Skills │   │ • Reports   │  │
│  │  • Neon Audio Bars   │   │  • GitHub Repo / Store │   │ • Memories  │  │
│  │  • CPU/RAM/Temp/Net  │   │    SKILL.md Fetcher    │   │ • Security  │  │
│  └──────────┬───────────┘   └───────────┬────────────┘   └──────┬──────┘  │
└─────────────┼───────────────────────────┼───────────────────────┼─────────┘
              │                           │                       │
              ▼                           ▼                       ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                      MayaViewModel (UDF State Holder)                     │
│   • HUD State (effectTheme, hudExpanded, floatingHudVisible, offset)      │
│   • Live Audio Energy Stream (micRmsDb + ttsSpeakingWave -> 24-band UI)   │
│   • Golden Rule 5 Confirmation Gate (Skill Install, Send, Delete, SOS)    │
└──────┬──────────────────────┬─────────────────────────┬───────────────────┘
       │                      │                         │
       ▼                      ▼                         ▼
┌──────────────┐   ┌─────────────────────┐   ┌──────────────────────────────┐
│ SystemHud    │   │ MayaAiBrain & Voice │   │ LiveInfoClient & Firestore   │
│ Monitor      │   │ • gemini-3.8-flash  │   │ • GitHub REST Skill Parser   │
│ • CPU & FPS  │   │ • HUD/System Actions│   │ • Named Cloud Firestore DB   │
│ • RAM & Disk │   │ • Mic RMS + TTS     │   │   (skills, documents, etc.)  │
│ • Temp & Net │   │   Sanitized Speech  │   │                              │
└──────────────┘   └─────────────────────┘   └──────────────────────────────┘
```

- **Data Model & State**:
  - **SystemHudSnapshot**: Immutable live telemetry snapshot containing CPU load percentage & active core clock, RAM used/total GB & percentage, internal storage free GB, battery/device temperature (`°C`) & thermal status label, live network download (`rxKbps`) & upload (`txKbps`) rates, UI frame rate (`fps`), and computed health level (`COOL`, `WARM`, `CRITICAL`).
  - **AudioSpectrumState**: Normalized `0f..1f` amplitude envelope + 24 frequency-styled band levels driven by real `SpeechRecognizer.onRmsChanged(rmsdB)` during voice input and active utterance wave modulation during `TextToSpeech` playback.
  - **HudVisualConfig**: Active background shader/canvas style (`QUANTUM_GRID`, `HOLOGRAPHIC_SCAN`, `MINIMAL`), Cockpit HUD expanded toggle, Floating Mini-HUD visibility, and bounded `(x, y)` drag offset.
  - **GitHubRemoteSkill**: Parsed skill candidate from GitHub (`name`, `trigger`, `description`, `stepsMarkdown`, `sourceRepo`) ready to preview and install into Firestore `SkillPlaybook` (`kind = "skill"`).

- **Interactive Component & State Mapping**:
  - **Centerpiece AI Orb Tap**: Starts voice input if idle (requesting `RECORD_AUDIO` if needed), stops listening if currently recording, or interrupts TTS speech if Maya is speaking.
  - **HUD Effect & Overlay Controls**: Instant chip toggles switch between `Quantum`, `Holographic`, and `Minimal` backgrounds; tapping `Float HUD` mounts the draggable overlay; dragging updates bounded screen coordinates; tapping `×` cleanly closes the floating HUD.
  - **Voice & Text HUD Actions (`GET_SYSTEM_HUD`, `CONTROL_HUD`, `SYNC_GITHUB_SKILLS`)**: Asking Maya for system performance, network speed, temperature, or HUD mode triggers immediate execution through `MayaAiBrain` -> `MayaViewModel`, speaking a concise Hinglish summary and optionally pinning a `dashboard` snapshot to Maya Board.
  - **GitHub Skill Store Flow**: Entering or selecting a GitHub repo (`HunterisLive-1/mobile-maya-skills` or `HunterisLive-1/maya-ui`) fetches real repository metadata and `.md` skill playbooks via OkHttp on `Dispatchers.IO`, displays selectable cards with loading/error states, and routes "Install" through `PendingConfirmation` (`confirmed = true`) before persisting to Firestore (respecting the 25-skill cap).
