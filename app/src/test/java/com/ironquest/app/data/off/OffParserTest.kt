package com.ironquest.app.data.off

import com.ironquest.app.domain.food.OnlineFood
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OffParserTest {

    private val foods: List<OnlineFood> by lazy { OffParser.parseSearch(fixture("off/search_ok.json")) }

    private fun fixture(path: String): String = requireNotNull(javaClass.classLoader?.getResource(path)) { "missing test resource $path" }.readText()

    @Test
    fun `parses usable products and skips the one without a name`() {
        assertEquals(listOf("7310865004703", "123"), foods.map { it.barcode })
    }

    @Test
    fun `first product has name brand macros kcal and serving`() {
        val skyr = foods.first()
        assertEquals("Skyr Naturell", skyr.name)
        assertEquals("Arla", skyr.brand)
        assertEquals(11.0, skyr.per100g.proteinG, 1e-9)
        assertEquals(4.0, skyr.per100g.carbsG, 1e-9)
        assertEquals(0.2, skyr.per100g.fatG, 1e-9)
        assertEquals(63.0, checkNotNull(skyr.per100g.kcal), 1e-9)
        assertEquals(150.0, checkNotNull(skyr.servingGrams), 1e-9)
    }

    @Test
    fun `string numbers are accepted, kcal comes from kJ and a blank brand is null`() {
        val bar = foods.last()
        assertEquals(20.0, bar.per100g.proteinG, 1e-9)
        assertEquals(1674 / 4.184, checkNotNull(bar.per100g.kcal), 1e-6)
        assertNull(bar.brand)
        assertNull(bar.servingGrams)
    }

    @Test
    fun `a product with no protein carbs fat or energy is skipped`() {
        assertTrue(OffParser.parseSearch(fixture("off/search_missing.json")).isEmpty())
    }

    @Test
    fun `serving quantity may be a string and negatives are clamped to zero`() {
        val body = """{"products":[{"code":"1","product_name":"X","serving_quantity":"30","nutriments":{"proteins_100g":-5,"fat_100g":2}}]}"""
        val food = OffParser.parseSearch(body).single()
        assertEquals(30.0, checkNotNull(food.servingGrams), 1e-9)
        assertEquals(0.0, food.per100g.proteinG, 1e-9)
        assertNull(food.per100g.kcal)
    }

    @Test
    fun `a products value that is not an array gives an empty list`() {
        assertTrue(OffParser.parseSearch("""{"products": "x"}""").isEmpty())
    }

    @Test(expected = JSONException::class)
    fun `a body that is not JSON throws JSONException`() {
        OffParser.parseSearch("not json")
    }
}
