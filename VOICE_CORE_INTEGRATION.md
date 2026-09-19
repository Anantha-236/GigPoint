# DhwaniMitra Voice Core — Integration Plan

## Core rule

Whisper is the hearing layer.

It converts:

audio -> text

The VoiceConversationManager is the understanding/dialogue layer.

It performs:

text
-> action
-> product
-> quantity
-> unit
-> missing-slot detection
-> clarification
-> confirmation

VoiceActionExecutor is the write layer.

No database write occurs from Whisper directly.

---

## Required pipeline

```text
Microphone
   ↓
Whisper Tiny Q5_1
   ↓
transcript
   ↓
VoiceConversationManager.handleTranscript()
   ↓
Ask / Answer / Confirm / Error
```

### If Ask

Display and optionally speak `question`.

Start the microphone again.

The next transcript must be passed to the SAME
VoiceConversationManager instance.

Do not create a new manager between turns.

Example:

```text
add rice
```

returns:

```text
Ask("How much Rice?", QUANTITY)
```

Next:

```text
five bags
```

is merged into the saved draft.

### If Confirm

Show the parsed action in a confirmation UI.

Only after the merchant taps Confirm:

```kotlin
VoiceActionExecutor(db).execute(turn.draft)
```

### If Answer

Display/speak the read-only answer.

### If Error

Do not modify inventory.

---

## What "did not understand" means

Do not use a single generic:

"I didn't understand."

Ask specifically for the missing field.

Examples:

Missing PRODUCT:
"Which product?"

Missing QUANTITY:
"How much Rice?"

Generic REMOVE:
"Was it sold, damaged, expired, returned to supplier, or a correction?"

Missing ACTION:
"What should I do with Rice?"

This is substantially better UX than forcing the merchant to repeat the entire sentence.

---

## Integration with Whisper

After the real Whisper activity produces:

```kotlin
val transcript: String
```

replace the old:

```kotlin
processCommand(transcript)
```

with:

```kotlin
val turn =
    conversationManager.handleTranscript(
        transcript
    )

renderVoiceTurn(turn)
```

Keep:

```kotlin
private lateinit var conversationManager:
    VoiceConversationManager

private lateinit var voiceExecutor:
    VoiceActionExecutor
```

Initialize once:

```kotlin
conversationManager =
    VoiceConversationManager(db)

voiceExecutor =
    VoiceActionExecutor(db)
```

---

## renderVoiceTurn skeleton

```kotlin
private fun renderVoiceTurn(
    turn: VoiceTurn
) {
    when (turn) {
        is VoiceTurn.Ask -> {
            tvVoiceResult.text =
                turn.question

            speak(turn.question)

            // Do NOT reset the manager.
            // The next recording supplies only the missing slot.
        }

        is VoiceTurn.Answer -> {
            tvVoiceResult.text =
                turn.text

            speak(turn.text)
        }

        is VoiceTurn.Confirm -> {
            AlertDialog.Builder(this)
                .setTitle(
                    "Confirm inventory change"
                )
                .setMessage(
                    turn.prompt
                )
                .setNegativeButton(
                    "Cancel"
                ) { _, _ ->
                    conversationManager.reset()
                }
                .setPositiveButton(
                    "Confirm"
                ) { _, _ ->
                    val result =
                        voiceExecutor.execute(
                            turn.draft
                        )

                    tvVoiceResult.text =
                        result.second

                    conversationManager.reset()

                    refreshAll()
                }
                .show()
        }

        is VoiceTurn.Error -> {
            tvVoiceResult.text =
                turn.message

            speak(turn.message)

            if (!turn.keepContext) {
                conversationManager.reset()
            }
        }
    }
}
```

---

## TTS

Android TextToSpeech can be used to speak clarification prompts.

Keep TTS optional.

Language mapping:

```text
English -> Locale("en", "IN")
Telugu  -> Locale("te", "IN")
Hindi   -> Locale("hi", "IN")
```

If the device has no local TTS voice for the selected language,
fall back to text-only prompts.

Do not make inventory operation depend on TTS availability.

---

# Current limitation

The current GitHub project still stores:

```text
Product
name
quantity
unit
```

The conversational manager can already ask for missing action/product/quantity,
but it cannot correctly disambiguate product sizes until the local SKU migration is done.

The next schema must represent:

```text
Product
  ↓
ProductVariant
  ↓
PackagingConversion
  ↓
Inventory
```

Then:

```text
"Coke five bottles sold"
```

can return:

```text
Which Coke?

250 ml
500 ml
1 L
2 L
```

and the follow-up:

```text
500 ml
```

completes the pending SALE command.

---

# Core acceptance criteria

The voice system is not complete merely because Whisper transcribes.

It is complete only when all of these hold:

```text
real microphone
→ offline Whisper
→ transcript
→ slot extraction
→ targeted clarification when incomplete
→ context retained across turns
→ ambiguity resolved
→ explicit confirmation for writes
→ atomic local transaction
→ PENDING sync
→ actual Supabase RPC sync later
```
