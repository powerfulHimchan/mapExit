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
import androidx.compose.ui.graphics.Color
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
        val illustrationHeight = if (maxHeight < 520.dp) 144.dp else (maxWidth * .60f).coerceIn(200.dp,320.dp)
        Column(Modifier.fillMaxSize(),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if(vm.replayingStory) "이야기 다시 보기" else "한 층만 더 · 이야기",color = mint,fontSize = 12.sp)
                Text("${page+1} / ${scene.lines.size}",color = muted,fontSize = 12.sp)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),state = scroll,verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(scene.title,color = pale,fontSize = 25.sp,fontWeight = FontWeight.Black,
                    modifier = Modifier.semantics { heading() }) }
                item {
                    Column(Modifier.fillMaxWidth().border(1.dp,StoryBorder)) {
                        Canvas(Modifier.fillMaxWidth().height(illustrationHeight)
                            .semantics { contentDescription = "이야기 장면 · ${scene.title}" }) {
                            storyArt(scene.art,vm.engine.state,page)
                        }
                        Text(storyArtCaption(scene.art),color = pale,fontSize = 12.sp,lineHeight = 19.sp,
                            modifier = Modifier.fillMaxWidth().background(panel).padding(horizontal = 12.dp,vertical = 9.dp))
                    }
                }
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
