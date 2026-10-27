package com.ironquest.app.data.off

import com.ironquest.app.domain.food.OnlineFood
import com.ironquest.app.domain.nutrition.Macros
import org.json.JSONObject

/**
 * Parses an Open Food Facts search response. Uses `opt()` + `as?` because Android's org.json and json.org
 * differ in `optString` / number handling. Numbers may arrive as JSON numbers or strings.
 */
object OffParser {
    const val MAX_NAME_LENGTH = 120
    private const val KJ_PER_KCAL = 4.184

    /** Throws `JSONException` when [body] is not JSON; the client maps that to BAD_RESPONSE. */
    fun parseSearch(body: String): List<OnlineFood> {
        val products = JSONObject(body).optJSONArray("products") ?: return emptyList() // not UI text: JSON key
        return (0 until products.length()).mapNotNull { index ->
            (products.opt(index) as? JSONObject)?.let(::parseProduct)
        }
    }

    private fun parseProduct(product: JSONObject): OnlineFood? {
        val barcode = (product.opt("code") as? String)?.trim().orEmpty() // not UI text: JSON key
        val name = (product.opt("product_name") as? String)?.trim().orEmpty().take(MAX_NAME_LENGTH) // not UI text: JSON key
        if (barcode.isEmpty() || name.isEmpty()) return null
        val nutriments = product.optJSONObject("nutriments") ?: return null // not UI text: JSON key
        val protein = nutriments.number("proteins_100g") // not UI text: JSON key
        val carbs = nutriments.number("carbohydrates_100g") // not UI text: JSON key
        val fat = nutriments.number("fat_100g") // not UI text: JSON key
        val fiber = nutriments.number("fiber_100g") // not UI text: JSON key
        val kcal = nutriments.number("energy-kcal_100g") // not UI text: JSON key
            ?: nutriments.number("energy-kj_100g")?.div(KJ_PER_KCAL) // not UI text: JSON key
        if (protein == null && carbs == null && fat == null && kcal == null) return null
        val brand = (product.opt("brands") as? String)?.trim()?.takeIf { it.isNotEmpty() } // not UI text: JSON key
        return OnlineFood(
            barcode = barcode,
            name = name,
            brand = brand,
            per100g = Macros(
                proteinG = protein ?: 0.0,
                carbsG = carbs ?: 0.0,
                fatG = fat ?: 0.0,
                fiberG = fiber ?: 0.0,
                kcal = kcal,
            ),
            servingGrams = product.number("serving_quantity")?.takeIf { it > 0 }, // not UI text: JSON key
        )
    }

    private fun JSONObject.number(key: String): Double? = when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
}
