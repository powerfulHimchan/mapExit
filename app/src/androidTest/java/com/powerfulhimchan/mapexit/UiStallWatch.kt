package com.powerfulhimchan.mapexit

import android.util.Log
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
