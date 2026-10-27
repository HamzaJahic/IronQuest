package com.ironquest.app.domain.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the generated asset: a broken or truncated file would silently empty the built-in search. */
class BundledFoodsTest {
    // Unit tests run with the module directory as working directory.
    // not UI text: test message/path
    private val table = BasicFoodTsv.parse(File("src/main/assets/foods/basic_foods.tsv").readLines().asSequence())

    @Test
    fun `the bundled list parses completely`() {
        // not UI text: test message/path
        assertTrue("only ${table.foods.size} foods", table.foods.size > 1_000)
        assertEquals(0, table.skipped)
        assertEquals(table.foods.size, table.foods.map { it.usdaId }.toSet().size)
    }

    @Test
    fun `everyday foods are found and eggs count in pieces`() {
        listOf("egg", "chicken breast", "rice", "banana", "oats", "milk", "olive oil").forEach { query ->
            // not UI text: test message/path
            assertTrue("no hit for $query", FoodSearchRanker.rank(query, table.foods, { it.name }, { it.aliases }, limit = 5).isNotEmpty())
        }
        val egg = FoodSearchRanker.rank("eggs", table.foods, { it.name }, { it.aliases }, limit = 1).single()
        assertTrue(egg.units.any { it.kind == UnitKind.PIECE })
    }
}
