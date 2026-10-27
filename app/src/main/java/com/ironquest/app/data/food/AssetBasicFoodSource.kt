package com.ironquest.app.data.food

import android.content.Context
import com.ironquest.app.domain.food.BasicFood
import com.ironquest.app.domain.food.BasicFoodTsv
import com.ironquest.app.domain.repository.BasicFoodSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetBasicFoodSource @Inject constructor(@ApplicationContext private val context: Context) : BasicFoodSource {
    private val mutex = Mutex()
    private var cached: List<BasicFood>? = null

    override suspend fun all(): List<BasicFood> = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { load() }.also { cached = it }
    }

    private fun load(): List<BasicFood> = try {
        context.assets.open(PATH).bufferedReader().useLines { lines -> BasicFoodTsv.parse(lines).foods }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        emptyList()
    }

    private companion object {
        const val PATH = "foods/basic_foods.tsv" // not UI text: asset path
    }
}
