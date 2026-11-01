package com.powerfulhimchan.mapexit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State

/** Replaces gameplay composition while reading: no live timers or hidden touch targets. */
@Composable
internal fun StoryScreen(vm: GameModel) {
    vm.revision
    val scene = vm.storyScene ?: return
    val page = vm.storyPage.coerceIn(0,scene.lines.lastIndex)
    val line = scene.lines[page]
    val scroll = rememberLazyListState()
    LaunchedEffect(scene.id,page) { scroll.scrollToItem(0) }
    BackHandler { vm.closeStory() }
    BoxWithConstraints(Modifier.fillMaxSize().background(ink).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
        val illustrationHeight = if (maxHeight < 520.dp) 112.dp else 190.dp
        Column(Modifier.fillMaxSize(),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if(vm.replayingStory) "이야기 다시 보기" else "한 층만 더 · 이야기",color = mint,fontSize = 12.sp)
                Text("${page+1} / ${scene.lines.size}",color = muted,fontSize = 12.sp)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),state = scroll,verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(scene.title,color = pale,fontSize = 25.sp,fontWeight = FontWeight.Black,
                    modifier = Modifier.semantics { heading() }) }
                item { Canvas(Modifier.fillMaxWidth().height(illustrationHeight).border(1.dp,StoryBorder)
                    .semantics { contentDescription = "이야기 장면 · ${scene.title}" }) { storyArt(scene.art,vm.engine.state) } }
                item {
                    Column(Modifier.fillMaxWidth().background(panel).border(1.dp,StoryBorder).padding(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(line.speaker,color = amber,fontSize = 14.sp,fontWeight = FontWeight.Bold)
                        Text(line.text,color = pale,fontSize = 17.sp,lineHeight = 28.sp)
                    }
                }
                item { Text("기록 → 이야기에서 다시 읽을 수 있습니다.",color = muted,fontSize = 11.sp) }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(onClick = vm::closeStory,modifier = Modifier.weight(1f)) {
                    Text(if(vm.replayingStory) "기록으로" else "나중에 읽기",color = muted)
                }
                if(page>0) GameButton(onClick = vm::previousStory) { Text("이전") }
                GameButton(onClick = vm::nextStory,modifier = Modifier.weight(1f)
                    .semantics { contentDescription = "이야기 다음" }) {
                    Text(if(page<scene.lines.lastIndex) "다음" else if(vm.replayingStory) "기록으로" else "계속하기",color = mint)
                }
            }
        }
    }
}

private val StoryBorder = Color(0xFF3D4B54)

internal fun LazyListScope.storyRecordCards(vm: GameModel) {
    val scenes = Story.history(vm.engine.state)
    item { Text("목표 · ${Story.objective(vm.engine.state)}",color = amber,fontSize = 13.sp,lineHeight = 20.sp) }
    item { Text("발견한 이야기 ${scenes.size}개 · 새로 발견한 순서",color = muted,fontSize = 12.sp) }
    if(scenes.isEmpty()) item { Text("아직 발견한 이야기가 없습니다.",color = pale) }
    items(scenes,key = { it.id }) { scene ->
        Column(Modifier.fillMaxWidth().background(panel).padding(12.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(scene.title,color = pale,fontSize = 16.sp,fontWeight = FontWeight.Bold)
            GameButton(onClick = { vm.replayStory(scene.id) },modifier = Modifier.fillMaxWidth()
                .semantics { contentDescription = "이야기 다시 보기 · ${scene.title}" }) { Text("다시 읽기",color = mint) }
        }
    }
}

/** Authored using the game's existing pixel materials and character atlas. */
internal fun DrawScope.storyArt(art: String, state: State) {
    val w = size.width; val h = size.height
    drawRect(ink)
    masonry(0f,0f,w,h*.72f,Color(0xFF26343E))
    stoneFloor(.70f)
    torch(w*.10f,h*.32f,0)
    torch(w*.86f,h*.32f,170)
    when(art) {
        "lantern" -> {
            val width = (w*.14f).coerceAtLeast(32f)
            sprite("npc",w*.63f,h*.89f-spriteHeight("npc",width),width)
            adventurer(state,w*.27f,h*.92f,width,false)
            torch(w*.55f,h*.64f,280)
        }
        "contract", "record" -> {
            pixelRect(Color(0xFF3D4248),w*.32f,h*.67f,w*.36f,h*.20f)
            pixelRect(Color(0xFF9A7751),w*.31f,h*.25f,w*.38f,h*.43f)
            pixelRect(Color(0xFFE9D6A7),w*.33f,h*.27f,w*.34f,h*.38f)
            for(i in 0..4) pixelRect(Color(0xFF7A6450),w*.38f,h*(.33f+i*.045f),w*(if(i==4) .13f else .24f),2f)
            if(art=="contract") { drawCircle(amber.copy(alpha=.18f),w*.13f,Offset(w*.50f,h*.49f)); pixelRect(amber,w*.46f,h*.47f,w*.08f,4f) }
        }
        "flame", "fragment" -> {
            val color = if(art=="fragment") mint else amber
            drawCircle(color.copy(alpha=.08f),h*.38f,Offset(w*.5f,h*.48f))
            drawCircle(color.copy(alpha=.16f),h*.23f,Offset(w*.5f,h*.48f))
            for(i in 0..6) {
                val span = if(i<=3) i+1 else 7-i
                pixelRect(color,w*.5f-span*4,h*.25f+i*8,span*8f,8f)
            }
            pixelRect(Color(0xFFFFF0C2),w*.5f-4,h*.41f,8f,16f)
        }
        else -> {
            val violet = Color(0xFFAC80DA)
            val color = if(art=="possessed") violet else amber
            pixelRect(Color(0xFF53616B),w*.28f,h*.09f,w*.44f,h*.73f)
            pixelRect(Color(0xFF0A101B),w*.30f,h*.12f,w*.40f,h*.69f)
            pixelRect(color.copy(alpha=.17f),w*.59f,h*.13f,w*.06f,h*.66f)
            pixelRect(color,w*.61f,h*.14f,4f,h*.66f)
            if(art in listOf("chains","guardian","possessed")) {
                for(i in 0..9) {
                    val x=w*(.31f+i*.038f); val y=h*(.26f+i*.034f)
                    pixelRect(Color(0xFF81929C),x,y,10f,4f)
                    pixelRect(Color(0xFF35454F),x+2,y+2,6f,4f)
                }
            }
            if(art in listOf("guardian","possessed")) {
                val width=(w*.18f).coerceAtLeast(32f)
                val x=w*.5f-width*.5f; val y=h*.94f-spriteHeight("monster_4_boss",width)
                if(art=="possessed") drawOval(violet.copy(alpha=.25f),Offset(x-8,y-6),Size(width+20,h*.73f))
                sprite("monster_4_boss",x,y,width)
            } else adventurer(state,w*.40f,h*.94f,(w*.14f).coerceAtLeast(32f),false)
        }
    }
}
