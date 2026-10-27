package com.ironquest.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ironquest.app.data.local.dao.FoodDao
import com.ironquest.app.data.local.dao.NutritionDao
import com.ironquest.app.data.local.dao.PlayerProfileDao
import com.ironquest.app.data.local.dao.ProgramDao
import com.ironquest.app.data.local.dao.SetDao
import com.ironquest.app.data.local.dao.WorkoutDao
import com.ironquest.app.data.local.entity.ExerciseEntity
import com.ironquest.app.data.local.entity.MealEntity
import com.ironquest.app.data.local.entity.MealLogEntity
import com.ironquest.app.data.local.entity.MealLogItemEntity
import com.ironquest.app.data.local.entity.MealPlanEntity
import com.ironquest.app.data.local.entity.MealTagEntity
import com.ironquest.app.data.local.entity.NutritionDayEntity
import com.ironquest.app.data.local.entity.PlanSlotEntity
import com.ironquest.app.data.local.entity.PlayerProfileEntity
import com.ironquest.app.data.local.entity.ProgramDayEntity
import com.ironquest.app.data.local.entity.ProgramEntity
import com.ironquest.app.data.local.entity.ProgramExerciseEntity
import com.ironquest.app.data.local.entity.SetEntity
import com.ironquest.app.data.local.entity.SupplementEntity
import com.ironquest.app.data.local.entity.SupplementIntakeEntity
import com.ironquest.app.data.local.entity.UserFoodEntity
import com.ironquest.app.data.local.entity.WorkoutEntity
import com.ironquest.app.domain.food.UserFoodSource
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.program.ProgramCatalog

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutEntity::class,
        SetEntity::class,
        PlayerProfileEntity::class,
        MealEntity::class,
        MealTagEntity::class,
        MealPlanEntity::class,
        PlanSlotEntity::class,
        MealLogEntity::class,
        NutritionDayEntity::class,
        SupplementEntity::class,
        SupplementIntakeEntity::class,
        ProgramEntity::class,
        ProgramDayEntity::class,
        ProgramExerciseEntity::class,
        MealLogItemEntity::class,
        UserFoodEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class IronQuestDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun setDao(): SetDao
    abstract fun playerProfileDao(): PlayerProfileDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun programDao(): ProgramDao
    abstract fun foodDao(): FoodDao
    abstract fun wipeDao(): com.ironquest.app.data.local.dao.WipeDao

    companion object {
        const val NAME = "ironquest.db"
    }
}

/** Enums stored by name: stable across reordering, readable in Database Inspector. */
class EnumConverters {
    @TypeConverter fun statToString(value: StatType): String = value.name

    @TypeConverter fun stringToStat(value: String): StatType = StatType.valueOf(value)

    @TypeConverter fun trackingToString(value: TrackingType): String = value.name

    @TypeConverter fun stringToTracking(value: String): TrackingType = TrackingType.valueOf(value)

    @TypeConverter fun userFoodSourceToString(value: UserFoodSource): String = value.name

    @TypeConverter fun stringToUserFoodSource(value: String): UserFoodSource = UserFoodSource.entries.firstOrNull { it.name == value } ?: UserFoodSource.CUSTOM
}

/**
 * Runs synchronously inside the open helper, before Room hands the database to any DAO,
 * so there's no race between seeding and the first query (unlike launching a coroutine
 * from onCreate).
 *
 * Exercises are upserted on every open, so adding a new exercise to ProgramCatalog
 * needs no migration. INSERT OR IGNORE + UPDATE is used instead of INSERT OR REPLACE,
 * because REPLACE deletes the old row first and would trip the RESTRICT foreign key
 * from `sets`. (Native UPSERT needs SQLite 3.24 = API 30; minSdk is 26.)
 */
class SeedCallback(private val text: (Int) -> String) : RoomDatabase.Callback() {
    /** [text] resolves string resources in the device language at install time. */
    constructor(context: Context) : this({ id -> context.getString(id) })

    /** Fresh installs only (existing installs get the same data from MIGRATION_1_2). */
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            NutritionSeed.seed(db, text)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            ProgramCatalog.Exercises.all.forEach { ex ->
                db.execSQL(
                    "INSERT OR IGNORE INTO exercises (id, name, stat, trackingType, incrementKg) VALUES (?, ?, ?, ?, ?)",
                    arrayOf(ex.id, ex.name, ex.stat.name, ex.trackingType.name, ex.incrementKg),
                )
                db.execSQL(
                    "UPDATE exercises SET name = ?, stat = ?, trackingType = ?, incrementKg = ? WHERE id = ?",
                    arrayOf(ex.name, ex.stat.name, ex.trackingType.name, ex.incrementKg, ex.id),
                )
            }
            // Kotlin default values are not SQL DEFAULTs, so every NOT NULL column is spelled out.
            db.execSQL(
                """
                INSERT OR IGNORE INTO player_profile
                    (id, totalXp, constitutionXp, strengthXp, staminaXp, questsCompleted, highestLevelReached)
                VALUES (${PlayerProfileEntity.SINGLETON_ID}, 0, 0, 0, 0, 0, 1)
                """.trimIndent(),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
