package com.powerfulhimchan.mapexit

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import org.junit.Assert.*
import org.junit.Test

class Audio054Test {
    @Test fun everySelectedAssetIsBundledInTheExpectedContainer() {
        val resources=InstrumentationRegistry.getInstrumentation().targetContext.resources
        assertEquals(42,AudioResources.sounds.size);assertEquals(8,AudioResources.music.size)
        for((ids,signature) in listOf(AudioResources.sounds.values to "RIFF",AudioResources.music.values to "OggS")) {
            ids.forEach { id->resources.openRawResource(id).use { stream->
                val bytes=ByteArray(4);assertEquals(4,stream.read(bytes));assertEquals(signature,String(bytes,Charsets.US_ASCII))
            } }
        }
    }
    @Test fun musicStopsInBackgroundAndResumesAtTheCurrentFloor() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario->
            scenario.onActivity {
                it.game.storyPlaybackEnabled=false
                it.game.engine=GameEngine(State());it.game.engine.enterFloor(11)
                it.startGame();it.game.refresh()
                assertEquals("dungeon_2",it.game.gameMusicCue);assertTrue(it.game.gameMusicPlaying);assertFalse(it.introMusicPlaying)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity{assertFalse(it.game.gameMusicPlaying)}
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity{assertEquals("dungeon_2",it.game.gameMusicCue);assertTrue(it.game.gameMusicPlaying)}
        }
    }
}
