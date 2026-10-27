package com.ironquest.app.domain.food

import com.ironquest.app.domain.nutrition.Macros

/** Parses `assets/foods/basic_foods.tsv` (written by `scripts/build-foods.py`). Never throws. */
object BasicFoodTsv {
    const val MAGIC = "#ironquest-basic-foods" // not UI text: file header
    const val VERSION = 1
    private const val COLUMNS = 11

    fun parse(lines: Sequence<String>): BasicFoodTable {
        val iterator = lines.iterator()
        if (!iterator.hasNext()) return BasicFoodTable(emptyList(), 0)
        val first = iterator.next().split('\t')
        if (first.getOrNull(0) != MAGIC || first.getOrNull(1)?.trim()?.toIntOrNull() != VERSION) return BasicFoodTable(emptyList(), 0)
        if (iterator.hasNext()) iterator.next() // column header
        val foods = mutableListOf<BasicFood>()
        var skipped = 0
        while (iterator.hasNext()) {
            val line = iterator.next()
            if (line.isBlank()) continue
            val food = runCatching { parseLine(line) }.getOrNull()
            if (food == null) skipped++ else foods += food
        }
        return BasicFoodTable(foods, skipped)
    }

    private fun parseLine(line: String): BasicFood? {
        val cells = line.split('\t')
        if (cells.size != COLUMNS) return null
        val id = cells[0].toLongOrNull() ?: return null
        val name = cells[1].trim().takeIf { it.isNotEmpty() } ?: return null
        fun number(index: Int): Double? = cells[index].trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }
        // not UI text: internal
        fun required(index: Int): Double = number(index) ?: throw IllegalArgumentException("column $index")
        val macros = Macros(proteinG = required(3), carbsG = required(4), fatG = required(5), fiberG = number(6) ?: 0.0, kcal = number(7))
        if (cells[7].isNotBlank() && macros.kcal == null) return null
        val units = buildList {
            add(FoodUnit.GRAM)
            number(8)?.takeIf { it > 0 }?.let { add(FoodUnit(UnitKind.MILLILITRE, it)) }
            number(9)?.takeIf { it > 0 }?.let { add(FoodUnit(UnitKind.PIECE, it)) }
            cells[10].split('|').filter { it.isNotBlank() }.forEach { portion ->
                val label = portion.substringBefore('=').trim()
                val grams = portion.substringAfter('=', "").trim().toDoubleOrNull()
                if (label.isNotEmpty() && grams != null && grams > 0) add(FoodUnit(UnitKind.PORTION, grams, label))
            }
        }
        return BasicFood(id, name, cells[2].split('|').map { it.trim() }.filter { it.isNotEmpty() }, macros, units)
    }
}
