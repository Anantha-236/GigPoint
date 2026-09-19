package com.example.gigpoint

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private val parser = CommandParser()

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

    private val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.title = "GigPoint Voice Inventory"

        db = DatabaseHelper(this)

        bindViews()
        setupLanguageSelector()
        setupActions()
        refreshAll()
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    private fun bindViews() {
        tvProductCount = findViewById(R.id.tvProductCount)
        tvLowStockCount = findViewById(R.id.tvLowStockCount)
        tvOutOfStockCount = findViewById(R.id.tvOutOfStockCount)
        tvPendingCount = findViewById(R.id.tvPendingCount)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvVoiceResult = findViewById(R.id.tvVoiceResult)
        tvLanguageHelp = findViewById(R.id.tvLanguageHelp)

        inventoryContainer = findViewById(R.id.inventoryContainer)
        alertsContainer = findViewById(R.id.alertsContainer)
        transactionsContainer = findViewById(R.id.transactionsContainer)

        etCommand = findViewById(R.id.etCommand)
        languageSpinner = findViewById(R.id.languageSpinner)
        switchInternet = findViewById(R.id.switchInternet)
        btnSync = findViewById(R.id.btnSync)
    }

    private fun setupLanguageSelector() {
        val languages = listOf("English", "Telugu", "Hindi")
        languageSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languages
        )

        languageSpinner.setSelection(1)
        languageSpinner.setOnItemSelectedListener(
            SimpleItemSelectedListener {
                updateLanguageHelp(languageSpinner.selectedItem.toString())
            }
        )

        updateLanguageHelp("Telugu")
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.btnAddProduct).setOnClickListener {
            showAddProductDialog()
        }

        findViewById<MaterialButton>(R.id.btnStockIn).setOnClickListener {
            showStockDialog("STOCK_IN")
        }

        findViewById<MaterialButton>(R.id.btnStockOut).setOnClickListener {
            showStockDialog("STOCK_OUT")
        }

        findViewById<MaterialButton>(R.id.btnVoice).setOnClickListener {
            showVoicePrototypeDialog()
        }

        findViewById<MaterialButton>(R.id.btnProcessCommand).setOnClickListener {
            processCommand(etCommand.text?.toString().orEmpty())
        }

        switchInternet.setOnCheckedChangeListener { _, checked ->
            updateConnectionState(checked)
        }

        btnSync.setOnClickListener {
            if (!switchInternet.isChecked) {
                toast("No internet. Pending data remains safely stored on this phone.")
                return@setOnClickListener
            }

            val updated = db.markEverythingSynced()
            toast("Prototype sync complete. $updated local record(s) marked synced.")
            refreshAll()
        }

        updateConnectionState(false)
    }

    private fun updateLanguageHelp(language: String) {
        tvLanguageHelp.text = when (language) {
            "Telugu" ->
                "Try: \"Rice five bags add cheyyi\" or \"Rice stock entha undi?\""
            "Hindi" ->
                "Try: \"Rice five bags add karo\" or \"Rice stock kitna hai?\""
            else ->
                "Try: \"Add 5 bags of rice\" or \"How much rice is available?\""
        }
    }

    private fun updateConnectionState(online: Boolean) {
        if (online) {
            tvConnectionStatus.text = "ONLINE - cloud sync can run"
            tvConnectionStatus.setTextColor(getColor(R.color.success))
            btnSync.isEnabled = true
        } else {
            tvConnectionStatus.text = "OFFLINE - changes stay on this device"
            tvConnectionStatus.setTextColor(getColor(R.color.warning))
            btnSync.isEnabled = false
        }
    }

    private fun showVoicePrototypeDialog() {
        val options = when (languageSpinner.selectedItem.toString()) {
            "Hindi" -> arrayOf(
                "Rice five bags add karo",
                "Sugar do kg nikalo",
                "Rice stock kitna hai?",
                "Low stock items batao"
            )
            "English" -> arrayOf(
                "Add 5 bags of rice",
                "Remove 2 kg sugar",
                "How much rice is available?",
                "Show low stock items"
            )
            else -> arrayOf(
                "Rice five bags add cheyyi",
                "Sugar rendu kg teesey",
                "Rice stock entha undi?",
                "Low stock items enti?"
            )
        }

        AlertDialog.Builder(this)
            .setTitle("Voice prototype")
            .setMessage(
                "Whisper is the next integration step. For now choose a sample transcript. " +
                        "The parser, confirmation, database update and offline persistence are real."
            )
            .setItems(options) { _, which ->
                etCommand.setText(options[which])
                processCommand(options[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processCommand(rawText: String) {
        val parsed = parser.parse(rawText, db.getProducts())

        if (parsed.error != null) {
            tvVoiceResult.text = "Not understood\n${parsed.error}"
            return
        }

        when (parsed.intent) {
            CommandIntent.LOW_STOCK -> {
                val low = db.getLowStockProducts()
                tvVoiceResult.text =
                    if (low.isEmpty()) {
                        "No products are currently below their minimum stock level."
                    } else {
                        "Low stock:\n" + low.joinToString("\n") {
                            "${it.name}: ${formatQty(it.quantity)} ${it.unit}"
                        }
                    }
            }

            CommandIntent.CHECK_STOCK -> {
                val product = parsed.product!!
                tvVoiceResult.text =
                    "${product.name}: ${formatQty(product.quantity)} ${product.unit} available."
            }

            CommandIntent.STOCK_IN,
            CommandIntent.STOCK_OUT -> showVoiceConfirmation(parsed)

            else -> {
                tvVoiceResult.text = "Command not supported in this prototype."
            }
        }
    }

    private fun showVoiceConfirmation(command: ParsedCommand) {
        val product = command.product ?: return
        val quantity = command.quantity ?: return
        val typeText =
            if (command.intent == CommandIntent.STOCK_IN) "STOCK IN" else "STOCK OUT"

        val summary = """
            Transcript:
            ${command.originalText}

            Interpreted as:
            Operation: $typeText
            Product: ${product.name}
            Quantity: ${formatQty(quantity)}
            Unit: ${command.unit ?: product.unit}
        """.trimIndent()

        tvVoiceResult.text = summary

        AlertDialog.Builder(this)
            .setTitle("Confirm voice command")
            .setMessage(summary)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ ->
                val type =
                    if (command.intent == CommandIntent.STOCK_IN) "STOCK_IN" else "STOCK_OUT"

                val result = db.adjustStock(
                    productId = product.id,
                    type = type,
                    quantity = quantity,
                    source = "VOICE",
                    transcript = command.originalText
                )

                toast(result.second)
                if (result.first) {
                    tvVoiceResult.text = "Saved locally.\n${result.second}\nSync status: PENDING"
                    refreshAll()
                }
            }
            .show()
    }

    private fun showAddProductDialog() {
        val container = dialogContainer()

        val name = editText("Product name")
        val unit = editText("Unit (kg, bag, carton, box...)")
        val opening = numberEditText("Opening quantity")
        val minimum = numberEditText("Low-stock threshold")

        container.addView(name)
        container.addView(unit)
        container.addView(opening)
        container.addView(minimum)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add product")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val productName = name.text.toString().trim()
                val productUnit = unit.text.toString().trim()
                val openingQty = opening.text.toString().toDoubleOrNull() ?: 0.0
                val minimumQty = minimum.text.toString().toDoubleOrNull() ?: 0.0

                if (productName.isBlank() || productUnit.isBlank()) {
                    toast("Product name and unit are required.")
                    return@setOnClickListener
                }

                val id = db.addProduct(productName, productUnit, openingQty, minimumQty)
                if (id == -1L) {
                    toast("A product with that name already exists.")
                    return@setOnClickListener
                }

                dialog.dismiss()
                toast("Product saved locally. Cloud sync is pending.")
                refreshAll()
            }
        }

        dialog.show()
    }

    private fun showStockDialog(type: String) {
        val products = db.getProducts()
        if (products.isEmpty()) {
            toast("Add a product first.")
            return
        }

        val container = dialogContainer()
        val productSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                products.map { "${it.name} (${formatQty(it.quantity)} ${it.unit})" }
            )
        }
        val quantity = numberEditText("Quantity")

        container.addView(productSpinner)
        container.addView(quantity)

        val title = if (type == "STOCK_IN") "Stock in" else "Stock out"

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedProduct = products[productSpinner.selectedItemPosition]
                val qty = quantity.text.toString().toDoubleOrNull()

                if (qty == null || qty <= 0) {
                    toast("Enter a valid quantity.")
                    return@setOnClickListener
                }

                val result = db.adjustStock(
                    productId = selectedProduct.id,
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
        val stats = db.getStats()

        tvProductCount.text = stats.totalProducts.toString()
        tvLowStockCount.text = stats.lowStock.toString()
        tvOutOfStockCount.text = stats.outOfStock.toString()
        tvPendingCount.text = stats.pendingSync.toString()

        renderInventory()
        renderAlerts()
        renderTransactions()
    }

    private fun renderInventory() {
        inventoryContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        db.getProducts().forEach { product ->
            val row = inflater.inflate(R.layout.item_product, inventoryContainer, false)

            row.findViewById<TextView>(R.id.tvItemProductName).text = product.name
            row.findViewById<TextView>(R.id.tvItemProductQuantity).text =
                "${formatQty(product.quantity)} ${product.unit}"
            row.findViewById<TextView>(R.id.tvItemProductStatus).text =
                when {
                    product.quantity <= 0 -> "OUT OF STOCK"
                    product.quantity <= product.minimumStock ->
                        "LOW - minimum ${formatQty(product.minimumStock)} ${product.unit}"
                    else -> "In stock"
                }

            val sync = row.findViewById<TextView>(R.id.tvItemProductSync)
            sync.text = product.syncStatus
            sync.setTextColor(
                getColor(
                    if (product.syncStatus == DatabaseHelper.SYNC_SYNCED)
                        R.color.success
                    else
                        R.color.warning
                )
            )

            inventoryContainer.addView(row)
        }
    }

    private fun renderAlerts() {
        alertsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val low = db.getLowStockProducts()

        if (low.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No low-stock alerts."
                setTextColor(getColor(R.color.text_secondary))
                setPadding(8, 12, 8, 12)
            }
            alertsContainer.addView(empty)
            return
        }

        low.forEach { product ->
            val row = inflater.inflate(R.layout.item_alert, alertsContainer, false)
            row.findViewById<TextView>(R.id.tvAlertTitle).text = product.name
            row.findViewById<TextView>(R.id.tvAlertDetail).text =
                "Current: ${formatQty(product.quantity)} ${product.unit}  |  " +
                        "Minimum: ${formatQty(product.minimumStock)} ${product.unit}"
            alertsContainer.addView(row)
        }
    }

    private fun renderTransactions() {
        transactionsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val transactions = db.getTransactions()

        if (transactions.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No transactions yet."
                setTextColor(getColor(R.color.text_secondary))
                setPadding(8, 12, 8, 12)
            }
            transactionsContainer.addView(empty)
            return
        }

        transactions.forEach { transaction ->
            val row = inflater.inflate(R.layout.item_transaction, transactionsContainer, false)

            val symbol = if (transaction.type == "STOCK_IN") "+" else "-"
            row.findViewById<TextView>(R.id.tvTxTitle).text =
                "$symbol${formatQty(transaction.quantity)} ${transaction.unit}  ${transaction.productName}"

            row.findViewById<TextView>(R.id.tvTxMeta).text =
                "${transaction.source}  |  ${dateFormat.format(Date(transaction.createdAt))}"

            val sync = row.findViewById<TextView>(R.id.tvTxSync)
            sync.text = transaction.syncStatus
            sync.setTextColor(
                getColor(
                    if (transaction.syncStatus == DatabaseHelper.SYNC_SYNCED)
                        R.color.success
                    else
                        R.color.warning
                )
            )

            transactionsContainer.addView(row)
        }
    }

    private fun dialogContainer(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
    }

    private fun editText(hint: String): EditText =
        EditText(this).apply {
            this.hint = hint
            setSingleLine(true)
        }

    private fun numberEditText(hint: String): EditText =
        EditText(this).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
        }

    private fun formatQty(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

/**
 * Small listener helper so MainActivity stays readable.
 */
class SimpleItemSelectedListener(
    private val onSelected: () -> Unit
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) = onSelected()

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
