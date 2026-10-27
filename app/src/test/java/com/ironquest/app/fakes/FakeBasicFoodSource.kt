package com.ironquest.app.fakes

import com.ironquest.app.domain.food.BasicFood
import com.ironquest.app.domain.repository.BasicFoodSource

class FakeBasicFoodSource(var foods: List<BasicFood> = emptyList()) : BasicFoodSource {
    override suspend fun all(): List<BasicFood> = foods
}
