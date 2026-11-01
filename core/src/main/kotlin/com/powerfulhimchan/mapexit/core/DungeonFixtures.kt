package com.powerfulhimchan.mapexit.core

import kotlin.math.*

data class VisibleFixture(val x: Int, val y: Int, val event: Event, val depth: Float, val lateral: Float)

object DungeonFixtures {
    private fun active(map: Dungeon): Boolean = (!map.here().resolved || map.here().event == Event.HEAL) &&
        map.here().event in listOf(Event.HEAL, Event.EXIT, Event.PORTAL, Event.CHALLENGE, Event.FORGE) &&
        (map.arrivalDirection !in 0..3 || map.direction == map.arrivalDirection)

    /** Limit approach progressively, so arriving at an altar never makes it shrink again. */
    fun camera(map: Dungeon, fallback: DungeonViewPose): DungeonViewPose {
        if (!active(map) || map.arrivalDirection !in 0..3) return fallback
        val d=map.arrivalDirection; val dx=MapGenerator.dx[d]; val dy=MapGenerator.dy[d]
        if (map.cell(map.x-dx,map.y-dy).wall) return fallback
        val depth=(map.x+.5f-fallback.x)*dx+(map.y+.5f-fallback.y)*dy
        val shift=(.7f-depth).coerceAtLeast(0f)
        return fallback.copy(x=fallback.x-dx*shift,y=fallback.y-dy*shift)
    }

    @Suppress("UNUSED_PARAMETER")
    fun focal(map: Dungeon, width: Int, height: Int): Float = min(width/1.4f,height*.70f)

    fun doorWall(map: Dungeon, x: Int, y: Int): Pair<Int,Int>? {
        val entrances=(0..3).filter { !map.cell(x+MapGenerator.dx[it],y+MapGenerator.dy[it]).wall }
        if (entrances.size!=1) return null
        val d=(entrances.single()+2)%4
        return x+MapGenerator.dx[d] to y+MapGenerator.dy[d]
    }

    fun doorOnFace(map: Dungeon, wallX: Int, wallY: Int, fromX: Int, fromY: Int): Boolean {
        val entrance = map.cell(fromX, fromY)
        return !entrance.wall && !entrance.resolved && entrance.event == Event.EXIT &&
            doorWall(map, fromX, fromY) == (wallX to wallY)
    }

    fun spriteSpan(event: Event, focal: Float, depth: Float, width: Int, height: Int, aspect: Float): Float {
        val worldHeight = when (event) {
            Event.CHEST -> .32f
            Event.MONSTER -> .46f
            Event.MERCHANT, Event.NPC, Event.ELITE -> .64f
            Event.HEAL -> .60f
            Event.FORGE -> .66f
            Event.PORTAL -> .68f
            Event.CHALLENGE -> .45f
            Event.TRAP -> .12f
            else -> .60f
        }
        return minOf(worldHeight * focal / depth.coerceAtLeast(.12f), height * .56f, width * .56f / aspect)
    }

    fun visible(map: Dungeon, camera: DungeonViewPose): List<VisibleFixture> {
        val dx=cos(camera.angle);val dy=sin(camera.angle)
        return map.cells.mapIndexedNotNull { index,c ->
            if(c.wall || (c.resolved && c.event != Event.HEAL) || c.event==Event.NONE) return@mapIndexedNotNull null
            val x=index%map.width;val y=index/map.width
            val vx=x+.5f-camera.x;val vy=y+.5f-camera.y
            val depth=vx*dx+vy*dy;val lateral=-vx*dy+vy*dx
            if(depth<=.12f || depth>12 || abs(lateral)>depth*.9f+.6f) return@mapIndexedNotNull null
            val steps=(hypot(vx,vy)*24).toInt().coerceAtLeast(1)
            for(i in 1..steps) {
                val t=i.toFloat()/steps
                if(map.cell(floor(camera.x+vx*t).toInt(),floor(camera.y+vy*t).toInt()).wall) return@mapIndexedNotNull null
            }
            VisibleFixture(x,y,c.event,depth,lateral)
        }.sortedByDescending { it.depth }
    }
}
