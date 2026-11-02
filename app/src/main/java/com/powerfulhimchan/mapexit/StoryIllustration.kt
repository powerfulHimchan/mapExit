package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.min

/** Fixed scene coordinates keep people, paper and doorways readable at every screen size. */
internal fun DrawScope.storyArt(art: String, state: State, page: Int = 0) {
    drawRect(Color(0xFF101B28))
    val unit = min(size.width / 384f,size.height / 224f)
    clipRect {
        withTransform({
            translate((size.width-384f*unit)/2,(size.height-224f*unit)/2)
            scale(unit,unit,Offset.Zero)
        }) { StoryPainter(this,state).paint(art,page) }
    }
}

internal fun storyArtCaption(art: String): String = when(art) {
    "lantern" -> "던전 입구 · 등불지기가 건네는 꺼지지 않는 불씨"
    "contract" -> "10층 제단 · 계약서에 새겨진 나의 이름"
    "record" -> "이전 계약자가 남긴 낡은 기록"
    "flame" -> "부활의 불씨 · 돌아온 몸과 남겨진 장비"
    "ember" -> "가슴에 깃든 불씨 · 아래로 이어지는 빛"
    "fragment" -> "봉인의 파편 · 영혼에 새기는 장비"
    "chains" -> "마지막 문을 붙잡고 있는 봉인의 사슬"
    "guardian" -> "50층 · 봉인된 문을 지키는 마지막 문지기"
    "possessed" -> "50층 · 회수자의 사슬에 붙잡힌 문지기"
    "door" -> "열린 봉인 · 문지기에게 뻗어 오는 어둠"
    "footprints" -> "먼지 위에 남은, 아래로 향하는 발자국"
    "stairs" -> "지상으로 오르는 계단과 더 깊이 내려가는 길"
    "names" -> "벽에 남겨진 수많은 계약자의 이름"
    "graves" -> "끝내 잠들지 못한 이들의 빈 무덤"
    "water" -> "고인 물에 비친 등불과 낯선 그림자"
    "ledger" -> "사람의 이름과 날짜를 적어 둔 거래 장부"
    "corridor" -> "같은 흔적이 되풀이되는 긴 복도"
    "lanterns" -> "돌아오지 못한 주인의 등불과 목각 인형"
    "letter" -> "돌아오지 않은 아들이 남긴 편지"
    "prayer" -> "아래를 향해 사슬을 쥔 석상들"
    "sealed_door" -> "심연의 문 · 틈 너머에서 들려오는 숨소리"
    else -> "던전 깊은 곳 · 아직 닫히지 않은 문"
}

/** Quiet backgrounds, bright silhouettes and large narrative props; no random or live effects. */
private class StoryPainter(private val d: DrawScope, private val state: State) {
    private val dark=Color(0xFF0B1422)
    private val stone=Color(0xFF354655)
    private val edge=Color(0xFF72858D)
    private val gold=Color(0xFFF1BE68)
    private val paper=Color(0xFFF2DCA8)
    private val teal=Color(0xFF91E6CD)
    private val purple=Color(0xFFC49AF5)
    private fun r(x:Int,y:Int,w:Int,h:Int,c:Color) = d.drawRect(c,Offset(x.toFloat(),y.toFloat()),Size(w.toFloat(),h.toFloat()))
    private fun shape(c:Color,vararg xy:Int) {
        val p=Path();p.moveTo(xy[0].toFloat(),xy[1].toFloat())
        for(i in 2 until xy.size step 2) p.lineTo(xy[i].toFloat(),xy[i+1].toFloat())
        p.close();d.drawPath(p,c)
    }
    private fun line(x:Int,y:Int,xx:Int,yy:Int,c:Color,width:Float=1f) =
        d.drawLine(c,Offset(x.toFloat(),y.toFloat()),Offset(xx.toFloat(),yy.toFloat()),width)
    private fun glow(x:Int,y:Int,c:Color,radius:Int) {
        for(i in 3 downTo 1) d.drawCircle(c.copy(alpha=.035f+i*.009f),radius*i/3f,Offset(x.toFloat(),y.toFloat()))
    }
    private fun shadow(x:Int,y:Int,w:Int) = d.drawOval(dark.copy(alpha=.8f),Offset(x.toFloat(),y.toFloat()),Size(w.toFloat(),9f))
    private fun star(x:Int,y:Int,c:Color) {r(x-3,y,7,1,c);r(x,y-3,1,7,c);r(x-1,y-1,3,3,c)}
    private fun room(c:Color=stone) {
        r(0,0,384,224,dark)
        for(row in 0..7) for(col in -1..8) {
            val x=col*48+(row%2)*24;val y=row*22
            r(x+1,y+1,46,20,c.copy(alpha=if((row+col)%3==0).58f else .46f))
            r(x+2,y+2,44,1,edge.copy(alpha=.16f))
        }
        r(0,165,384,59,Color(0xFF23323E))
        for(y in listOf(172,188,212)) r(0,y,384,2,edge.copy(alpha=.22f))
        for(x in -160..544 step 64) line(192,155,x,224,edge.copy(alpha=.18f))
        for(x in listOf(12,350)) {
            r(x,0,22,165,dark);r(x+2,0,17,165,c);r(x+4,0,3,163,edge.copy(alpha=.4f))
            for(y in 12..150 step 32) r(x+1,y,20,3,dark.copy(alpha=.7f))
            r(x-3,155,28,13,c);r(x-4,154,30,3,edge)
        }
    }
    private fun hero(x:Int,baseline:Int=208,height:Int=106,back:Boolean=false) {
        shadow(x-height/3,baseline-2,height*2/3)
        d.withTransform({translate(x-height/3f,baseline-height.toFloat());scale(height/48f,height/48f,Offset.Zero)}) {
            sprite(if(back)"heroBack" else "hero",0f,0f,32f)
        }
    }
    private fun lantern(x:Int,y:Int,lit:Boolean=true) {
        if(lit) glow(x+9,y+15,gold,44)
        r(x+5,y-5,9,2,edge);r(x+3,y-3,2,7,edge);r(x+13,y-3,2,7,edge)
        r(x,y+4,19,3,dark);r(x+2,y+7,15,20,Color(0xFF705337))
        r(x+5,y+8,9,17,if(lit)gold else Color(0xFF293C44))
        if(lit){r(x+8,y+11,4,11,Color(0xFFFFF3C5));r(x+9,y+6,2,7,Color(0xFFFFF3C5))}
        r(x+1,y+27,17,4,Color(0xFFC29353));r(x+4,y+7,2,20,Color(0xFFAF8856));r(x+13,y+7,2,20,Color(0xFFAF8856))
    }
    private fun elder(dim:Boolean=false) {
        shadow(250,202,84)
        shape(dark,266,62,307,62,320,95,326,193,253,193,258,95)
        shape(Color(0xFF6B7885),271,65,300,65,311,90,312,115,261,115,262,90)
        shape(Color(0xFFB1B9B6),275,70,297,70,305,88,267,88)
        r(272,84,28,24,Color(0xFFDCB48C));r(273,85,25,5,Color(0xFFF1D4AF))
        r(274,93,7,3,dark);r(291,93,6,3,dark);r(284,95,4,7,Color(0xFFAE7A5B))
        shape(Color(0xFFD8DFD0),271,103,281,107,288,104,301,103,298,122,288,133,276,122)
        r(281,110,3,14,Color(0xFFF0EDDA));r(292,109,2,12,Color(0xFF99A69F))
        shape(Color(0xFF4C6470),263,116,276,124,289,140,301,123,312,116,319,184,257,184)
        shape(Color(0xFF81938D),263,117,273,127,279,180,263,181,256,170)
        r(286,138,4,47,Color(0xFFAEBDB0));r(260,179,56,7,Color(0xFF44372D));r(284,178,9,9,gold)
        r(266,186,16,15,Color(0xFF26343D));r(295,186,16,15,Color(0xFF26343D))
        r(260,200,24,7,Color(0xFF8F7150));r(294,200,24,7,Color(0xFF8F7150))
        // An outstretched hand makes the exchange legible without a speech bubble.
        shape(Color(0xFF81938D),260,127,264,146,243,157,219,151,221,139,243,140)
        r(213,140,14,10,Color(0xFFF1D4AF));lantern(205,150,!dim)
    }
    private fun door(open:Boolean=false) {
        r(112,20,160,166,dark);r(105,46,14,140,stone);r(265,46,14,140,stone)
        r(117,27,150,13,edge);r(119,41,146,8,stone)
        shape(stone,102,48,117,20,267,20,282,48,264,48,256,36,128,36,120,48)
        r(122,48,140,134,Color(0xFF142330));r(125,49,62,132,Color(0xFF26394A));r(197,49,62,132,Color(0xFF223547))
        for(x in listOf(128,204)) {r(x,52,48,4,edge.copy(alpha=.5f));r(x,59,3,115,edge.copy(alpha=.3f));r(x,168,48,5,dark)}
        if(open) {
            shape(Color(0xFF0A0817),176,47,211,47,229,181,160,181)
            line(176,48,160,181,purple,3f);line(211,48,229,181,purple,3f)
            glow(192,122,purple,105)
        } else {r(189,48,6,134,gold.copy(alpha=.35f));r(191,50,2,130,gold)}
        r(103,182,178,6,edge);r(93,190,198,7,stone);r(83,199,218,9,edge.copy(alpha=.6f))
        for(x in listOf(134,241)) {r(x,111,8,15,Color(0xFF9F7E53));r(x+2,113,4,9,dark)}
    }
    private fun chain(x:Int,y:Int,xx:Int,yy:Int,c:Color=edge) {
        val count=14
        for(i in 0..count) {
            val a=x+(xx-x)*i/count;val b=y+(yy-y)*i/count
            r(a-4,b-2,9,6,dark);r(a-3,b-2,7,2,c);r(a-3,b+2,7,2,c)
            r(a-4,b-1,2,4,c);r(a+3,b-1,2,4,c)
        }
    }
    private fun guardian(possessed:Boolean=false,kneeling:Boolean=false) {
        val x=if(kneeling)211 else 222;val y=if(kneeling)212 else 207;val height=if(kneeling)102 else 145
        shadow(x-46,y-2,92)
        if(possessed)glow(x,y-80,purple,96)
        d.withTransform({translate(x-height/3f,y-height.toFloat());scale(height/48f,height/48f,Offset.Zero)}) {
            sprite("monster_4_boss",0f,0f,32f)
        }
        // Long weapon and chest seal clearly separate a guardian from a door decoration.
        r(x+45,y-121,5,123,Color(0xFF8C6950));r(x+46,y-119,2,121,gold)
        shape(Color(0xFFB9C9CE),x+34,y-120,x+34,y-144,x+63,y-144,x+72,y-136,x+67,y-120)
        r(x+36,y-143,26,3,Color(0xFFE4ECDA))
        if(possessed) {
            chain(147,46,x-20,y-68,purple);chain(290,45,x+19,y-69,purple)
            star(x-6,y-98,purple);star(x+15,y-98,purple)
            for(i in 0..6) {r(144+i*17,66+(i%3)*21,3,5,purple.copy(alpha=.8f))}
        }
    }
    private fun scroll(contract:Boolean=false,letter:Boolean=false) {
        shadow(71,197,242)
        r(89,181,208,15,Color(0xFF4C5A66));r(102,195,181,13,Color(0xFF2F414E))
        r(100,176,181,4,edge)
        shape(Color(0xFF5F4635),90,38,288,38,302,169,78,169)
        shape(paper,99,45,279,45,291,163,89,163)
        shape(Color(0xFFCBAE78),99,45,107,45,98,163,89,163)
        r(95,37,190,9,Color(0xFFC49B64));r(90,36,8,12,gold);r(283,36,8,12,gold)
        r(83,163,210,8,Color(0xFFC49B64));r(78,161,8,12,gold);r(292,161,8,12,gold)
        for(y in listOf(67,78,89)) {
            r(120,y,118+(y%3)*9,2,Color(0xFF8B7151))
            for(i in 0..5)r(123+i*20,y-3,8+(i%2)*4,2,Color(0xFF8B7151))
        }
        r(120,106,139,2,Color(0xFFB3976B))
        if(contract) {
            glow(191,128,gold,95)
            r(123,117,133,26,Color(0xFFBD955C));r(126,120,127,20,Color(0xFFFFEDBD))
            // A signature, circular red seal and fresh sparks replace anonymous horizontal bars.
            line(139,134,148,124,Color(0xFF8C4538),2f);line(148,124,151,135,Color(0xFF8C4538),2f)
            line(155,131,172,125,Color(0xFF8C4538),2f);line(166,125,161,136,Color(0xFF8C4538),2f)
            line(178,134,189,123,Color(0xFF8C4538),2f);line(190,134,203,128,Color(0xFF8C4538),2f)
            d.drawCircle(Color(0xFF9E463C),9f,Offset(239f,130f));d.drawCircle(gold,5f,Offset(239f,130f))
            star(112,113,gold);star(273,111,gold);star(262,145,gold)
            chain(40,185,98,149);chain(288,148,344,185)
        } else if(letter) {
            shape(Color(0xFFBA9B6D),244,144,271,118,279,158)
            r(120,121,89,2,Color(0xFF8B7151));r(120,133,121,2,Color(0xFF8B7151))
            r(165,147,64,2,Color(0xFF8B7151))
        } else {
            for(y in listOf(119,131,143)) r(120,y,if(y==143)90 else 137,2,Color(0xFF8B7151))
        }
        lantern(37,149)
        r(324,114,5,72,Color(0xFF8F7554));shape(paper,321,116,333,88,336,114,329,131)
    }
    private fun crystal(x:Int,y:Int) {
        glow(x,y,teal,72)
        shape(Color(0xFF2C7C83),x,y-35,x+24,y-9,x+14,y+28,x-12,y+32,x-24,y-7)
        shape(teal,x,y-35,x+9,y-7,x,y+29,x-14,y-8)
        shape(Color(0xFFDEFDE8),x,y-35,x+4,y-8,x-2,y+13,x-7,y-9)
        line(x-24,y-7,x+24,y-9,Color(0xFFA9F7E2),1.5f)
        star(x-39,y-18,teal);star(x+39,y+12,teal)
    }
    private fun flameScene() {
        room();r(36,177,136,20,stone);r(28,195,152,10,edge)
        hero(102,184,112)
        glow(103,125,gold,80);r(100,124,5,12,Color(0xFFFFE5A0))
        for(i in 0..7){star(59+(i*31)%100,54+i*17,if(i%2==0)teal else gold)}
        // The gear stays on the far side of the broken return path.
        r(218,176,132,3,edge);lantern(188,164)
        d.withTransform({translate(248f,143f)}) {sprite("sword",0f,0f,32f);sprite("shield",40f,6f,32f)}
        for(i in 0..5) {r(162+i*12,201-i*5,5,2,gold.copy(alpha=1f-i*.12f))}
        line(211,178,222,184,Color(0xFFD1836D),3f);line(222,178,211,184,Color(0xFFD1836D),3f)
    }
    private fun fragmentScene() {
        room();r(73,182,238,15,stone);r(86,176,212,6,edge)
        crystal(193,78)
        // Visible sword imprint inside a luminous seal connects the fragment to equipment.
        glow(191,157,teal,90)
        d.drawOval(teal.copy(alpha=.2f),Offset(105f,147f),Size(174f,32f))
        for(x in listOf(113,145,231,263))star(x,161,teal)
        d.withTransform({translate(166f,112f);scale(1.5f,1.5f,Offset.Zero)}) {sprite("sword",0f,0f,32f)}
        chain(28,35,86,136);chain(299,136,354,35)
    }
    private fun emberScene() {
        room();hero(190,211,153)
        glow(192,139,gold,91)
        shape(Color(0xFFD66F3C),193,113,198,129,208,135,207,148,197,158,184,153,179,142,187,132)
        shape(gold,194,125,198,136,202,140,198,151,188,148,187,141)
        r(191,138,5,12,Color(0xFFFFF0C0))
        for(i in 0..7)star(125+(i*29)%132,48+(i*23)%132,if(i%2==0)gold else teal)
        lantern(65,163)
    }
    private fun corridor(footprints:Boolean=false,stairs:Boolean=false) {
        room()
        for(i in 0..3) {
            val inset=i*24
            r(54+inset,20+i*19,276-inset*2,153-i*17,Color(0xFF1B2C3A))
            r(54+inset,20+i*19,10,153-i*17,edge.copy(alpha=.5f));r(320-inset,20+i*19,10,153-i*17,stone)
            r(54+inset,20+i*19,276-inset*2,7,edge.copy(alpha=.6f))
        }
        r(162,96,60,76,dark)
        if(stairs) {
            for(i in 0..5) {r(162-i*5,113+i*12,60+i*10,6,edge);r(162-i*5,119+i*12,60+i*10,6,stone)}
            glow(192,95,gold,48);r(180,84,24,18,gold.copy(alpha=.3f))
        } else if(footprints) {
            for(i in 0..6) {
                val y=207-i*11;val x=176+(i%2)*22
                r(x,y,if(i<3)8 else 5,if(i<3)5 else 3,Color(0xFFC7B28B));r(x+2,y+7,4,2,Color(0xFFC7B28B))
            }
        } else {r(177,101,3,55,purple);star(215,120,purple)}
        lantern(46,138)
    }
    private fun graves(prayer:Boolean=false) {
        room()
        for(i in 0..2) {
            val x=66+i*99;val top=if(i==1)61 else 83
            shadow(x-10,201,80)
            shape(edge,x,top+16,x+10,top,x+44,top,x+54,top+16,x+54,194,x,194)
            r(x+5,top+18,44,110-(top-61),stone)
            if(prayer){r(x+18,top+21,19,20,edge);shape(edge,x+16,top+44,x+33,top+44,x+43,top+81,x+8,top+81);chain(x+27,top+66,192,204)}
            else {r(x+24,top+28,5,36,edge);r(x+14,top+38,25,5,edge);r(x+12,top+79,30,2,edge);r(x+16,top+86,22,2,edge)}
            r(x-7,194,68,9,edge)
        }
        lantern(27,169)
    }
    private fun water() {
        room(Color(0xFF314F61));hero(85,179,106);lantern(119,140)
        shape(Color(0xFF193C4C),144,162,346,149,365,207,132,219)
        for(i in 0..7) r(153+(i%3)*17,163+i*7,117-(i%2)*30,1,teal.copy(alpha=.25f))
        glow(239,183,teal,55)
        shape(Color(0xFF355B69),228,165,243,161,256,175,253,198,221,198,221,176)
        r(228,175,5,2,teal);r(243,175,5,2,teal);r(225,192,27,2,Color(0xFF193C4C))
    }
    private fun names() {
        room();r(73,29,238,146,Color(0xFF485B66));r(79,35,226,134,Color(0xFF344650))
        for(row in 0..4) for(col in 0..5) {
            val x=94+col*33;val y=51+row*22
            r(x,y,18,2,edge);r(x+5,y-4,2,13,edge);line(x+2,y+9,x+17,y+4,edge)
        }
        r(222,137,54,20,gold.copy(alpha=.22f));line(228,151,239,140,gold,2f);line(239,140,250,151,gold,2f);r(257,140,2,12,gold)
        hero(109,214,98,true);lantern(159,176)
    }
    private fun ledger() {
        room();r(54,176,277,15,Color(0xFF73583E));r(66,191,14,28,Color(0xFF493D32));r(304,191,14,28,Color(0xFF493D32))
        shape(Color(0xFF684D3C),74,57,189,42,310,57,316,169,190,179,68,168)
        shape(paper,82,62,188,48,188,170,76,160);shape(Color(0xFFE0C58E),194,48,302,63,308,160,194,170)
        r(188,48,6,124,Color(0xFF9D784E))
        for(y in 75..145 step 17) {line(94,y,173,y-7,Color(0xFFA28A63));line(211,y-7,290,y,Color(0xFFA28A63))}
        for(x in listOf(121,248))line(x,65,x,152,Color(0xFFA28A63))
        r(209,143,75,10,Color(0xFFBD7453));lantern(30,143)
    }
    private fun lanterns() {
        room();lantern(81,154,state.cycle>1);lantern(294,160,state.cycle>1)
        r(140,171,91,23,Color(0xFF4E4236));r(145,166,81,5,Color(0xFF9C7A51))
        // A wooden child's doll, with head, spread arms and two separate feet.
        r(176,107,21,22,Color(0xFFC79C64));r(178,109,17,4,paper);r(179,117,3,3,dark);r(190,117,3,3,dark)
        r(180,131,15,22,Color(0xFF9E724C));r(164,134,46,6,Color(0xFFC79C64));r(177,153,6,13,Color(0xFFC79C64));r(191,153,6,13,Color(0xFFC79C64))
    }
    fun paint(art:String,page:Int) {
        when(art) {
            "lantern" -> {room();r(52,44,88,130,dark);r(49,42,94,7,edge);for(i in 0..4)r(54,139+i*7,82,3,stone);hero(156,208,121);elder(state.cycle>1)}
            "contract" -> {room();scroll(contract=true)}
            "record" -> {room();scroll()}
            "letter" -> {room();scroll(letter=true)}
            "flame" -> flameScene()
            "ember" -> emberScene()
            "fragment" -> fragmentScene()
            "footprints" -> corridor(footprints=true)
            "stairs" -> corridor(stairs=true)
            "corridor" -> corridor()
            "names" -> names()
            "graves" -> graves()
            "prayer" -> graves(prayer=true)
            "water" -> water()
            "ledger" -> ledger()
            "lanterns" -> lanterns()
            "guardian", "possessed", "door", "chains", "sealed_door" -> {
                room(Color(0xFF3B3F58));door(art in listOf("possessed","door"))
                if(art=="chains") {chain(117,64,264,160);chain(265,64,116,160);crystal(192,113)}
                else if(art=="sealed_door") {hero(84,215,94,true);chain(117,64,264,160);chain(265,64,116,160);glow(192,124,gold,72)}
                else {
                    hero(84,215,94,true)
                    if(art=="door") {
                        for(i in 0..4)shape(purple.copy(alpha=.25f+i*.07f),183+i*5,80+i*9,206+i*7,99+i*9,245,167,223,151)
                        guardian(possessed=page>=2,kneeling=true);crystal(316,163)
                    } else guardian(possessed=art=="possessed")
                }
            }
            else -> corridor()
        }
        // A restrained frame separates the artwork from its caption and dialogue.
        r(0,0,384,2,edge.copy(alpha=.6f));r(0,222,384,2,dark)
        for(x in listOf(5,369)){r(x,6,10,2,gold.copy(alpha=.7f));r(x,6,2,10,gold.copy(alpha=.7f))}
    }
}
