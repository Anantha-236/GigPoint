# DhwaniMitra Master Core Integration

This bundle consolidates:

- DhwaniMitra branding
- Login / signup
- database-driven merchant setup
- Supabase REST/RPC client
- local cached session/profile
- account settings, logout and account-delete client
- redesigned dashboard resources
- light/dark/system theme foundation
- real microphone capture
- local whisper.cpp JNI/CMake bridge
- Whisper Tiny Multilingual Q5_1 download script
- stateful voice conversation
- targeted clarification for missing slots
- confirmation before every write
- local persistent inventory writes

## Important truth

The current GitHub repository was still the older prototype when this package was produced.
This bundle is intended to replace the piecemeal overlays from earlier steps.

## Apply

Extract over the Android project root.

Then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\add-whisper-cpp.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\download-whisper-model.ps1
```

Edit:

`app/src/main/res/values/supabase.xml`

and put the LOCAL publishable key from:

```powershell
npx supabase status
```

Use:

`http://127.0.0.1:55321`

with:

```powershell
adb reverse tcp:55321 tcp:55321
```

## Android Studio

Install:

- NDK 25.2.9519653
- CMake

Then:

- Sync Gradle
- Rebuild
- Run

## Voice test sequence

Say:

`add rice`

DhwaniMitra should ask:

`How much Rice?`

Tap microphone again and say:

`five bags`

DhwaniMitra should merge both turns and show a confirmation.

Also test:

`remove five sugar`

It must NOT assume a sale.

It should ask:

`Was it sold, damaged, expired, returned to supplier, or a correction?`

## Current remaining architectural gap

The local Android stock database is still the flat v1 Product model.

The cloud backend is already Product -> Variant/SKU -> Packaging -> Inventory.

Before production-complete cloud stock sync, migrate the local database to the same SKU model and replace `markEverythingSynced()` with WorkManager + `apply_inventory_transaction()`.

Do not call the project fully synchronized until that is done.
