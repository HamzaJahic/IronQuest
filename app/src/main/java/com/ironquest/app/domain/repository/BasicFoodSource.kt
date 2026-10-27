package com.ironquest.app.domain.repository

import com.ironquest.app.domain.food.BasicFood

/** The bundled food list. Loads once; an unreadable asset gives an empty list, never an exception. */
interface BasicFoodSource {
    suspend fun all(): List<BasicFood>
}
