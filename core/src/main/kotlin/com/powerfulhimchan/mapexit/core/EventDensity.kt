package com.powerfulhimchan.mapexit.core

/** Fractional counts are rounded probabilistically, once when a new floor is generated. */
object EventDensity {
    fun count(s: State, baseline: Int, percent: Int): Int {
        require(baseline >= 0 && percent in 0..100)
        val scaled = baseline * percent
        val remainder = scaled % 100
        return scaled / 100 + if (remainder > 0 && s.nextInt(100) < remainder) 1 else 0
    }
}
