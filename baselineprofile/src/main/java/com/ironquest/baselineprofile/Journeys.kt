package com.ironquest.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** Mirrors `MainActivity.EXTRA_SKIP_ONBOARDING`: tips would block the scripted taps. */
// not UI text: intent extra name
const val SKIP_ONBOARDING_EXTRA = "com.ironquest.app.SKIP_ONBOARDING"

private fun text(label: String): Pattern = Pattern.compile("(?i)" + Pattern.quote(label))

/** Waits for a text (case-insensitive) and returns it, or null. */
private fun MacrobenchmarkScope.find(label: String, timeoutMs: Long = 5_000): UiObject2? {
    device.wait(Until.hasObject(By.text(text(label))), timeoutMs)
    return device.findObject(By.text(text(label)))
}

private fun MacrobenchmarkScope.tap(label: String, timeoutMs: Long = 5_000): Boolean {
    val o = find(label, timeoutMs) ?: return false
    return runCatching {
        o.click()
        true
    }.getOrDefault(false)
}

/**
 * Walks the main screens. Never taps "Log set": a workout row is only created on the first logged set,
 * so opening a workout and backing out writes nothing. Every step skips when its element is missing.
 */
fun MacrobenchmarkScope.criticalJourneys() {
    pressHome()
    startActivityAndWait { intent -> intent.putExtra(SKIP_ONBOARDING_EXTRA, true) }

    tap("Log")
    device.waitForIdle()

    if (tap("Food")) {
        device.waitForIdle()
        if (tap("Meals", 2_000)) {
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }
        tap("Week", 2_000)
        device.waitForIdle()
        tap("Month", 2_000)
        device.waitForIdle()
        tap("Day", 2_000)
        device.waitForIdle()
    }

    if (tap("You")) {
        device.waitForIdle()
        tap("Goals", 2_000)
        device.waitForIdle()
    }

    if (tap("Today")) {
        device.waitForIdle()
        val start = find("Start workout", 3_000) ?: find("Resume workout", 1_000)
        if (start != null && runCatching { start.click() }.isSuccess) {
            device.waitForIdle()
            val more = device.wait(Until.findObject(By.desc("More weight")), 3_000)
            if (more != null) {
                repeat(3) {
                    runCatching { more.click() }
                    device.waitForIdle()
                }
            }
            runCatching {
                device.swipe(
                    device.displayWidth * 9 / 10,
                    device.displayHeight / 2,
                    device.displayWidth / 10,
                    device.displayHeight / 2,
                    20,
                )
            }
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }
    }
}
