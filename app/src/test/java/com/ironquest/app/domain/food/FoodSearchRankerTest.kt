package com.ironquest.app.domain.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSearchRankerTest {
    private data class Item(val name: String, val aliases: List<String> = emptyList())

    private fun rank(query: String, vararg items: Item) =
        FoodSearchRanker.rank(query, items.toList(), name = { it.name }, aliases = { it.aliases }, limit = 10).map { it.name }

    @Test
    fun `plural queries find singular names and the reverse`() {
        assertEquals(listOf("Egg, whole, raw"), rank("eggs", Item("Egg, whole, raw"), Item("Eggplant, raw")).take(1))
        assertEquals(listOf("Tomatoes, red, raw"), rank("tomato", Item("Tomatoes, red, raw")))
        assertEquals(listOf("Blueberries, raw"), rank("blueberry", Item("Blueberries, raw")))
    }

    @Test
    fun `every query word must match`() {
        assertEquals(listOf("Chicken breast, roasted"), rank("chicken breast", Item("Chicken breast, roasted"), Item("Chicken thigh, roasted")))
    }

    @Test
    fun `exact words beat prefixes and shorter names win ties`() {
        val result = rank("rice", Item("Rice, white, cooked"), Item("Rice cakes"), Item("Licorice"), Item("Rice, brown, long-grain, cooked"))
        assertEquals(listOf("Rice cakes", "Rice, white, cooked", "Rice, brown, long-grain, cooked", "Licorice"), result)
    }

    @Test
    fun `aliases count like the name`() {
        assertEquals(listOf("Oats, dry"), rank("porridge", Item("Oats, dry", aliases = listOf("porridge oats"))))
    }

    @Test
    fun `blank queries and no matches give nothing`() {
        assertTrue(rank("  ", Item("Rice")).isEmpty())
        assertTrue(rank("zzz", Item("Rice")).isEmpty())
    }
}
