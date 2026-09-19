# DhwaniMitra — Offline Whisper Tiny Q5_1

## Architecture

```text
Microphone
   ↓
16 kHz mono PCM
   ↓
Whisper Tiny Multilingual Q5_1
   ↓
Transcript
   ↓
Deterministic inventory parser
   ↓
Validation
   ↓
Explicit confirmation for writes
   ↓
Local SQLite / Room
   ↓
PENDING cloud sync
```

Whisper performs speech-to-text only. It never writes inventory.

---

## 1. Android tools

Android Studio -> SDK Manager -> SDK Tools

Install:

- NDK (Side by side), revision 25.2.9519653
- CMake

The official whisper.cpp Android library currently uses NDK 25.2.9519653.

---

## 2. Add whisper.cpp

From Android project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\add-whisper-cpp.ps1
```

This creates:

```text
third_party/
  whisper.cpp/
```

Because it is a Git submodule, the exact commit is recorded in your project.

---

## 3. Put the model inside the app

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\download-whisper-model.ps1
```

Result:

```text
app/src/main/assets/models/
  ggml-tiny-q5_1.bin
```

The script checks the model SHA-256.

That file is bundled with the app; transcription needs no network request.

---

## 4. Gradle

Replace your current `app/build.gradle.kts` with the contents of:

`app/build.gradle.kts.REPLACEMENT`

or merge the NDK/CMake sections manually.

Then Android Studio:

```text
Sync Project with Gradle Files
```

---

## 5. Manifest

Your existing project already contains:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Keep it.

Android also requires runtime permission; the MainActivity patch handles it.

---

## 6. MainActivity

Follow:

`MAIN_ACTIVITY_PATCH.md`

This changes the existing prototype voice button from sample text into:

```text
Tap
↓
record
↓
Tap Stop
↓
Whisper
↓
real transcript
↓
existing command parser
↓
confirmation
↓
local database
```

---

# Offline behavior

After installation, these components are all local:

- model file
- C/C++ inference runtime
- microphone capture
- command parser
- SQLite/Room database

So this still works with airplane mode enabled:

```text
"Rice five bags add cheyyi"
```

The cloud backend is not required for transcription or the local inventory write.

When connectivity returns, pending local transactions can be synchronized.

---

# What voice should be allowed to do

## Read-only — execute immediately

```text
Check stock
Show low stock
```

These cannot corrupt data.

## Write operations — always require confirmation

```text
Stock in
Sale / stock out
Set exact stock
Change reorder level
Delete product
```

## Transaction history — never delete by voice

Transactions are the audit log.

If the merchant made a mistake:

```text
wrong sale
↓
create correction / adjustment
```

Do not erase the original transaction.

---

# Product deletion rule

The included MVP action service only physically deletes a product when it has no transaction history.

If history exists, deletion is refused.

During the Product/Variant migration, replace hard deletion with:

```text
active = false
deleted_at = timestamp
```

That matches the cloud backend already designed for DhwaniMitra.

---

# SKU / variant limitation

The current local prototype still has one flat product row:

```text
Coca-Cola
```

That is NOT sufficient for production voice commands.

The proper model is:

```text
Coca-Cola
  ├─ 250 ml can
  ├─ 500 ml bottle
  ├─ 1 L bottle
  └─ 2 L bottle
```

After local SKU migration:

```text
"Coke five bottles sold"
```

must be rejected as ambiguous if several Coke bottle sizes exist.

The app should ask:

```text
Which one?

250 ml
500 ml
1 L
2 L
```

Never let Whisper guess the SKU.

---

# Language handling

Use the merchant's selected base language:

```text
English -> en
Telugu  -> te
Hindi   -> hi
```

For code-switched speech such as:

```text
Rice rendu bags add cheyyi
```

use Telugu (`te`) because Telugu is the sentence's base language.

Very short commands do not always provide enough speech for reliable automatic language detection.

---

# Performance constraints

The model is small enough for on-device use, but inference speed depends on CPU.

The integration:

- uses arm64-v8a only initially
- compiles native Whisper in Release/O3 even for Android debug
- caps inference threads at 4
- limits one command recording to 10 seconds
- loads the model once per activity lifetime
- uses push-to-talk instead of continuous listening

This is deliberate to control RAM, heat, and battery use.

---

# Important limitations

Whisper Tiny Q5_1 can mishear:

- numbers: 15 vs 50
- brand names
- local product names
- units
- Telugu/English or Hindi/English code switching
- speech in a noisy shop
- distant microphones

Tiny is a transcription model, not an inventory reasoning model.

Therefore:

```text
Whisper transcript
      ↓
parser
      ↓
product / variant validation
      ↓
quantity / unit validation
      ↓
confirmation
      ↓
database
```

must remain mandatory for writes.

---

# Test matrix

At minimum test:

| Spoken command | Expected |
|---|---|
| Add five bags rice | Stock in +5 |
| Rice rendu bags add cheyyi | Stock in +2 |
| Sugar do kg nikalo | Stock out -2 |
| Rice stock entha undi | Read current stock |
| Show low stock items | Read low-stock list |
| Set Rice stock 20 | Set exact inventory to 20 through adjustment |
| Set Rice minimum 5 | Change reorder level |
| Delete product Rice | Require confirmation; refuse if history exists |

Repeat tests:

- quiet room
- fan noise
- normal shop noise
- phone 20 cm away
- phone 50 cm away

For writes, field correctness matters more than perfect transcription:

- action
- product/SKU
- quantity
- unit

---

# Recommended next step after Whisper

Do NOT add more AI first.

Migrate the local data model to:

```text
Product
→ ProductVariant/SKU
→ PackagingConversion
→ Inventory
→ immutable StockTransaction
```

Then connect WorkManager to the backend `apply_inventory_transaction()` RPC.

That is when voice + offline + cloud inventory becomes end-to-end correct.
