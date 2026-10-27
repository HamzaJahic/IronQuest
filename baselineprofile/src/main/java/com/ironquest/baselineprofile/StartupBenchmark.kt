package com.ironquest.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = "com.ironquest.app",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        // Same as the journeys, so cold-start numbers stay comparable with the ones before tips.
        startActivityAndWait { intent -> intent.putExtra(SKIP_ONBOARDING_EXTRA, true) }
    }
}
