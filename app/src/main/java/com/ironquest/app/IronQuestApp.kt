package com.ironquest.app

import android.app.Application
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.notifications.GoalNotifier
import com.ironquest.app.notifications.ReminderCoordinator
import com.ironquest.app.notifications.ReminderNotifier
import com.ironquest.app.notifications.SafetyRearmReceiver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class IronQuestApp : Application() {

    @Inject lateinit var reminderNotifier: ReminderNotifier

    @Inject lateinit var reminderCoordinator: ReminderCoordinator

    @Inject lateinit var goalNotifier: GoalNotifier

    @Inject lateinit var appSettings: AppSettingsRepository

    override fun onCreate() {
        super.onCreate()
        // An existing database file means this is an update: skip the first-launch food setup. Must run
        // before anything opens Room (injection doesn't; requestRearm only posts to a channel).
        appSettings.initFoodSetup(freshInstall = !getDatabasePath(IronQuestDatabase.NAME).exists())
        reminderNotifier.createChannel()
        goalNotifier.createChannel()
        // Every process start re-arms: covers updates, force-stops and anything a receiver missed.
        reminderCoordinator.requestRearm()
        SafetyRearmReceiver.schedule(this)
    }
}
