package com.example.gigpoint.voice

import com.example.gigpoint.domain.Product
import java.util.Locale

class VoiceUnderstandingEngine {

    private val cancelPhrases =
        listOf(
            "cancel",
            "stop",
            "never mind",
            "nevermind",
            "vaddu",
            "oddu",
            "వద్దు",
            "ఆపు",
            "cancel cheyyi",
            "cancel chey",
            "mat karo",
            "रद्द",
            "रद्द करो",
            "रुको",
            "बस"
        )

    private val negativePhrases =
        listOf(
            "do not",
            "don't",
            "dont",
            "cheyyaku",
            "cheyaku",
            "చేయకు",
            "వద్దు",
            "mat karo",
            "मत करो",
            "नहीं करना"
        )

    private val stockIn =
        listOf(
            "add",
            "stock in",
            "received",
            "came",
            "plus",
            "restock",
            "refill",

            "vachindi",
            "vachayi",
            "add cheyyi",
            "add chey",
            "stock lo pettu",
            "stock lo veyyi",
            "cherchu",
            "jodinchandi",

            "వచ్చింది",
            "వచ్చాయి",
            "జోడించు",
            "చేర్చు",
            "స్టాక్ లో పెట్టు",

            "add karo",
            "stock mein daalo",
            "stock me dalo",
            "aaya",
            "aayi",
            "aaye",

            "जोड़ो",
            "स्टॉक में डालो",
            "आया",
            "आई",
            "आए"
        )

    private val sale =
        listOf(
            "sold",
            "sale",
            "sell",
            "customer bought",

            "ammindi",
            "ammayi",
            "sale ayyindi",
            "sale ayyayi",
            "ammesanu",

            "అమ్మింది",
            "అమ్మాయి",
            "అమ్మేశాను",
            "సేల్ అయింది",

            "bik gaya",
            "bik gaye",
            "becha",
            "sale hua",

            "बिक गया",
            "बिक गए",
            "बेचा",
            "सेल हुआ"
        )

    private val genericRemove =
        listOf(
            "remove",
            "take out",
            "minus",
            "reduce",

            "teesey",
            "teese",
            "teesiveyyi",

            "తీసేయి",
            "తీసివేయి",
            "తగ్గించు",

            "nikalo",
            "hatao",

            "निकालो",
            "हटाओ",
            "कम करो"
        )

    private val damage =
        listOf(
            "damaged",
            "damage",
            "broken",
            "spoiled",

            "poyindi",
            "damage ayyindi",
            "paadayindi",

            "పాడైంది",
            "డ్యామేజ్ అయింది",
            "విరిగింది",

            "kharab",
            "toot gaya",

            "खराब",
            "टूट गया",
            "डैमेज"
        )

    private val expired =
        listOf(
            "expired",
            "expiry",
            "expire",

            "date ayipoyindi",
            "expiry ayindi",

            "ఎక్స్‌పైర్ అయింది",
            "గడువు ముగిసింది",

            "expire ho gaya",
            "expiry ho gayi",

            "एक्सपायर हो गया",
            "मियाद खत्म"
        )

    private val customerReturn =
        listOf(
            "customer return",
            "returned by customer",
            "customer returned",

            "customer ichadu",
            "customer return vachindi",

            "కస్టమర్ రిటర్న్",
            "కస్టమర్ తిరిగి ఇచ్చాడు",

            "customer wapas",
            "customer return aaya",

            "कस्टमर रिटर्न",
            "कस्टमर ने वापस किया"
        )

    private val supplierReturn =
        listOf(
            "supplier return",
            "return to supplier",
            "vendor return",

            "supplier ki return",

            "సప్లయర్ కి రిటర్న్",
            "వెండర్ రిటర్న్",

            "supplier ko return",

            "सप्लायर को रिटर्न",
            "वेंडर रिटर्न"
        )

    private val checkStock =
        listOf(
            "how much",
            "available",
            "check stock",
            "what is stock",
            "stock available",

            "stock entha",
            "entha undi",
            "entha stock",

            "స్టాక్ ఎంత",
            "ఎంత ఉంది",
            "ఎంత స్టాక్ ఉంది",

            "stock kitna",
            "kitna hai",
            "kitna stock",

            "स्टॉक कितना",
            "कितना है",
            "कितना स्टॉक है"
        )

    private val lowStock =
        listOf(
            "low stock",
            "running low",
            "needs reorder",
            "need reorder",
            "what should i reorder",
            "what needs refill",

            "takkuva",
            "thakkuva",
            "low unna items",

            "తక్కువ",
            "తక్కువ స్టాక్",
            "ఏవి తక్కువగా ఉన్నాయి",

            "kam stock",

            "कम स्टॉक",
            "क्या कम है",
            "रीऑर्डर"
        )

    private val setStock =
        listOf(
            "set stock",
            "stock set",
            "set quantity",

            "stock ni set",

            "స్టాక్ సెట్",
            "క్వాంటిటీ సెట్",

            "stock set karo",

            "स्टॉक सेट",
            "क्वांटिटी सेट"
        )

    private val setMinimum =
        listOf(
            "set minimum",
            "minimum stock",
            "reorder level",
            "low stock limit",
            "minimum set",

            "reorder set",

            "మినిమమ్ స్టాక్",
            "రీఆర్డర్ లెవల్",

            "minimum set karo",
            "reorder level set karo",

            "मिनिमम स्टॉक",
            "रीऑर्डर लेवल"
        )

    private val archive =
        listOf(
            "delete product",
            "delete item",
            "remove product",
            "archive product",
            "product delete",
            "item delete",

            "product ni delete",
            "item ni delete",

            "ప్రొడక్ట్ డిలీట్",
            "ఐటమ్ డిలీట్",

            "product delete karo",
            "item hatao",

            "प्रोडक्ट डिलीट",
            "आइटम हटाओ"
        )

    private val numberWords =
        mapOf(
            // English
            "zero" to 0.0,
            "one" to 1.0,
            "two" to 2.0,
            "three" to 3.0,
            "four" to 4.0,
            "five" to 5.0,
            "six" to 6.0,
            "seven" to 7.0,
            "eight" to 8.0,
            "nine" to 9.0,
            "ten" to 10.0,
            "eleven" to 11.0,
            "twelve" to 12.0,
            "thirteen" to 13.0,
            "fourteen" to 14.0,
            "fifteen" to 15.0,
            "sixteen" to 16.0,
            "seventeen" to 17.0,
            "eighteen" to 18.0,
            "nineteen" to 19.0,
            "twenty" to 20.0,
            "thirty" to 30.0,
            "forty" to 40.0,
            "fifty" to 50.0,
            "hundred" to 100.0,
            "half" to 0.5,

            // Telugu transliteration
            "oka" to 1.0,
            "okati" to 1.0,
            "rendu" to 2.0,
            "moodu" to 3.0,
            "nalugu" to 4.0,
            "aidu" to 5.0,
            "aaru" to 6.0,
            "edu" to 7.0,
            "enimidi" to 8.0,
            "tommidi" to 9.0,
            "padi" to 10.0,

            // Telugu script
            "ఒకటి" to 1.0,
            "రెండు" to 2.0,
            "మూడు" to 3.0,
            "నాలుగు" to 4.0,
            "ఐదు" to 5.0,
            "ఆరు" to 6.0,
            "ఏడు" to 7.0,
            "ఎనిమిది" to 8.0,
            "తొమ్మిది" to 9.0,
            "పది" to 10.0,

            // Hindi transliteration
            "ek" to 1.0,
            "do" to 2.0,
            "teen" to 3.0,
            "char" to 4.0,
            "chaar" to 4.0,
            "paanch" to 5.0,
            "panch" to 5.0,
            "chhe" to 6.0,
            "cheh" to 6.0,
            "saat" to 7.0,
            "aath" to 8.0,
            "nau" to 9.0,
            "das" to 10.0,

            // Hindi script
            "एक" to 1.0,
            "दो" to 2.0,
            "तीन" to 3.0,
            "चार" to 4.0,
            "पांच" to 5.0,
            "पाँच" to 5.0,
            "छह" to 6.0,
            "सात" to 7.0,
            "आठ" to 8.0,
            "नौ" to 9.0,
            "दस" to 10.0
        )

    private val units =
        linkedMapOf(
            "quintals" to "quintal",
            "quintal" to "quintal",

            "cartons" to "carton",
            "carton" to "carton",
            "కార్టన్" to "carton",
            "कार्टन" to "carton",

            "packets" to "packet",
            "packet" to "packet",
            "packs" to "packet",
            "pack" to "packet",
            "ప్యాకెట్" to "packet",
            "ప్యాకెట్లు" to "packet",
            "पैकेट" to "packet",

            "pieces" to "piece",
            "piece" to "piece",
            "pcs" to "piece",
            "పీస్" to "piece",
            "पीस" to "piece",

            "boxes" to "box",
            "box" to "box",
            "బాక్స్" to "box",
            "డబ్బా" to "box",
            "बॉक्स" to "box",
            "डिब्बा" to "box",

            "bags" to "bag",
            "bag" to "bag",
            "బ్యాగ్" to "bag",
            "బస్తా" to "bag",
            "बैग" to "bag",
            "बोरी" to "bag",

            "kilograms" to "kg",
            "kilogram" to "kg",
            "kilos" to "kg",
            "kilo" to "kg",
            "kgs" to "kg",
            "kg" to "kg",
            "కిలో" to "kg",
            "కిలోలు" to "kg",
            "किलो" to "kg",

            "litres" to "litre",
            "liters" to "litre",
            "litre" to "litre",
            "liter" to "litre",
            "లీటర్" to "litre",
            "లీటర్లు" to "litre",
            "लीटर" to "litre",

            "bottles" to "bottle",
            "bottle" to "bottle",
            "బాటిల్" to "bottle",
            "బాటిల్స్" to "bottle",
            "बोतल" to "bottle",
            "बोतलें" to "bottle",

            "dozens" to "dozen",
            "dozen" to "dozen",

            "grams" to "gram",
            "gram" to "gram",
            "గ్రామ్" to "gram",
            "ग्राम" to "gram"
        )

    fun understand(
        text: String,
        products: List<Product>
    ): CommandDraft {

        val n =
            normalize(
                text
            )

        return CommandDraft(
            action =
                detectAction(
                    n
                ),

            product =
                resolveProduct(
                    n,
                    products
                ),

            quantity =
                extractQuantity(
                    n
                ),

            unit =
                extractUnit(
                    n
                ),

            originalUtterances =
                listOf(
                    text
                )
        )
    }

    fun isCancel(
        text: String
    ): Boolean {

        val n =
            normalize(
                text
            )

        return containsAny(
            n,
            cancelPhrases
        )
    }

    fun hasNegation(
        text: String
    ): Boolean {

        val n =
            normalize(
                text
            )

        return containsAny(
            n,
            negativePhrases
        )
    }

    fun extractQuantityAndUnit(
        text: String
    ): Pair<Double?, String?> {

        val n =
            normalize(
                text
            )

        return extractQuantity(
            n
        ) to
            extractUnit(
                n
            )
    }

    fun detectOutboundReason(
        text: String
    ): InventoryAction? {

        val n =
            normalize(
                text
            )

        return when {

            containsAny(
                n,
                sale
            ) ->
                InventoryAction
                    .SALE

            containsAny(
                n,
                damage
            ) ->
                InventoryAction
                    .DAMAGE

            containsAny(
                n,
                expired
            ) ->
                InventoryAction
                    .EXPIRED

            containsAny(
                n,
                customerReturn
            ) ->
                InventoryAction
                    .CUSTOMER_RETURN

            containsAny(
                n,
                supplierReturn
            ) ->
                InventoryAction
                    .SUPPLIER_RETURN

            n.contains(
                "correction"
            ) ||
            n.contains(
                "adjustment"
            ) ||
            n.contains(
                "సరి చేయి"
            ) ||
            n.contains(
                "सुधार"
            ) ->
                InventoryAction
                    .ADJUSTMENT_OUT

            else ->
                null
        }
    }

    fun resolveProductOnly(
        text: String,
        products: List<Product>
    ): Product? =
        resolveProduct(
            normalize(
                text
            ),
            products
        )

    private fun detectAction(
        n: String
    ): InventoryAction {

        return when {

            containsAny(
                n,
                lowStock
            ) ->
                InventoryAction
                    .LOW_STOCK

            containsAny(
                n,
                checkStock
            ) ->
                InventoryAction
                    .CHECK_STOCK

            containsAny(
                n,
                setMinimum
            ) ->
                InventoryAction
                    .SET_REORDER_LEVEL

            containsAny(
                n,
                setStock
            ) ->
                InventoryAction
                    .SET_STOCK

            containsAny(
                n,
                archive
            ) ->
                InventoryAction
                    .ARCHIVE_PRODUCT

            containsAny(
                n,
                customerReturn
            ) ->
                InventoryAction
                    .CUSTOMER_RETURN

            containsAny(
                n,
                supplierReturn
            ) ->
                InventoryAction
                    .SUPPLIER_RETURN

            containsAny(
                n,
                damage
            ) ->
                InventoryAction
                    .DAMAGE

            containsAny(
                n,
                expired
            ) ->
                InventoryAction
                    .EXPIRED

            containsAny(
                n,
                sale
            ) ->
                InventoryAction
                    .SALE

            containsAny(
                n,
                stockIn
            ) ->
                InventoryAction
                    .STOCK_IN

            containsAny(
                n,
                genericRemove
            ) ->
                InventoryAction
                    .OUTBOUND_UNSPECIFIED

            else ->
                InventoryAction
                    .UNKNOWN
        }
    }

    private fun resolveProduct(
        n: String,
        products: List<Product>
    ): Product? {

        val sorted =
            products
                .sortedByDescending {
                    it.name.length
                }

        val exact =
            sorted
                .firstOrNull {

                    val product =
                        normalize(
                            it.name
                        )

                    n.contains(
                        product
                    )
                }

        if (
            exact !=
            null
        ) {
            return exact
        }

        val aliases =
            mapOf(
                "coke" to "coca cola",
                "coca cola" to "coca cola",
                "oil" to "sunflower oil",
                "parle" to "parle g",
                "parle biscuit" to "parle g"
            )

        aliases
            .forEach {
                    (
                        alias,
                        canonical
                    ) ->

                if (
                    n.contains(
                        normalize(
                            alias
                        )
                    )
                ) {

                    return products
                        .firstOrNull {

                            val pn =
                                normalize(
                                    it.name
                                )

                            pn.contains(
                                normalize(
                                    canonical
                                )
                            ) ||
                                normalize(
                                    canonical
                                )
                                    .contains(
                                        pn
                                    )
                        }
                }
            }

        /**
         * Safe token matching:
         * Only resolve when every significant word in the product name
         * appears in the transcript. We intentionally avoid aggressive
         * fuzzy matching because selecting the wrong product would be
         * worse than asking the merchant to repeat the product name.
         */
        return sorted
            .firstOrNull {

                val tokens =
                    normalize(
                        it.name
                    )
                        .split(
                            " "
                        )
                        .filter {
                            token ->
                            token.length >=
                                2
                        }

                tokens
                    .isNotEmpty() &&
                    tokens
                        .all {
                            token ->
                            n.contains(
                                token
                            )
                        }
            }
    }

    private fun extractQuantity(
        n: String
    ): Double? {

        Regex(
            """(?<![A-Za-z])\d+(?:\.\d+)?"""
        )
            .find(
                n
            )
            ?.value
            ?.toDoubleOrNull()
            ?.let {
                return it
            }

        val tokens =
            n.split(
                " "
            )

        // Handle simple English compound quantities such as "twenty five".
        for (
            i in tokens.indices
        ) {

            val first =
                numberWords[
                    tokens[i]
                ]
                    ?: continue

            if (
                first >=
                20.0 &&
                first <
                100.0 &&
                i +
                    1 <
                tokens.size
            ) {

                val second =
                    numberWords[
                        tokens[
                            i +
                                1
                        ]
                    ]

                if (
                    second !=
                    null &&
                    second >
                    0.0 &&
                    second <
                    10.0
                ) {

                    return first +
                        second
                }
            }

            return first
        }

        return null
    }

    private fun extractUnit(
        n: String
    ): String? {

        units
            .forEach {
                    (
                        spoken,
                        canonical
                    ) ->

                val regex =
                    Regex(
                        """(^|\s)${Regex.escape(spoken)}($|\s)"""
                    )

                if (
                    regex
                        .containsMatchIn(
                            n
                        )
                ) {

                    return canonical
                }
            }

        return null
    }

    private fun containsAny(
        n: String,
        values: List<String>
    ): Boolean =
        values
            .any {
                value ->
                n.contains(
                    normalize(
                        value
                    )
                )
            }

    private fun normalize(
        value: String
    ): String =
        value
            .lowercase(
                Locale.ROOT
            )
            .replace(
                "-",
                " "
            )
            .replace(
                Regex(
                    """[^\p{L}\p{N}.]+"""
                ),
                " "
            )
            .replace(
                Regex(
                    """\s+"""
                ),
                " "
            )
            .trim()
}
