package com.example.gigpoint

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.example.gigpoint.actions.VoiceActionExecutor
import com.example.gigpoint.data.DatabaseHelper
import com.example.gigpoint.domain.Product
import com.example.gigpoint.ui.DashboardExperience
import com.example.gigpoint.voice.OfflineAudioRecorder
import com.example.gigpoint.voice.VoiceConversationManager
import com.example.gigpoint.voice.VoiceTurn
import com.example.gigpoint.voice.WhisperEngine
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private enum class ProductFilter {
        ALL,
        LOW,
        OUT
    }

    private lateinit var db: DatabaseHelper

    private val voiceWorker =
        Executors.newSingleThreadExecutor()

    private val cloudWorker =
        Executors.newSingleThreadExecutor()

    private lateinit var audioRecorder:
        OfflineAudioRecorder

    private lateinit var whisperEngine:
        WhisperEngine

    private lateinit var conversationManager:
        VoiceConversationManager

    private lateinit var voiceActionExecutor:
        VoiceActionExecutor

    private var voiceRecording =
        false

    private var tts:
        TextToSpeech? =
        null

    private var productFilter =
        ProductFilter.ALL

    private lateinit var tvProductCount:
        TextView

    private lateinit var tvLowStockCount:
        TextView

    private lateinit var tvOutOfStockCount:
        TextView

    private lateinit var tvPendingCount:
        TextView

    private lateinit var tvConnectionStatus:
        TextView

    private lateinit var tvVoiceResult:
        TextView

    private lateinit var tvLanguageHelp:
        TextView

    private lateinit var tvTodaySales:
        TextView

    private lateinit var tvTodayProfit:
        TextView

    private lateinit var tvTodayBills:
        TextView

    private lateinit var inventoryContainer:
        LinearLayout

    private lateinit var alertsContainer:
        LinearLayout

    private lateinit var transactionsContainer:
        LinearLayout

    private lateinit var etCommand:
        TextInputEditText

    private lateinit var etProductSearch:
        TextInputEditText

    private lateinit var languageSpinner:
        Spinner

    private lateinit var switchInternet:
        SwitchMaterial

    private lateinit var btnSync:
        MaterialButton

    private val dateFormat =
        SimpleDateFormat(
            "dd MMM, hh:mm a",
            Locale.getDefault()
        )

    private val microphonePermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startVoiceRecording()
            } else {
                toast(
                    "Microphone permission is required for voice commands."
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        AppPreferences(this).apply()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val session =
            SessionManager(this)

        if (!session.hasSession()) {
            goToLogin()
            return
        }

        db =
            DatabaseHelper(this)

        val userId =
            session.userId()

        val profile =
            userId?.let {
                db.getMerchantProfile(it)
            }

        val shop =
            userId?.let {
                db.getShopForOwner(it)
            }

        bindViews()

        setupModernHeader(
            ownerName =
                profile?.ownerName,
            shop =
                shop
        )

        applyDashboardExperience(
            shop?.businessType
        )

        setupBottomNavigation()
        setupProductSearchAndFilters()

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
            "Preparing DhwaniMitra voice…"

        voiceWorker.execute {
            try {
                whisperEngine.initialize()

                runOnUiThread {
                    tvVoiceResult.text =
                        "Voice is ready. Speak naturally in English, Telugu, Hindi, or mixed speech."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvVoiceResult.text =
                        "Voice model is not ready: ${
                            e.message ?: "unknown error"
                        }"
                }
            }
        }

        setupLanguageSelector(
            profile?.preferredLanguage
                ?: "en"
        )

        setupActions()
        refreshAll()

        loadHostedDashboard(
            session =
                session,
            shopId =
                shop?.id
        )
    }

    override fun onResume() {
        super.onResume()

        if (::db.isInitialized) {
            refreshAll()
        }
    }

    override fun onDestroy() {
        try {
            if (
                ::audioRecorder.isInitialized &&
                audioRecorder.isRecording()
            ) {
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
        cloudWorker.shutdownNow()

        if (::db.isInitialized) {
            db.close()
        }

        super.onDestroy()
    }

    private fun bindViews() {
        tvProductCount =
            findViewById(
                R.id.tvProductCount
            )

        tvLowStockCount =
            findViewById(
                R.id.tvLowStockCount
            )

        tvOutOfStockCount =
            findViewById(
                R.id.tvOutOfStockCount
            )

        tvPendingCount =
            findViewById(
                R.id.tvPendingCount
            )

        tvConnectionStatus =
            findViewById(
                R.id.tvConnectionStatus
            )

        tvVoiceResult =
            findViewById(
                R.id.tvVoiceResult
            )

        tvLanguageHelp =
            findViewById(
                R.id.tvLanguageHelp
            )

        tvTodaySales =
            findViewById(
                R.id.tvTodaySales
            )

        tvTodayProfit =
            findViewById(
                R.id.tvTodayProfit
            )

        tvTodayBills =
            findViewById(
                R.id.tvTodayBills
            )

        inventoryContainer =
            findViewById(
                R.id.inventoryContainer
            )

        alertsContainer =
            findViewById(
                R.id.alertsContainer
            )

        transactionsContainer =
            findViewById(
                R.id.transactionsContainer
            )

        etCommand =
            findViewById(
                R.id.etCommand
            )

        etProductSearch =
            findViewById(
                R.id.etProductSearch
            )

        languageSpinner =
            findViewById(
                R.id.languageSpinner
            )

        switchInternet =
            findViewById(
                R.id.switchInternet
            )

        btnSync =
            findViewById(
                R.id.btnSync
            )
    }

    private fun setupModernHeader(
        ownerName: String?,
        shop: ShopProfile?
    ) {
        val cleanOwner =
            ownerName
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        val greeting =
            when (
                Calendar.getInstance()
                    .get(
                        Calendar.HOUR_OF_DAY
                    )
            ) {
                in 5..11 ->
                    "Good morning"

                in 12..16 ->
                    "Good afternoon"

                in 17..21 ->
                    "Good evening"

                else ->
                    "Welcome"
            }

        findViewById<TextView>(
            R.id.tvGreeting
        ).text =
            cleanOwner
                ?.let {
                    "$greeting, $it"
                }
                ?: "$greeting to DhwaniMitra"

        findViewById<TextView>(
            R.id.tvShopName
        ).text =
            shop?.shopName
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "Your business"

        findViewById<TextView>(
            R.id.tvShopLocation
        ).text =
            listOfNotNull(
                shop?.city
                    ?.takeIf {
                        it.isNotBlank()
                    },
                shop?.area
                    ?.takeIf {
                        it.isNotBlank()
                    }
            )
                .joinToString(" • ")
                .ifBlank {
                    shop?.businessType
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "Merchant workspace"
                }

        findViewById<TextView>(
            R.id.tvRoleBadge
        ).text =
            "OWNER"

        val accountButton =
            findViewById<TextView>(
                R.id.btnAccount
            )

        accountButton.text =
            cleanOwner
                ?.firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: "M"

        accountButton.setOnClickListener {
            openAccount()
        }
    }

    private fun applyDashboardExperience(
        businessType: String?
    ) {
        val experience =
            DashboardExperience
                .forBusinessType(
                    businessType
                )

        findViewById<TextView>(
            R.id.tvExperienceLabel
        ).text =
            experience.label

        findViewById<TextView>(
            R.id.tvExperienceSubtitle
        ).text =
            experience.subtitle

        findViewById<TextView>(
            R.id.tvAttentionSubtitle
        ).text =
            experience.attentionSubtitle

        findViewById<TextView>(
            R.id.sectionInventory
        ).text =
            experience.productsTitle

        findViewById<TextView>(
            R.id.tvProductsSubtitle
        ).text =
            experience.productsSubtitle

        findViewById<TextView>(
            R.id.tvCompanionPrompt
        ).text =
            experience.companionPrompt

        listOf(
            R.id.tvFocusOne,
            R.id.tvFocusTwo,
            R.id.tvFocusThree
        ).forEachIndexed {
                index,
                viewId ->

            findViewById<TextView>(
                viewId
            ).text =
                experience
                    .focusTags
                    .getOrNull(index)
                    ?: ""
        }
    }

    private fun setupBottomNavigation() {
        val rootScroll =
            findViewById<android.widget.ScrollView>(
                R.id.rootScroll
            )

        fun scrollToView(
            viewId: Int
        ) {
            val target =
                findViewById<View>(
                    viewId
                )

            rootScroll.post {
                rootScroll.smoothScrollTo(
                    0,
                    target.top
                )
            }
        }

        findViewById<View>(
            R.id.btnNavHome
        ).setOnClickListener {
            rootScroll.smoothScrollTo(
                0,
                0
            )
        }

        findViewById<View>(
            R.id.btnNavInventory
        ).setOnClickListener {
            scrollToView(
                R.id.sectionInventory
            )
        }

        findViewById<View>(
            R.id.btnNavVoice
        ).setOnClickListener {
            scrollToView(
                R.id.sectionVoice
            )
        }

        findViewById<View>(
            R.id.btnNavAlerts
        ).setOnClickListener {
            scrollToView(
                R.id.sectionAlerts
            )
        }

        findViewById<View>(
            R.id.btnNavMore
        ).setOnClickListener {
            openAccount()
        }
    }

    private fun setupProductSearchAndFilters() {
        etProductSearch
            .doAfterTextChanged {
                renderInventory()
            }

        findViewById<Chip>(
            R.id.chipAll
        ).setOnClickListener {
            productFilter =
                ProductFilter.ALL
            renderInventory()
        }

        findViewById<Chip>(
            R.id.chipLow
        ).setOnClickListener {
            productFilter =
                ProductFilter.LOW
            renderInventory()
        }

        findViewById<Chip>(
            R.id.chipOut
        ).setOnClickListener {
            productFilter =
                ProductFilter.OUT
            renderInventory()
        }
    }

    private fun openAccount() {
        startActivity(
            Intent(
                this,
                AccountActivity::class.java
            )
        )
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
            showStockDialog(
                "STOCK_IN"
            )
        }

        findViewById<MaterialButton>(
            R.id.btnStockOut
        ).setOnClickListener {
            showStockDialog(
                "STOCK_OUT"
            )
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

        btnSync.setOnClickListener {
            toast(
                "Cloud stock synchronization is not enabled yet. Local records remain unchanged."
            )
        }

        updateConnectionState(
            online = false,
            label = "Checking cloud…"
        )
    }

    private fun loadHostedDashboard(
        session: SessionManager,
        shopId: String?
    ) {
        val token =
            session.accessToken()

        if (
            token.isNullOrBlank() ||
            shopId.isNullOrBlank()
        ) {
            updateConnectionState(
                online = false,
                label = "Local mode"
            )
            return
        }

        cloudWorker.execute {
            try {
                val summary =
                    BackendClient(this)
                        .getDashboardSummary(
                            accessToken =
                                token,
                            shopId =
                                shopId
                        )

                val todaySales =
                    summary.optDouble(
                        "today_sales",
                        0.0
                    )

                runOnUiThread {
                    tvTodaySales.text =
                        formatCurrency(
                            todaySales
                        )

                    // Keep unknown values unknown until the Sales/COGS
                    // data model exists. Never fabricate financial data.
                    tvTodayProfit.text =
                        "—"

                    tvTodayBills.text =
                        "—"

                    findViewById<TextView>(
                        R.id.tvAnalyticsNote
                    ).text =
                        "Live sales connected • Profit and bill count will activate with the Sales module."

                    updateConnectionState(
                        online = true,
                        label = "Cloud connected"
                    )
                }
            } catch (_: Exception) {
                runOnUiThread {
                    findViewById<TextView>(
                        R.id.tvAnalyticsNote
                    ).text =
                        "Showing local inventory • Sales analytics are temporarily unavailable."

                    updateConnectionState(
                        online = false,
                        label = "Local mode"
                    )
                }
            }
        }
    }

    private fun updateLanguageHelp(
        language: String
    ) {
        tvLanguageHelp.text =
            when (language) {
                "Telugu" ->
                    "Voice understands Telugu + English mixed speech."

                "Hindi" ->
                    "Voice understands Hindi + English mixed speech."

                else ->
                    "Voice recognition uses automatic language detection."
            }
    }

    private fun updateConnectionState(
        online: Boolean,
        label: String
    ) {
        switchInternet.isChecked =
            online

        tvConnectionStatus.text =
            label

        tvConnectionStatus
            .setTextColor(
                getColor(
                    if (online) {
                        R.color.success
                    } else {
                        R.color.warning
                    }
                )
            )
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
                languageSpinner
                    .selectedItem
                    ?.toString()
            ) {
                "Telugu" ->
                    Locale.forLanguageTag(
                        "te-IN"
                    )

                "Hindi" ->
                    Locale.forLanguageTag(
                        "hi-IN"
                    )

                else ->
                    Locale.forLanguageTag(
                        "en-IN"
                    )
            }

        tts?.language =
            locale
    }

    private fun speak(
        text: String
    ) {
        updateTtsLanguage()

        val engine =
            tts ?: return

        if (
            engine.isLanguageAvailable(
                engine.language
            ) >=
            TextToSpeech.LANG_AVAILABLE
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
            ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission
                        .RECORD_AUDIO
                ) !=
            PackageManager
                .PERMISSION_GRANTED
        ) {
            microphonePermission.launch(
                Manifest.permission
                    .RECORD_AUDIO
            )
            return
        }

        startVoiceRecording()
    }

    private fun startVoiceRecording() {
        try {
            tts?.stop()
            audioRecorder.start()
            voiceRecording = true

            findViewById<MaterialButton>(
                R.id.btnVoice
            ).text =
                "Listening… tap to finish"

            tvVoiceResult.text =
                "Listening. Speak naturally."
        } catch (e: Exception) {
            tvVoiceResult.text =
                e.message
                    ?: "Could not start microphone."
        }
    }

    private fun stopVoiceRecording() {
        voiceRecording = false

        findViewById<MaterialButton>(
            R.id.btnVoice
        ).text =
            "Talk to DhwaniMitra"

        val audio =
            audioRecorder.stop()

        if (
            !audioRecorder
                .looksLikeUsefulAudio(
                    audio
                )
        ) {
            val message =
                "I did not hear enough speech. Please try again closer to the phone."

            tvVoiceResult.text =
                message
            speak(message)
            return
        }

        tvVoiceResult.text =
            "Understanding…"

        val inventoryPrompt =
            whisperEngine
                .buildInventoryPrompt(
                    db.getProducts()
                        .map {
                            it.name
                        }
                )

        voiceWorker.execute {
            try {
                val transcript =
                    whisperEngine.transcribe(
                        audio,
                        "auto",
                        inventoryPrompt
                    )

                val detectedLanguage =
                    whisperEngine
                        .detectedLanguage()

                runOnUiThread {
                    etCommand.setText(
                        transcript
                    )

                    val detectedLabel =
                        when (
                            detectedLanguage
                        ) {
                            "te" -> "Telugu"
                            "hi" -> "Hindi"
                            "en" -> "English"
                            "" -> "Unknown"
                            else -> detectedLanguage
                        }

                    tvLanguageHelp.text =
                        "Auto / mixed recognition • primary detected: $detectedLabel"

                    handleVoiceTranscript(
                        transcript
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val message =
                        "Voice processing failed: ${
                            e.message ?: "unknown error"
                        }"

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
                    turn.question

                speak(turn.question)

                if (
                    turn.choices
                        .isNotEmpty()
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
                            renderVoiceTurn(
                                conversationManager
                                    .choose(
                                        turn.choices[
                                            which
                                        ]
                                    )
                            )
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

    private fun showAddProductDialog() {
        val container =
            dialogContainer()

        val name =
            editText(
                "Product name"
            )

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
                .setTitle(
                    "Add product"
                )
                .setView(
                    container
                )
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

                try {
                    db.addProduct(
                        name =
                            productName,
                        unit =
                            productUnit,
                        openingQuantity =
                            openingQty,
                        minimumStock =
                            minimumQty
                    )

                    dialog.dismiss()

                    toast(
                        "Product saved locally."
                    )

                    refreshAll()
                } catch (e: Exception) {
                    toast(
                        e.message
                            ?: "Could not save product."
                    )
                }
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
                            "${it.name} (${
                                formatQty(
                                    it.quantity
                                )
                            } ${it.unit})"
                        }
                    )
            }

        val quantity =
            numberEditText(
                "Quantity"
            )

        container.addView(
            productSpinner
        )

        container.addView(
            quantity
        )

        val title =
            if (
                type ==
                "STOCK_IN"
            ) {
                "Stock in"
            } else {
                "Stock out"
            }

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
                        type =
                            type,
                        quantity =
                            qty,
                        source =
                            "MANUAL"
                    )

                toast(
                    result.second
                )

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
            stats.totalProducts
                .toString()

        tvLowStockCount.text =
            stats.lowStock
                .toString()

        tvOutOfStockCount.text =
            stats.outOfStock
                .toString()

        tvPendingCount.text =
            stats.pendingSync
                .toString()

        renderInventory()
        renderAlerts()
        renderTransactions()
    }

    private fun renderInventory() {
        inventoryContainer
            .removeAllViews()

        val query =
            etProductSearch.text
                ?.toString()
                ?.trim()
                ?.lowercase()
                .orEmpty()

        val products =
            db.getProducts()
                .filter {
                    query.isBlank() ||
                        it.name
                            .lowercase()
                            .contains(
                                query
                            ) ||
                        it.brand
                            ?.lowercase()
                            ?.contains(
                                query
                            ) ==
                        true ||
                        it.category
                            ?.lowercase()
                            ?.contains(
                                query
                            ) ==
                        true
                }
                .filter {
                    when (
                        productFilter
                    ) {
                        ProductFilter.ALL ->
                            true

                        ProductFilter.LOW ->
                            it.quantity >
                                0.0 &&
                                it.quantity <=
                                it.minimumStock

                        ProductFilter.OUT ->
                            it.quantity <=
                                0.0
                    }
                }

        if (products.isEmpty()) {
            inventoryContainer.addView(
                emptyState(
                    when {
                        query.isNotBlank() ->
                            "No products match \"$query\"."

                        productFilter ==
                            ProductFilter.LOW ->
                            "No low-stock products."

                        productFilter ==
                            ProductFilter.OUT ->
                            "No out-of-stock products."

                        else ->
                            "No products yet. Add your first product."
                    }
                )
            )
            return
        }

        val inflater =
            LayoutInflater.from(this)

        products.forEach { product ->
            val row =
                inflater.inflate(
                    R.layout.item_product,
                    inventoryContainer,
                    false
                )

            val productImage =
                row.findViewById<ImageView>(
                    R.id.ivItemProductImage
                )

            AssetImageLoader
                .loadProduct(
                    context =
                        this,
                    imageView =
                        productImage,
                    assetName =
                        ProductImageMapper
                            .getAssetName(
                                product.name
                            )
                )

            row.findViewById<TextView>(
                R.id.tvItemProductName
            ).text =
                product.name

            row.findViewById<TextView>(
                R.id.tvItemProductQuantity
            ).text =
                "${formatQty(
                    product.quantity
                )} ${product.unit}"

            val status =
                row.findViewById<TextView>(
                    R.id.tvItemProductStatus
                )

            status.text =
                when {
                    product.quantity <=
                        0.0 ->
                        "Out of stock"

                    product.quantity <=
                        product.minimumStock ->
                        "Low stock • reorder at ${
                            formatQty(
                                product.minimumStock
                            )
                        } ${product.unit}"

                    else ->
                        "Available"
                }

            status.setTextColor(
                getColor(
                    when {
                        product.quantity <=
                            0.0 ->
                            R.color.danger

                        product.quantity <=
                            product.minimumStock ->
                            R.color.warning

                        else ->
                            R.color.success
                    }
                )
            )

            val sync =
                row.findViewById<TextView>(
                    R.id.tvItemProductSync
                )

            sync.text =
                if (
                    product.syncStatus ==
                    DatabaseHelper.SYNC_SYNCED
                ) {
                    "SYNCED"
                } else {
                    "LOCAL"
                }

            sync.setTextColor(
                getColor(
                    if (
                        product.syncStatus ==
                        DatabaseHelper.SYNC_SYNCED
                    ) {
                        R.color.success
                    } else {
                        R.color.warning
                    }
                )
            )

            row.setOnClickListener {
                showProductSnapshot(
                    product
                )
            }

            inventoryContainer
                .addView(
                    row
                )
        }
    }

    private fun showProductSnapshot(
        product: Product
    ) {
        val status =
            when {
                product.quantity <=
                    0.0 ->
                    "Out of stock"

                product.quantity <=
                    product.minimumStock ->
                    "Low stock"

                else ->
                    "Available"
            }

        AlertDialog.Builder(this)
            .setTitle(
                product.name
            )
            .setMessage(
                """
                Availability: $status
                Current stock: ${formatQty(product.quantity)} ${product.unit}
                Reorder level: ${formatQty(product.minimumStock)} ${product.unit}

                Sales, profit, variants and price history will appear here when Product 360 is connected to the Sales and Variant modules.
                """.trimIndent()
            )
            .setPositiveButton(
                "OK",
                null
            )
            .show()
    }

    private fun renderAlerts() {
        alertsContainer
            .removeAllViews()

        val low =
            db.getLowStockProducts()

        if (low.isEmpty()) {
            alertsContainer.addView(
                emptyState(
                    "Nothing urgent in current local stock."
                )
            )
            return
        }

        val inflater =
            LayoutInflater.from(this)

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
                if (
                    product.quantity <=
                    0.0
                ) {
                    "Out of stock • reorder level ${
                        formatQty(
                            product.minimumStock
                        )
                    } ${product.unit}"
                } else {
                    "${formatQty(
                        product.quantity
                    )} ${product.unit} left • reorder at ${
                        formatQty(
                            product.minimumStock
                        )
                    } ${product.unit}"
                }

            alertsContainer
                .addView(
                    row
                )
        }
    }

    private fun renderTransactions() {
        transactionsContainer
            .removeAllViews()

        val transactions =
            db.getTransactions()

        if (transactions.isEmpty()) {
            transactionsContainer.addView(
                emptyState(
                    "No activity yet."
                )
            )
            return
        }

        val inflater =
            LayoutInflater.from(this)

        transactions.forEach { transaction ->
            val row =
                inflater.inflate(
                    R.layout.item_transaction,
                    transactionsContainer,
                    false
                )

            val outbound =
                transaction.type in
                    setOf(
                        "STOCK_OUT",
                        "SALE",
                        "DAMAGE",
                        "EXPIRED",
                        "SUPPLIER_RETURN",
                        "TRANSFER_OUT"
                    )

            val symbol =
                if (outbound) {
                    "−"
                } else {
                    "+"
                }

            row.findViewById<TextView>(
                R.id.tvTxTitle
            ).text =
                "$symbol${
                    formatQty(
                        transaction.quantity
                    )
                } ${transaction.unit} • ${transaction.productName}"

            row.findViewById<TextView>(
                R.id.tvTxMeta
            ).text =
                "${transaction.source} • ${
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
                if (
                    transaction.syncStatus ==
                    DatabaseHelper.SYNC_SYNCED
                ) {
                    "SYNCED"
                } else {
                    "LOCAL"
                }

            sync.setTextColor(
                getColor(
                    if (
                        transaction.syncStatus ==
                        DatabaseHelper.SYNC_SYNCED
                    ) {
                        R.color.success
                    } else {
                        R.color.warning
                    }
                )
            )

            transactionsContainer
                .addView(
                    row
                )
        }
    }

    private fun emptyState(
        message: String
    ): TextView =
        TextView(this).apply {
            text = message

            setTextColor(
                getColor(
                    R.color.text_secondary
                )
            )

            textSize =
                13f

            setPadding(
                dp(4),
                dp(12),
                dp(4),
                dp(16)
            )
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
            this.hint =
                hint
            setSingleLine(true)
        }

    private fun numberEditText(
        hint: String
    ): EditText =
        EditText(this).apply {
            this.hint =
                hint

            inputType =
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL

            setSingleLine(true)
        }

    private fun formatQty(
        value: Double
    ): String =
        if (
            value %
                1.0 ==
            0.0
        ) {
            value
                .toLong()
                .toString()
        } else {
            "%.2f".format(
                value
            )
        }

    private fun formatCurrency(
        value: Double
    ): String =
        NumberFormat
            .getCurrencyInstance(
                Locale.forLanguageTag(
                    "en-IN"
                )
            )
            .format(
                value
            )

    private fun toast(
        message: String
    ) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun dp(
        value: Int
    ): Int =
        (
            value *
                resources
                    .displayMetrics
                    .density
        ).toInt()

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
}

class SimpleItemSelectedListener(
    private val onSelected: () -> Unit
) :
    android.widget.AdapterView
        .OnItemSelectedListener {

    override fun onItemSelected(
        parent:
            android.widget.AdapterView<*>?,
        view:
            View?,
        position:
            Int,
        id:
            Long
    ) {
        onSelected()
    }

    override fun onNothingSelected(
        parent:
            android.widget.AdapterView<*>?
    ) =
        Unit
}
