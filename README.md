# 100X Cloud Android VM Master

Mobile-first Cloud Android Virtual Machine Management System built with **Kotlin + Jetpack Compose** (Android Admin APK), **Firebase Authentication + Cloud Firestore**, a companion **Node.js VM Host Gateway**, and a **React + Vite + Tailwind CSS Web Admin Dashboard**.

## Architecture Overview

1. **Android Admin APK (`/app`)**: Launches directly into **Local Operator Mode** (no Gmail/Google login required) backed by an on-device **Room (SQLite)** database, with optional **Google Cloud Sync** (`CredentialManager` + Firestore) in **Host & Logs**. Includes honest 100-slot (`VM-001`–`VM-100`) matrix & list views, device grouping, URL dispatch (`OPEN SELECTED` / `OPEN ALL`), per-VM APK & isolated session tracking, authorized WebRTC remote screen viewer, DataStore-backed local gateway token storage, and immutable local/cloud audit logs.
2. **Firebase Security & Schema (`/firebase-blueprint.json`, `/firestore.rules`, `/firebase.json`)**: Zero-trust per-admin tenant isolation under `/users/{uid}/...` with strict schema validation, server timestamps, terminal-state command locking, and append-only audit logs when Cloud Sync is enabled.
3. **Companion Node.js Host Gateway (`/companion-server`)**: Authenticated Express + WebRTC signaling bridge designed to run on Linux KVM cloud nodes hosting Android Emulators (`adb` / `emulator`).
4. **Companion React Web Dashboard (`/companion-web`)**: React + Vite + Tailwind CSS dark admin dashboard launching directly in Local Operator Mode (`localStorage` + direct HTTPS gateway) with optional Google Cloud Sync.

---

## Firebase Setup

- **Android App**: Already provisioned via AI Studio (`app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` using named Firestore database ID `ai-studio-android-vmmaster-4e07d6e9-135d-4676-9922-8e5572a0f7b8`).
- **Web Dashboard**: Copy `companion-web/.env.example` to `companion-web/.env` and fill in your Firebase Web App config (`VITE_FIREBASE_*`).

---

## Companion Node.js Host Gateway Setup

```bash
cd companion-server
cp .env.example .env
# Edit .env with a strong GATEWAY_BEARER_TOKEN and HTTPS reverse proxy (Caddy/Nginx)
npm install
npm start
```

Configure the HTTPS URL and Bearer Token inside the Android app under **Host & Logs**.

---

## Companion React + Vite Web Dashboard Setup

```bash
cd companion-web
cp .env.example .env
npm install
npm run dev
```

---

## Free-Tier Limitations & Scaling to 100 VMs

- **Control Plane (Free-Tier Compatible)**: Firebase Authentication, Firestore Spark (50K reads / 20K writes per day), and static React hosting on Firebase Hosting / Vercel fit free tiers during setup and moderate fleet administration.
- **Compute Plane (Requires Paid Linux KVM Infrastructure)**: Running 100 concurrent Android Emulators requires ~200–400 GB RAM, nested/bare-metal KVM virtualization, and multi-node horizontal sharding (e.g., 5–10 Linux KVM servers running 10–20 emulators each). Free cloud tiers cannot run 100 simultaneous Android VMs.
- **Migration Path**: Start with 1–5 real emulators on a single Linux KVM node using `companion-server`, then assign additional node HTTPS URLs per VM slot (`VM-001`–`VM-100`).

---

## Testing Instructions

### 1. Tier 1 Firestore Security Rules Tests (Node.js)
```bash
firebase emulators:start --only firestore,auth --project demo-no-project
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

### 2. Tier 2 Android Robolectric Repository & Rule Tests
```bash
gradle :app:testDebugUnitTest --tests '*Rule*'
```
