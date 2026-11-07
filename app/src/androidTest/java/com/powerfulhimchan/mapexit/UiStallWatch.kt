package com.powerfulhimchan.mapexit

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Diagnose blocked UI synchronization without changing the test thread or timeout. */
class UiStallWatch : TestWatcher() {
    private var monitor: ScheduledExecutorService? = null
    override fun starting(description: Description) {
        monitor = Executors.newSingleThreadScheduledExecutor().also { executor ->
            executor.scheduleAtFixedRate({
                Thread.getAllStackTraces().forEach { (thread, frames) ->
                    if (thread.name == "main" || thread.name.contains("Instr", ignoreCase = true))
                        Log.w("System.err", "UI wait: ${description.methodName} / ${thread.name}\n" + frames.joinToString("\n"))
                }
            }, 35, 35, TimeUnit.SECONDS)
        }
    }
    override fun finished(description: Description) {
        monitor?.shutdownNow()
        monitor = null
    }
}

/**
 * Game animation intentionally keeps scheduling frames. ActivityScenario.onActivity waits for
 * global idleness before posting, so software-rendered devices can starve that wait indefinitely.
 * Post directly to the main thread and resolve the current instance (including after recreation).
 * Assertions still run on the UI thread and their original failures propagate to the test runner.
 */
internal fun ActivityScenario<MainActivity>.onGameActivity(
    action: (MainActivity) -> Unit
): ActivityScenario<MainActivity> {
    check(state != Lifecycle.State.DESTROYED) { "Scenario is already destroyed" }
    var result: Result<Unit>? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        result = runCatching {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            val activities = Stage.values()
                .filter { it != Stage.DESTROYED && it != Stage.PRE_ON_CREATE }
                .flatMap { monitor.getActivitiesInStage(it) }
                .filterIsInstance<MainActivity>()
                .filter { !it.isDestroyed && !it.isFinishing }
                .distinct()
            check(activities.size == 1) { "Expected one live game activity; found ${activities.size}" }
            action(activities.single())
        }
    }
    checkNotNull(result).getOrThrow()
    return this
}
