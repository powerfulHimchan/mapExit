package com.powerfulhimchan.mapexit.core

enum class DungeonTheme(val wall: Int, val ceiling: Int, val floor: Int, val grout: Int, val accent: Int, val light: Int) {
    MOSS(0xff435c68.toInt(),0xff24343d.toInt(),0xff293a42.toInt(),0xff111d25.toInt(),0xff66875a.toInt(),0xffffb75b.toInt()),
    TIDAL(0xff365f72.toInt(),0xff1c3445.toInt(),0xff284a5b.toInt(),0xff0f2534.toInt(),0xff6bc1d2.toInt(),0xff8edbe7.toInt()),
    CRYSTAL(0xff625878.toInt(),0xff302d46.toInt(),0xff423951.toInt(),0xff201b32.toInt(),0xffb494e1.toInt(),0xffd6b5ff.toInt()),
    EMBER(0xff685044.toInt(),0xff3a2728.toInt(),0xff46302e.toInt(),0xff24181e.toInt(),0xffff8755.toInt(),0xffffae67.toInt()),
    ABYSS(0xff414865.toInt(),0xff202337.toInt(),0xff2c3047.toInt(),0xff121627.toInt(),0xff7d9fda.toInt(),0xff9bbded.toInt());
    companion object { fun forFloor(floor: Int) = entries[((floor.coerceIn(1,50)-1)/10)] }
}
