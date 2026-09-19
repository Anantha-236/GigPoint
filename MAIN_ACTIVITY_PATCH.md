# MainActivity voice patch

Apply this AFTER adding the files in this ZIP.

## 1. Add imports

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.gigpoint.voice.LocalInventoryActionService
import com.example.gigpoint.voice.OfflineAudioRecorder
import com.example.gigpoint.voice.VoiceInventoryAction
import com.example.gigpoint.voice.VoiceInventoryCommand
import com.example.gigpoint.voice.VoiceInventoryParser
import com.example.gigpoint.voice.WhisperEngine
import java.util.concurrent.Executors
```

## 2. Add fields inside MainActivity

```kotlin
private val voiceExecutor =
    Executors.newSingleThreadExecutor()

private lateinit var audioRecorder:
    OfflineAudioRecorder

private lateinit var whisperEngine:
    WhisperEngine

private val voiceParser =
    VoiceInventoryParser()

private var voiceRecording = false

private val requestMicrophonePermission =
    registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecording()
        } else {
            toast("Microphone permission is required for offline voice commands.")
        }
    }
```

## 3. Initialize in onCreate(), after DatabaseHelper

```kotlin
audioRecorder =
    OfflineAudioRecorder()

whisperEngine =
    WhisperEngine(this)

// Load the 32 MB model once in the background.
// First initialization is expected to take longer than later commands.
voiceExecutor.execute {
    try {
        whisperEngine.initialize()
    } catch (e: Exception) {
        runOnUiThread {
            tvVoiceResult.text =
                "Whisper model failed to load: ${e.message}"
        }
    }
}
```

If `tvVoiceResult` has not been bound yet at this exact position,
move only the `voiceExecutor.execute { ... }` block to after `bindViews()`.

## 4. Replace btnVoice listener

Replace the current:

```kotlin
showVoicePrototypeDialog()
```

with:

```kotlin
toggleVoiceRecording()
```

Full listener:

```kotlin
findViewById<MaterialButton>(
    R.id.btnVoice
).setOnClickListener {
    toggleVoiceRecording()
}
```

## 5. Add these functions

```kotlin
private fun toggleVoiceRecording() {
    if (voiceRecording) {
        stopVoiceRecording()
        return
    }

    if (
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        requestMicrophonePermission.launch(
            Manifest.permission.RECORD_AUDIO
        )
        return
    }

    startVoiceRecording()
}

private fun startVoiceRecording() {
    try {
        audioRecorder.start()
        voiceRecording = true

        findViewById<MaterialButton>(
            R.id.btnVoice
        ).text = "STOP & PROCESS"

        tvVoiceResult.text =
            "Listening… speak one short inventory command."
    } catch (e: Exception) {
        toast(
            e.message ?: "Could not start microphone."
        )
    }
}

private fun stopVoiceRecording() {
    voiceRecording = false

    findViewById<MaterialButton>(
        R.id.btnVoice
    ).text = "TAP TO SPEAK"

    val audio =
        audioRecorder.stop()

    if (
        !audioRecorder.looksLikeUsefulAudio(
            audio
        )
    ) {
        tvVoiceResult.text =
            "I did not hear enough speech. Try again closer to the phone."
        return
    }

    tvVoiceResult.text =
        "Understanding your command offline…"

    val language =
        when (
            languageSpinner.selectedItem
                .toString()
        ) {
            "Telugu" -> "te"
            "Hindi" -> "hi"
            else -> "en"
        }

    voiceExecutor.execute {
        try {
            val transcript =
                whisperEngine.transcribe(
                    audio,
                    language
                )

            runOnUiThread {
                etCommand.setText(
                    transcript
                )

                handleOfflineVoiceTranscript(
                    transcript
                )
            }
        } catch (e: Exception) {
            runOnUiThread {
                tvVoiceResult.text =
                    "Voice processing failed: ${e.message}"
            }
        }
    }
}

private fun handleOfflineVoiceTranscript(
    transcript: String
) {
    if (transcript.isBlank()) {
        tvVoiceResult.text =
            "No speech was recognized."
        return
    }

    val command =
        voiceParser.parse(
            transcript,
            db.getProducts()
        )

    if (command.error != null) {
        tvVoiceResult.text =
            "Heard:\n$transcript\n\n${command.error}"
        return
    }

    when (command.action) {
        VoiceInventoryAction.CHECK_STOCK,
        VoiceInventoryAction.LOW_STOCK,
        VoiceInventoryAction.STOCK_IN,
        VoiceInventoryAction.STOCK_OUT -> {
            // Existing parser/confirmation flow remains the source of truth.
            processCommand(transcript)
        }

        VoiceInventoryAction.SET_STOCK,
        VoiceInventoryAction.SET_REORDER_LEVEL,
        VoiceInventoryAction.DELETE_PRODUCT -> {
            confirmAdvancedVoiceAction(
                command
            )
        }

        VoiceInventoryAction.UNKNOWN -> {
            tvVoiceResult.text =
                "Heard:\n$transcript\n\nI could not safely understand that inventory action."
        }
    }
}

private fun confirmAdvancedVoiceAction(
    command: VoiceInventoryCommand
) {
    val product =
        command.product ?: return

    val actionText =
        when (command.action) {
            VoiceInventoryAction.SET_STOCK ->
                "Set ${product.name} stock to ${command.quantity} ${product.unit}"

            VoiceInventoryAction.SET_REORDER_LEVEL ->
                "Set ${product.name} low-stock level to ${command.quantity} ${product.unit}"

            VoiceInventoryAction.DELETE_PRODUCT ->
                "Delete ${product.name}"

            else ->
                return
        }

    AlertDialog.Builder(this)
        .setTitle("Confirm inventory change")
        .setMessage(
            """
            Heard:
            ${command.originalText}

            Action:
            $actionText
            """.trimIndent()
        )
        .setNegativeButton(
            "Cancel",
            null
        )
        .setPositiveButton(
            "Confirm"
        ) { _, _ ->
            executeAdvancedVoiceAction(
                command
            )
        }
        .show()
}

private fun executeAdvancedVoiceAction(
    command: VoiceInventoryCommand
) {
    val product =
        command.product ?: return

    val actions =
        LocalInventoryActionService(db)

    val result =
        when (command.action) {
            VoiceInventoryAction.SET_STOCK ->
                actions.setExactStock(
                    product,
                    command.quantity ?: return,
                    command.originalText
                )

            VoiceInventoryAction.SET_REORDER_LEVEL ->
                actions.updateReorderLevel(
                    product,
                    command.quantity ?: return
                )

            VoiceInventoryAction.DELETE_PRODUCT ->
                actions.deleteProductIfUnused(
                    product
                )

            else ->
                false to "Unsupported action."
        }

    toast(result.second)

    if (result.first) {
        refreshAll()
    }

    tvVoiceResult.text =
        result.second
}
```

## 6. Update onDestroy()

Before `super.onDestroy()`:

```kotlin
if (::whisperEngine.isInitialized) {
    whisperEngine.close()
}

voiceExecutor.shutdownNow()
```

---

# Voice commands to test

Existing:

```text
Add five bags rice
Rice five bags add cheyyi
Rice five bags add karo

Sell two kg sugar
Sugar rendu kg teesey
Sugar do kg nikalo

How much rice is available?
Rice stock entha undi?
Rice stock kitna hai?

Show low stock
```

New:

```text
Set Rice stock 20
Set Rice minimum 5
Delete product Rice
```

Deletion always requires UI confirmation.

Do NOT support voice deletion of transaction history.
Incorrect transactions should be corrected with an adjustment so the audit trail remains intact.
