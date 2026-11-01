package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class ChallengeWallMarksTest {
    @Test fun landmarkFacesKeepTheirOwnSideOfTheMazeAcrossSaves() {
        val map = Dungeon(9,9,MutableList(81) { Cell() },4,4,0)
        for (y in listOf(3,4,6,7)) map.cell(4,y).wall = false
        assertEquals(40,ChallengeStages.wallRuneIndex(map,4,5,4,4))
        assertEquals(58,ChallengeStages.wallRuneIndex(map,4,5,4,6))
        assertNotEquals(ChallengeStages.rune(40),ChallengeStages.rune(58))
        assertNull(ChallengeStages.wallRuneIndex(map,5,4,4,4))
        assertNull(ChallengeStages.wallRuneIndex(map,4,4,5,4))
        assertNull(ChallengeStages.wallRuneIndex(map,4,5,3,4))
        val restored = SaveCodec.decode(SaveCodec.encode(State(dungeon=map))).dungeon!!
        assertEquals(40,ChallengeStages.wallRuneIndex(restored,4,5,4,4))
        assertEquals(58,ChallengeStages.wallRuneIndex(restored,4,5,4,6))
    }

    @Test fun onlyCornersAndDeadEndsHaveOneMarkedFace() {
        for (mask in 0..15) {
            val map = Dungeon(9,9,MutableList(81) { Cell() },4,4,0)
            map.here().wall = false
            val open = (0..3).filter { mask and (1 shl it) != 0 }
            for (d in open) map.cell(4+MapGenerator.dx[d],4+MapGenerator.dy[d]).wall = false
            val marked = (0..3).filter { ChallengeStages.wallRuneIndex(map,4+MapGenerator.dx[it],4+MapGenerator.dy[it],4,4) != null }
            val landmark = open.size == 1 || open.size == 2 && (open[0]+2)%4 != open[1]
            assertEquals("Neighbour mask $mask",if (landmark) 1 else 0,marked.size)
            if (open.size == 1) assertEquals(listOf((open.single()+2)%4),marked)
        }
    }

    @Test fun paintedSymbolsAndNumbersMatchExistingCluesWithoutChangingSavedLocations() {
        for (i in 0..120) {
            val previous = "${ChallengeStages.runeNames[(i*7+i/11)%6]}-${i%17+1}"
            assertEquals(previous,ChallengeStages.rune(i))
            assertEquals(previous,"${ChallengeStages.runeNames[ChallengeStages.runeSymbol(i)]}-${ChallengeStages.runeNumber(i)}")
        }
    }
}
