package com.example.gigpoint

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import com.example.gigpoint.voice.OfflineAudioRecorder
import com.example.gigpoint.voice.WhisperEngine
import com.example.gigpoint.voice.VoiceConversationManager
import com.example.gigpoint.voice.VoiceActionExecutor
import com.example.gigpoint.voice.VoiceTurn

class MainActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private val parser = CommandParser()

    private val voiceWorker =
        Executors.newSingleThreadExecutor()

    private lateinit var audioRecorder:
        OfflineAudioRecorder

    private lateinit var whisperEngine:
        WhisperEngine

    private lateinit var conversationManager:
        VoiceConversationManager

    private lateinit var voiceActionExecutor:
        VoiceActionExecutor

    private var voiceRecording = false

    private var tts: TextToSpeech? = null

    private val microphonePermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startVoiceRecording()
            } else {
                toast(
                    "Microphone permission is required for voice inventory commands."
                )
            }
        }

    private lateinit var tvProductCount: TextView
    private lateinit var tvLowStockCount: TextView
    private lateinit var tvOutOfStockCount: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvVoiceResult: TextView
    private lateinit var tvLanguageHelp: TextView

    private lateinit var inventoryContainer: LinearLayout
    private lateinit var alertsContainer: LinearLayout
    private lateinit var transactionsContainer: LinearLayout

    private lateinit var etCommand: TextInputEditText
    private lateinit var languageSpinner: Spinner
    private lateinit var switchInternet: SwitchMaterial
    private lateinit var btnSync: MaterialButton

    private val dateFormat =
        SimpleDateFormat(
            "dd MMM, hh:mm a",
            Locale.getDefault()
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        AppPreferences(this).apply()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val session = SessionManager(this)

        if (!session.hasSession()) {
            goToLogin()
            return
        }

        db = DatabaseHelper(this)

        val userId = session.userId()
        val profile =
            userId?.let {
                db.getMerchantProfile(it)
            }

        val shop =
            userId?.let {
                db.getShopForOwner(it)
            }

        supportActionBar?.title =
            "DhwaniMitra"

        supportActionBar?.subtitle =
            listOfNotNull(
                profile?.ownerName
                    ?.takeIf(String::isNotBlank),
                shop?.shopName
                    ?.takeIf(String::isNotBlank)
            ).joinToString(" • ")

        bindViews()

        audioRecorder =
            OfflineAudioRecorder()

        whisperEngine =
            WhisperEngine(this)

        conversationManager =
            VoiceConversationManager(db)

        voiceActionExecutor =
            VoiceActionExecutor(db)

        initTts()

        tvVoiceResult.text =
            "Loading offline Whisper model…"

        voiceWorker.execute {
            try {
                whisperEngine.initialize()

                runOnUiThread {
                    tvVoiceResult.text =
                        "Voice assistant ready. Tap the microphone and speak naturally."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvVoiceResult.text =
                        "Whisper model is not ready: ${e.message}"
                }
            }
        }

        setupLanguageSelector(
            profile?.preferredLanguage
                ?: "en"
        )
        setupActions()
        refreshAll()
    }

    override fun onCreateOptionsMenu(
        menu: Menu
    ): Boolean {
        menu.add("Account")
            .setShowAsAction(
                MenuItem.SHOW_AS_ACTION_NEVER
            )

        return true
    }

    override fun onOptionsItemSelected(
        item: MenuItem
    ): Boolean {
        return if (
            item.title == "Account"
        ) {
            startActivity(
                Intent(
                    this,
                    AccountActivity::class.java
                )
            )
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        try {
            if (::audioRecorder.isInitialized && audioRecorder.isRecording()) {
                audioRecorder.stop()
            }
        } catch (_: Exception) {
        }

        if (::whisperEngine.isInitialized) {
            whisperEngine.close()
        }

        tts?.stop()
        tts?.shutdown()

        voiceWorker.shutdownNow()

        if (::db.isInitialized) {
            db.close()
        }

        super.onDestroy()
    }

    private fun goToLogin() {
        startActivity(
            Intent(
                this,
                LoginActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun bindViews() {
        tvProductCount =
            findViewById(R.id.tvProductCount)
        tvLowStockCount =
            findViewById(R.id.tvLowStockCount)
        tvOutOfStockCount =
            findViewById(R.id.tvOutOfStockCount)
        tvPendingCount =
            findViewById(R.id.tvPendingCount)
        tvConnectionStatus =
            findViewById(R.id.tvConnectionStatus)
        tvVoiceResult =
            findViewById(R.id.tvVoiceResult)
        tvLanguageHelp =
            findViewById(R.id.tvLanguageHelp)

        inventoryContainer =
            findViewById(R.id.inventoryContainer)
        alertsContainer =
            findViewById(R.id.alertsContainer)
        transactionsContainer =
            findViewById(R.id.transactionsContainer)

        etCommand =
            findViewById(R.id.etCommand)
        languageSpinner =
            findViewById(R.id.languageSpinner)
        switchInternet =
            findViewById(R.id.switchInternet)
        btnSync =
            findViewById(R.id.btnSync)
    }

    private fun setupLanguageSelector(
        preferred: String
    ) {
        val languages =
            listOf(
                "English",
                "Telugu",
                "Hindi"
            )

        languageSpinner.adapter =
            ArrayAdapter(
                this,
                android.R.layout
                    .simple_spinner_dropdown_item,
                languages
            )

        languageSpinner.setSelection(
            when (preferred) {
                "te" -> 1
                "hi" -> 2
                else -> 0
            }
        )

        languageSpinner
            .setOnItemSelectedListener(
                SimpleItemSelectedListener {
                    updateLanguageHelp(
                        languageSpinner
                            .selectedItem
                            .toString()
                    )
                }
            )

        updateLanguageHelp(
            languageSpinner
                .selectedItem
                .toString()
        )
    }

    private fun setupActions() {
        findViewById<MaterialButton>(
            R.id.btnAddProduct
        ).setOnClickListener {
            showAddProductDialog()
        }

        findViewById<MaterialButton>(
            R.id.btnStockIn
        ).setOnClickListener {
            showStockDialog("STOCK_IN")
        }

        findViewById<MaterialButton>(
            R.id.btnStockOut
        ).setOnClickListener {
            showStockDialog("STOCK_OUT")
        }

        findViewById<MaterialButton>(
            R.id.btnVoice
        ).setOnClickListener {
            toggleVoiceRecording()
        }

        findViewById<MaterialButton>(
            R.id.btnProcessCommand
        ).setOnClickListener {
            handleVoiceTranscript(
                etCommand.text
                    ?.toString()
                    .orEmpty()
            )
        }

        switchInternet
            .setOnCheckedChangeListener {
                    _,
                    checked ->
                updateConnectionState(
                    checked
                )
            }

        btnSync.setOnClickListener {
            if (!switchInternet.isChecked) {
                toast(
                    "No internet. Pending data remains safely stored on this phone."
                )
                return@setOnClickListener
            }

            // Current flat inventory schema is still local-only.
            // Real cloud stock sync comes after Product/Variant migration.
            val updated =
                db.markEverythingSynced()

            toast(
                "Prototype sync flag updated for $updated local record(s)."
            )

            refreshAll()
        }

        updateConnectionState(false)
    }

    private fun updateLanguageHelp(
        language: String
    ) {
        tvLanguageHelp.text =
            when (language) {
                "Telugu" ->
                    "Try: \"Rice five bags add cheyyi\" or \"Rice stock entha undi?\""

                "Hindi" ->
                    "Try: \"Rice five bags add karo\" or \"Rice stock kitna hai?\""

                else ->
                    "Try: \"Add 5 bags of rice\" or \"How much rice is available?\""
            }
    }

    private fun updateConnectionState(
        online: Boolean
    ) {
        if (online) {
            tvConnectionStatus.text =
                "ONLINE"
            tvConnectionStatus
                .setTextColor(
                    getColor(
                        R.color.success
                    )
                )
            btnSync.isEnabled = true
        } else {
            tvConnectionStatus.text =
                "OFFLINE - saved on device"
            tvConnectionStatus
                .setTextColor(
                    getColor(
                        R.color.warning
                    )
                )
            btnSync.isEnabled = false
        }
    }


    private fun initTts() {
        tts =
            TextToSpeech(this) { status ->
                if (
                    status ==
                    TextToSpeech.SUCCESS
                ) {
                    updateTtsLanguage()
                }
            }
    }

    private fun updateTtsLanguage() {
        val locale =
            when (
                languageSpinner.selectedItem
                    ?.toString()
            ) {
                "Telugu" ->
                    Locale("te", "IN")

                "Hindi" ->
                    Locale("hi", "IN")

                else ->
                    Locale("en", "IN")
            }

        tts?.language = locale
    }

    private fun speak(text: String) {
        updateTtsLanguage()

        val engine = tts ?: return

        if (
            engine.isLanguageAvailable(
                engine.language
            ) >= TextToSpeech.LANG_AVAILABLE
        ) {
            engine.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "dhwani_mitra_voice"
            )
        }
    }

    private fun toggleVoiceRecording() {
        if (voiceRecording) {
            stopVoiceRecording()
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            microphonePermission.launch(
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
            ).text =
                "STOP & UNDERSTAND"

            tvVoiceResult.text =
                "Listening… Speak one short inventory command."

        } catch (e: Exception) {
            tvVoiceResult.text =
                e.message ?:
                "Could not start microphone."
        }
    }

    private fun stopVoiceRecording() {
        voiceRecording = false

        findViewById<MaterialButton>(
            R.id.btnVoice
        ).text =
            "TAP TO SPEAK"

        val audio =
            audioRecorder.stop()

        if (
            !audioRecorder
                .looksLikeUsefulAudio(audio)
        ) {
            val message =
                "I did not hear enough speech. Please try again closer to the phone."

            tvVoiceResult.text =
                message

            speak(message)

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

        voiceWorker.execute {
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

                    handleVoiceTranscript(
                        transcript
                    )
                }

            } catch (e: Exception) {
                runOnUiThread {
                    val message =
                        "Voice processing failed: ${e.message}"

                    tvVoiceResult.text =
                        message

                    speak(message)
                }
            }
        }
    }

    private fun handleVoiceTranscript(
        transcript: String
    ) {
        if (transcript.isBlank()) {
            val message =
                "I did not understand any words. Please say it again."

            tvVoiceResult.text =
                message

            speak(message)

            return
        }

        val turn =
            conversationManager
                .handleTranscript(
                    transcript
                )

        renderVoiceTurn(turn)
    }

    private fun renderVoiceTurn(
        turn: VoiceTurn
    ) {
        when (turn) {
            is VoiceTurn.Ask -> {
                tvVoiceResult.text =
                    "Heard:\n" +
                    etCommand.text
                        ?.toString()
                        .orEmpty() +
                    "\n\nDhwaniMitra:\n" +
                    turn.question

                speak(turn.question)

                if (
                    turn.choices.isNotEmpty()
                ) {
                    AlertDialog.Builder(this)
                        .setTitle(
                            "I need one detail"
                        )
                        .setMessage(
                            turn.question
                        )
                        .setItems(
                            turn.choices
                                .toTypedArray()
                        ) { _, which ->
                            val next =
                                conversationManager
                                    .choose(
                                        turn.choices[
                                            which
                                        ]
                                    )

                            renderVoiceTurn(next)
                        }
                        .setPositiveButton(
                            "Answer by voice"
                        ) { _, _ ->
                            toggleVoiceRecording()
                        }
                        .setNegativeButton(
                            "Cancel"
                        ) { _, _ ->
                            conversationManager
                                .reset()
                        }
                        .show()
                }
            }

            is VoiceTurn.Answer -> {
                tvVoiceResult.text =
                    turn.text

                speak(turn.text)

                conversationManager.reset()
            }

            is VoiceTurn.Confirm -> {
                tvVoiceResult.text =
                    turn.prompt

                speak(turn.prompt)

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
                        conversationManager
                            .reset()
                    }
                    .setPositiveButton(
                        "Confirm"
                    ) { _, _ ->
                        val result =
                            voiceActionExecutor
                                .execute(
                                    turn.draft
                                )

                        tvVoiceResult.text =
                            result.second

                        speak(
                            result.second
                        )

                        conversationManager
                            .reset()

                        if (result.first) {
                            refreshAll()
                        }
                    }
                    .show()
            }

            is VoiceTurn.Error -> {
                tvVoiceResult.text =
                    turn.message

                speak(turn.message)

                if (!turn.keepContext) {
                    conversationManager
                        .reset()
                }
            }
        }
    }

    private fun showVoicePrototypeDialog() {
        val options =
            when (
                languageSpinner
                    .selectedItem
                    .toString()
            ) {
                "Hindi" ->
                    arrayOf(
                        "Rice five bags add karo",
                        "Sugar do kg nikalo",
                        "Rice stock kitna hai?",
                        "Low stock items batao"
                    )

                "English" ->
                    arrayOf(
                        "Add 5 bags of rice",
                        "Remove 2 kg sugar",
                        "How much rice is available?",
                        "Show low stock items"
                    )

                else ->
                    arrayOf(
                        "Rice five bags add cheyyi",
                        "Sugar rendu kg teesey",
                        "Rice stock entha undi?",
                        "Low stock items enti?"
                    )
            }

        AlertDialog.Builder(this)
            .setTitle("Voice prototype")
            .setMessage(
                "Whisper Tiny Q5_1 is the next input-layer integration. Choose a sample transcript to test the real parser and local inventory write."
            )
            .setItems(options) {
                    _,
                    which ->
                etCommand.setText(
                    options[which]
                )
                processCommand(
                    options[which]
                )
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    private fun processCommand(
        rawText: String
    ) {
        val parsed =
            parser.parse(
                rawText,
                db.getProducts()
            )

        if (parsed.error != null) {
            tvVoiceResult.text =
                "Not understood\n${parsed.error}"
            return
        }

        when (parsed.intent) {
            CommandIntent.LOW_STOCK -> {
                val low =
                    db.getLowStockProducts()

                tvVoiceResult.text =
                    if (low.isEmpty()) {
                        "No products are currently below their minimum stock level."
                    } else {
                        "Low stock:\n" +
                            low.joinToString(
                                "\n"
                            ) {
                                "${it.name}: ${formatQty(it.quantity)} ${it.unit}"
                            }
                    }
            }

            CommandIntent.CHECK_STOCK -> {
                val product =
                    parsed.product!!

                tvVoiceResult.text =
                    "${product.name}: ${formatQty(product.quantity)} ${product.unit} available."
            }

            CommandIntent.STOCK_IN,
            CommandIntent.STOCK_OUT ->
                showVoiceConfirmation(
                    parsed
                )

            else -> {
                tvVoiceResult.text =
                    "Command not supported in this prototype."
            }
        }
    }

    private fun showVoiceConfirmation(
        command: ParsedCommand
    ) {
        val product =
            command.product ?: return

        val quantity =
            command.quantity ?: return

        val typeText =
            if (
                command.intent ==
                CommandIntent.STOCK_IN
            )
                "STOCK IN"
            else
                "STOCK OUT"

        val summary =
            """
            Transcript:
            ${command.originalText}

            Interpreted as:
            Operation: $typeText
            Product: ${product.name}
            Quantity: ${formatQty(quantity)}
            Unit: ${command.unit ?: product.unit}
            """.trimIndent()

        tvVoiceResult.text =
            summary

        AlertDialog.Builder(this)
            .setTitle(
                "Confirm voice command"
            )
            .setMessage(summary)
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Confirm"
            ) { _, _ ->
                val type =
                    if (
                        command.intent ==
                        CommandIntent.STOCK_IN
                    )
                        "STOCK_IN"
                    else
                        "STOCK_OUT"

                val result =
                    db.adjustStock(
                        productId =
                            product.id,
                        type = type,
                        quantity =
                            quantity,
                        source = "VOICE",
                        transcript =
                            command.originalText
                    )

                toast(result.second)

                if (result.first) {
                    tvVoiceResult.text =
                        "Saved locally.\n${result.second}\nWaiting for cloud sync."
                    refreshAll()
                }
            }
            .show()
    }

    private fun showAddProductDialog() {
        val container =
            dialogContainer()

        val name =
            editText("Product name")

        val unit =
            editText(
                "Unit (kg, bag, carton, box...)"
            )

        val opening =
            numberEditText(
                "Opening quantity"
            )

        val minimum =
            numberEditText(
                "Low-stock threshold"
            )

        container.addView(name)
        container.addView(unit)
        container.addView(opening)
        container.addView(minimum)

        val dialog =
            AlertDialog.Builder(this)
                .setTitle("Add product")
                .setView(container)
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .setPositiveButton(
                    "Save",
                    null
                )
                .create()

        dialog.setOnShowListener {
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                val productName =
                    name.text
                        .toString()
                        .trim()

                val productUnit =
                    unit.text
                        .toString()
                        .trim()

                val openingQty =
                    opening.text
                        .toString()
                        .toDoubleOrNull()
                        ?: 0.0

                val minimumQty =
                    minimum.text
                        .toString()
                        .toDoubleOrNull()
                        ?: 0.0

                if (
                    productName.isBlank() ||
                    productUnit.isBlank()
                ) {
                    toast(
                        "Product name and unit are required."
                    )
                    return@setOnClickListener
                }

                val id =
                    db.addProduct(
                        productName,
                        productUnit,
                        openingQty,
                        minimumQty
                    )

                if (id == -1L) {
                    toast(
                        "A product with that name already exists."
                    )
                    return@setOnClickListener
                }

                dialog.dismiss()

                toast(
                    "Product saved locally."
                )

                refreshAll()
            }
        }

        dialog.show()
    }

    private fun showStockDialog(
        type: String
    ) {
        val products =
            db.getProducts()

        if (products.isEmpty()) {
            toast(
                "Add a product first."
            )
            return
        }

        val container =
            dialogContainer()

        val productSpinner =
            Spinner(this).apply {
                adapter =
                    ArrayAdapter(
                        this@MainActivity,
                        android.R.layout
                            .simple_spinner_dropdown_item,
                        products.map {
                            "${it.name} (${formatQty(it.quantity)} ${it.unit})"
                        }
                    )
            }

        val quantity =
            numberEditText("Quantity")

        container.addView(
            productSpinner
        )

        container.addView(quantity)

        val title =
            if (type == "STOCK_IN")
                "Stock in"
            else
                "Stock out"

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .setPositiveButton(
                    "Save",
                    null
                )
                .create()

        dialog.setOnShowListener {
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                val selectedProduct =
                    products[
                        productSpinner
                            .selectedItemPosition
                    ]

                val qty =
                    quantity.text
                        .toString()
                        .toDoubleOrNull()

                if (
                    qty == null ||
                    qty <= 0
                ) {
                    toast(
                        "Enter a valid quantity."
                    )
                    return@setOnClickListener
                }

                val result =
                    db.adjustStock(
                        productId =
                            selectedProduct.id,
                        type = type,
                        quantity = qty,
                        source = "MANUAL"
                    )

                toast(result.second)

                if (result.first) {
                    dialog.dismiss()
                    refreshAll()
                }
            }
        }

        dialog.show()
    }

    private fun refreshAll() {
        val stats =
            db.getStats()

        tvProductCount.text =
            stats.totalProducts.toString()

        tvLowStockCount.text =
            stats.lowStock.toString()

        tvOutOfStockCount.text =
            stats.outOfStock.toString()

        tvPendingCount.text =
            stats.pendingSync.toString()

        renderInventory()
        renderAlerts()
        renderTransactions()
    }

    private fun renderInventory() {
        inventoryContainer
            .removeAllViews()

        val inflater =
            LayoutInflater.from(this)

        db.getProducts().forEach {
                product ->

            val row =
                inflater.inflate(
                    R.layout.item_product,
                    inventoryContainer,
                    false
                )

            row.findViewById<TextView>(
                R.id.tvItemProductName
            ).text =
                product.name

            row.findViewById<TextView>(
                R.id.tvItemProductQuantity
            ).text =
                "${formatQty(product.quantity)} ${product.unit}"

            row.findViewById<TextView>(
                R.id.tvItemProductStatus
            ).text =
                when {
                    product.quantity <= 0 ->
                        "OUT OF STOCK"

                    product.quantity <=
                        product.minimumStock ->
                        "LOW - minimum ${formatQty(product.minimumStock)} ${product.unit}"

                    else ->
                        "In stock"
                }

            val sync =
                row.findViewById<TextView>(
                    R.id.tvItemProductSync
                )

            sync.text =
                product.syncStatus

            sync.setTextColor(
                getColor(
                    if (
                        product.syncStatus ==
                        DatabaseHelper.SYNC_SYNCED
                    )
                        R.color.success
                    else
                        R.color.warning
                )
            )

            inventoryContainer
                .addView(row)
        }
    }

    private fun renderAlerts() {
        alertsContainer
            .removeAllViews()

        val inflater =
            LayoutInflater.from(this)

        val low =
            db.getLowStockProducts()

        if (low.isEmpty()) {
            alertsContainer.addView(
                TextView(this).apply {
                    text =
                        "No low-stock alerts."
                    setTextColor(
                        getColor(
                            R.color.text_secondary
                        )
                    )
                    setPadding(
                        8,
                        12,
                        8,
                        12
                    )
                }
            )
            return
        }

        low.forEach { product ->
            val row =
                inflater.inflate(
                    R.layout.item_alert,
                    alertsContainer,
                    false
                )

            row.findViewById<TextView>(
                R.id.tvAlertTitle
            ).text =
                product.name

            row.findViewById<TextView>(
                R.id.tvAlertDetail
            ).text =
                "Current: ${formatQty(product.quantity)} ${product.unit}  |  " +
                "Minimum: ${formatQty(product.minimumStock)} ${product.unit}"

            alertsContainer.addView(row)
        }
    }

    private fun renderTransactions() {
        transactionsContainer
            .removeAllViews()

        val inflater =
            LayoutInflater.from(this)

        val transactions =
            db.getTransactions()

        if (transactions.isEmpty()) {
            transactionsContainer
                .addView(
                    TextView(this).apply {
                        text =
                            "No transactions yet."
                        setTextColor(
                            getColor(
                                R.color.text_secondary
                            )
                        )
                        setPadding(
                            8,
                            12,
                            8,
                            12
                        )
                    }
                )
            return
        }

        transactions.forEach {
                transaction ->

            val row =
                inflater.inflate(
                    R.layout.item_transaction,
                    transactionsContainer,
                    false
                )

            val symbol =
                if (
                    transaction.type ==
                    "STOCK_IN"
                )
                    "+"
                else
                    "-"

            row.findViewById<TextView>(
                R.id.tvTxTitle
            ).text =
                "$symbol${formatQty(transaction.quantity)} ${transaction.unit}  ${transaction.productName}"

            row.findViewById<TextView>(
                R.id.tvTxMeta
            ).text =
                "${transaction.source}  |  ${
                    dateFormat.format(
                        Date(
                            transaction.createdAt
                        )
                    )
                }"

            val sync =
                row.findViewById<TextView>(
                    R.id.tvTxSync
                )

            sync.text =
                transaction.syncStatus

            sync.setTextColor(
                getColor(
                    if (
                        transaction.syncStatus ==
                        DatabaseHelper.SYNC_SYNCED
                    )
                        R.color.success
                    else
                        R.color.warning
                )
            )

            transactionsContainer
                .addView(row)
        }
    }

    private fun dialogContainer():
        LinearLayout =
        LinearLayout(this).apply {
            orientation =
                LinearLayout.VERTICAL
            setPadding(
                dp(24),
                dp(8),
                dp(24),
                0
            )
        }

    private fun editText(
        hint: String
    ): EditText =
        EditText(this).apply {
            this.hint = hint
            setSingleLine(true)
        }

    private fun numberEditText(
        hint: String
    ): EditText =
        EditText(this).apply {
            this.hint = hint
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
        }

    private fun formatQty(
        value: Double
    ): String =
        if (value % 1.0 == 0.0)
            value.toLong().toString()
        else
            "%.2f".format(value)

    private fun toast(
        message: String
    ) =
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()

    private fun dp(
        value: Int
    ): Int =
        (
            value *
            resources.displayMetrics.density
        ).toInt()
}

class SimpleItemSelectedListener(
    private val onSelected: () -> Unit
) :
    android.widget.AdapterView
        .OnItemSelectedListener {

    override fun onItemSelected(
        parent:
            android.widget.AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) =
        onSelected()

    override fun onNothingSelected(
        parent:
            android.widget.AdapterView<*>?
    ) = Unit
}
