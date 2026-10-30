package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class RecordsBestiaryNavigationTest {
    @Test fun bestiaryIsOnlyReachedInsideRecordsAndBackReturnsToItsParent() {
        for(dungeon in listOf(false,true)) {
            val e=GameEngine()
            if(dungeon) e.enterFloor(1)
            val parentMode=e.state.mode
            assertFalse(e.choices().any { it.action=="bestiary" })
            if(dungeon) assertFalse(e.openDungeonPanel("bestiary"))
            e.choose(e.choices().first { it.action=="help" })
            assertEquals("help",e.state.menu?.type)
            e.choose(e.choices().first { it.action=="bestiary" })
            assertEquals("bestiary",e.state.menu?.type)
            val restored=GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
            restored.navigateBack();assertEquals("help",restored.state.menu?.type)
            restored.navigateBack();assertEquals(parentMode,restored.state.mode)
        }
    }
}
