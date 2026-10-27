package com.ironquest.app.domain.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BasicFoodTsvTest {
    private val header = "#ironquest-basic-foods\t1\nusdaId\tname\taliases\tprotein\tcarbs\tfat\tfiber\tkcal\tmlDensity\tpieceGrams\tportions"

    @Test
    fun `a full line parses with its units`() {
        val table = BasicFoodTsv.parse("$header\n171287\tEgg, whole, raw\teggs|egg\t12.56\t0.72\t9.51\t0\t143\t\t50\tcup=243".lineSequence())
        val egg = table.foods.single()
        assertEquals(171287L, egg.usdaId)
        assertEquals(listOf("eggs", "egg"), egg.aliases)
        assertEquals(143.0, checkNotNull(egg.per100g.kcal), 0.0)
        assertEquals(
            listOf(FoodUnit.GRAM, FoodUnit(UnitKind.PIECE, 50.0), FoodUnit(UnitKind.PORTION, 243.0, "cup")),
            egg.units,
        )
        assertEquals(0, table.skipped)
    }

    @Test
    fun `missing kcal stays null and drinks get ml`() {
        val milk = BasicFoodTsv.parse("$header\n1\tMilk, whole\t\t3.2\t4.8\t3.3\t0\t\t1.03\t\t".lineSequence()).foods.single()
        assertNull(milk.per100g.kcal)
        assertEquals(FoodUnit(UnitKind.MILLILITRE, 1.03), milk.units[1])
    }

    @Test
    fun `bad lines are skipped and counted, never thrown`() {
        val table = BasicFoodTsv.parse("$header\nnot\ta\tfood\n2\tRice\t\tx\t1\t1\t1\t1\t\t\t\n\n3\tOats\t\t13\t68\t6.5\t10\t379\t\t\t".lineSequence())
        assertEquals(listOf("Oats"), table.foods.map { it.name })
        assertEquals(2, table.skipped)
    }

    @Test
    fun `a file with another header or version gives nothing`() {
        assertEquals(0, BasicFoodTsv.parse("hello\nworld".lineSequence()).foods.size)
        assertEquals(0, BasicFoodTsv.parse("#ironquest-basic-foods\t99\nx".lineSequence()).foods.size)
    }
}
