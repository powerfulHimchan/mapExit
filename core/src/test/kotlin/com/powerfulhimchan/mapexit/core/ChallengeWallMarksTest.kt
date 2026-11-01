package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class ChallengeWallMarksTest {
    @Test fun wallFacesKeepTheirOwnSideOfTheMazeAcrossSaves() {
        val map = Dungeon(9,9,MutableList(81) { Cell() },4,4,0)
        map.here().wall = false; map.cell(6,4).wall = false
        for (d in 0..3) assertEquals(40, ChallengeStages.wallRuneIndex(map,
            4+MapGenerator.dx[d],4+MapGenerator.dy[d],4,4))
        assertEquals(42,ChallengeStages.wallRuneIndex(map,5,4,6,4))
        assertNotEquals(ChallengeStages.rune(40),ChallengeStages.rune(42))
        assertNull(ChallengeStages.wallRuneIndex(map,4,4,5,4))
        assertNull(ChallengeStages.wallRuneIndex(map,5,4,4,3))
        val saved = State(dungeon=map)
        val restored = SaveCodec.decode(SaveCodec.encode(saved)).dungeon!!
        assertEquals(40,ChallengeStages.wallRuneIndex(restored,5,4,4,4))
        assertEquals(42,ChallengeStages.wallRuneIndex(restored,5,4,6,4))
    }

    @Test fun paintedSymbolsAndNumbersMatchExistingCluesWithoutChangingSavedLocations() {
        for (i in 0..120) {
            val previous = "${ChallengeStages.runeNames[(i*7+i/11)%6]}-${i%17+1}"
            assertEquals(previous,ChallengeStages.rune(i))
            assertEquals(previous,"${ChallengeStages.runeNames[ChallengeStages.runeSymbol(i)]}-${ChallengeStages.runeNumber(i)}")
        }
    }
}
