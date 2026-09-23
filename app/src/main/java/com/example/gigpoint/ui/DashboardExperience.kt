package com.example.gigpoint.ui

data class DashboardExperience(
    val label: String,
    val subtitle: String,
    val attentionSubtitle: String,
    val productsTitle: String,
    val productsSubtitle: String,
    val companionPrompt: String,
    val focusTags: List<String>
) {
    companion object {
        fun forBusinessType(businessType: String?): DashboardExperience {
            val n = businessType?.trim()?.lowercase().orEmpty()

            return when {
                n.contains("medical") || n.contains("pharmacy") ->
                    DashboardExperience(
                        "PHARMACY PULSE",
                        "Batches, expiry and medicine availability",
                        "Expiry, batch and stock risks that need attention",
                        "Medicines & stock",
                        "Search medicine, brand, strength or availability",
                        "Try: \"Next month expire ayye medicines cheppu\"",
                        listOf("EXPIRY", "BATCHES", "LOW STOCK")
                    )

                n.contains("cloth") || n.contains("fashion") ->
                    DashboardExperience(
                        "FASHION PULSE",
                        "Sizes, colours and style availability",
                        "Missing sizes, low variants and slow-moving stock",
                        "Styles & variants",
                        "Search style, brand, size or colour",
                        "Try: \"Blue shirt medium size unda?\"",
                        listOf("SIZES", "COLOURS", "SLOW MOVERS")
                    )

                n.contains("mobile") || n.contains("electronics") ->
                    DashboardExperience(
                        "DEVICE RETAIL PULSE",
                        "Models, variants, serial stock and accessories",
                        "Low variants, ageing devices and accessory gaps",
                        "Devices & accessories",
                        "Search brand, model, RAM, storage or accessory",
                        "Try: \"8GB 256GB phones em unnayi?\"",
                        listOf("VARIANTS", "SERIAL / IMEI", "ACCESSORIES")
                    )

                n.contains("electrical") || n.contains("hardware") ->
                    DashboardExperience(
                        "ELECTRICAL STOCK PULSE",
                        "Specifications, brands and fast-moving stock",
                        "Low specifications and products that need restocking",
                        "Products & specifications",
                        "Search product, brand, wattage, gauge or size",
                        "Try: \"1.5 square wire coil stock entha undi?\"",
                        listOf("SPECS", "BRANDS", "LOW STOCK")
                    )

                n.contains("restaurant") || n.contains("food") || n.contains("bakery") ->
                    DashboardExperience(
                        "FOOD BUSINESS PULSE",
                        "Daily movement, ingredients and fast-moving items",
                        "Ingredient shortages, expiry and stock pressure",
                        "Items & ingredients",
                        "Search item, ingredient, category or availability",
                        "Try: \"Today low stock ingredients enti?\"",
                        listOf("INGREDIENTS", "EXPIRY", "FAST MOVERS")
                    )

                n.contains("wholesale") ->
                    DashboardExperience(
                        "WHOLESALE PULSE",
                        "Bulk inventory, movement and supplier readiness",
                        "Bulk stock risks and supplier-sensitive items",
                        "Bulk catalogue",
                        "Search product, case, carton, supplier or stock",
                        "Try: \"Rice bags stock entha undi?\"",
                        listOf("BULK STOCK", "SUPPLIERS", "REORDER")
                    )

                n.contains("beauty") || n.contains("cosmetic") ->
                    DashboardExperience(
                        "BEAUTY RETAIL PULSE",
                        "Brands, shades, variants and expiry-aware stock",
                        "Low shades, ageing stock and expiry risks",
                        "Beauty products",
                        "Search product, brand, shade or category",
                        "Try: \"Lipstick red shades stock cheppu\"",
                        listOf("SHADES", "BRANDS", "EXPIRY")
                    )

                n.contains("stationery") ->
                    DashboardExperience(
                        "STATIONERY PULSE",
                        "Fast-moving SKUs, brands and school/office stock",
                        "Low fast-movers and products that need reorder",
                        "Stationery products",
                        "Search item, brand, size or category",
                        "Try: \"A4 notebooks stock entha undi?\"",
                        listOf("FAST MOVERS", "BRANDS", "REORDER")
                    )

                n.contains("automobile") || n.contains("auto parts") ->
                    DashboardExperience(
                        "AUTO PARTS PULSE",
                        "Part numbers, compatibility and stock availability",
                        "Low-demand parts, missing variants and reorder needs",
                        "Parts & compatibility",
                        "Search part, brand, model or part number",
                        "Try: \"Activa brake pads stock lo unnaya?\"",
                        listOf("PART NO.", "COMPATIBILITY", "LOW STOCK")
                    )

                n.contains("grocery") || n.contains("kirana") ||
                    n.contains("general") || n.contains("fruit") ->
                    DashboardExperience(
                        "STORE PULSE",
                        "Daily sales, fast-moving stock and availability",
                        "Low, out-of-stock and ageing items that need action",
                        "Products & stock",
                        "Search product, brand, category or availability",
                        "Try: \"Sugar stock entha undi?\"",
                        listOf("FAST MOVERS", "LOW STOCK", "EXPIRY")
                    )

                else ->
                    DashboardExperience(
                        "BUSINESS PULSE",
                        "Sales, stock and the work that needs attention",
                        "Important stock and operational alerts",
                        "Products",
                        "Search products and check availability",
                        "Try: \"What is low in stock?\"",
                        listOf("STOCK", "SALES", "ALERTS")
                    )
            }
        }
    }
}
