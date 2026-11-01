package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.Serializable

@Serializable
data class StoryProgress(
    var version: Int = 0,
    val unlocked: MutableList<String> = mutableListOf(),
    val queue: MutableList<String> = mutableListOf(),
    var page: Int = 0,
    var recap: String? = null,
)

data class StoryLine(val speaker: String, val text: String)
data class StoryScene(val id: String, val title: String, val art: String, val lines: List<StoryLine>)

/** Narrative metadata never changes a mode, random roll, reward or dungeon layout. */
object Story {
    val cadence = listOf(2,5,7,10,12,15,17,20,22,25,27,30,32,35,37,40,42,45,47,50)
    private fun scene(id: String, title: String, art: String, vararg lines: Pair<String,String>) =
        StoryScene(id,title,art,lines.map { StoryLine(it.first,it.second) })
    private val firstNotes = mapOf(
        2 to ("식지 않는 불씨" to "노인이 건넨 등불은 기름도 없이 타오른다. 가슴 안에서도 같은 온기가 느껴진다."),
        5 to ("돌아가는 두 가지 길" to "계단을 따라 살아 돌아가면 짐도 가져갈 수 있다. 하지만 불씨에 몸을 맡기는 탐험 포기는, 몸만 지상으로 돌려보낸다."),
        7 to ("발자국" to "먼지 위에 내려가는 발자국만 남아 있다. 노인의 아들도 이 길을 걸었을까. 10층까지만 가면 된다."),
        12 to ("낯선 이름들" to "벽에 수많은 이름이 긁혀 있다. 계약서에서 본 것과 같은 글씨다. 가장 새로 새겨진 이름은 내 이름이다."),
        15 to ("돌아오는 몸" to "불씨는 상처를 지워도 익힌 감각까지 지우지는 않는다. 빈손으로 깨어나도, 이전의 나와 똑같지는 않다."),
        17 to ("빈 무덤" to "부서진 묘비마다 같은 문장이 남아 있다. ‘이곳에 잠들지 못한 사람을 위하여.’"),
        22 to ("물 아래의 목소리" to "고인 물에 등불을 비추자 누군가 속삭인다. ‘그 노인은 아직도 입구에 있나?’ 돌아보니 아무도 없다."),
        25 to ("거래의 흔적" to "낡은 장부에는 사람의 이름과 날짜가 적혀 있다. 물건을 사고판 기록은 아니다. 마지막 칸에는 ‘다음 계약자’라고 적혀 있다."),
        27 to ("반복되는 복도" to "처음 걷는 길인데 익숙하다. 바닥의 금과 벽의 흠집이 꿈속에서 본 것 같다. 얼마나 많은 사람이 같은 길을 반복했을까."),
        32 to ("누군가의 등불" to "꺼진 등불 옆에 작은 목각 인형이 놓여 있다. 아이에게 돌려주려던 선물이었을까. 주인을 잃은 물건은 이곳을 벗어나지 못한다."),
        35 to ("더 아래에서" to "아래로 내려갈수록 가슴의 불씨가 거세진다. 나를 지켜 주려는 것인지, 어딘가로 재촉하는 것인지 알 수 없다."),
        37 to ("돌아오지 않은 아들" to "편지 한 장을 주웠다. ‘아버지, 나를 기다리지 마세요. 다른 사람을 보내서도 안 됩니다.’ 받는 이의 이름은 지워져 있다."),
        42 to ("문을 향한 기도" to "석상들은 모두 지상이 아닌 아래를 향해 무릎 꿇고 있다. 손에 쥔 것은 무기가 아니라, 문을 묶는 사슬이다."),
        45 to ("아직 선택하지 않은 길" to "문지기를 쓰러뜨리지 말라는 경고가 머릿속을 맴돈다. 하지만 계약을 끊을 다른 길은 찾지 못했다. 직접 만나야 한다."),
        47 to ("문 너머의 숨" to "거대한 무언가가 문 너머에서 숨을 쉰다. 가슴의 불씨가 그 호흡에 맞춰 흔들린다. 나를 부른 것은 누구인가."),
    )
    private val echoNotes = mapOf(
        2 to "이번에는 부탁을 들어주러 내려가는 것이 아니다. 내가 열어 버린 문을 닫으러 간다.",
        5 to "길의 모양은 바뀌었지만 벽에 새겨진 이름은 그대로다. 던전은 지형보다 오래된 것을 기억한다.",
        7 to "이제는 안다. ‘10층까지만’이라는 말은 나에게만 했던 약속이 아니었다.",
        12 to "계약서의 빈칸은 사라지지 않았다. 내가 실패하면 누군가의 이름이 또 새겨질 것이다.",
        15 to "봉인의 파편은 새로운 회차를 여는 순간, 새겨 둔 장비를 복원한다. 평소의 불씨가 짐까지 데려오지는 못한다.",
        17 to "빈 무덤 앞에 등불을 잠시 내려놓았다. 이번에는 이 이름들에게 끝을 돌려주고 싶다.",
        22 to "물속 목소리가 다시 묻는다. ‘진실을 알고도 돌아왔나?’ 나는 고개를 끄덕였다.",
        25 to "장부의 이름 하나가 희미해져 있다. 문지기가 마지막으로 건넨 파편이 가슴에서 빛난다.",
        27 to "회수자는 같은 길을 걷게 하면 같은 선택을 할 거라 믿는 것 같다. 이번에는 다르다.",
        32 to "주인 없는 등불들이 조금씩 밝아진다. 저 아래의 문이 열리면, 이 불빛들도 함께 삼켜질 것이다.",
        35 to "불씨가 아래를 재촉한다. 나는 그 힘을 거슬러, 나의 뜻으로 한 걸음씩 내려간다.",
        37 to "노인의 아들이 남긴 편지를 다시 읽는다. 그는 끝내 아버지를 원망하는 말을 쓰지 않았다.",
        42 to "사슬 하나가 끊어져 있다. 회수자는 이미 문지기의 몸을 빌려 봉인을 부수기 시작했다.",
        45 to "문지기의 몸을 다시 쓰러뜨리는 것만으로는 부족하다. 그 몸과 회수자를 잇는 사슬까지 끊어야 한다.",
        47 to "문 너머의 숨이 거칠어진다. 이제 내가 무엇을 하러 돌아왔는지, 그도 알고 있다.",
    )
    private val major = listOf(
        scene("prologue","10층까지만","lantern",
            "등불지기" to "지하 10층 제단에 내 아들의 유품이 남아 있네. 그것만 찾아다오. 10층까지만 가면 되네.",
            "등불지기" to "이 등불을 가져가게. 꺼지지 않는 불씨가 길을 지켜 줄 걸세.",
            "나" to "등불을 받아 들자 가슴에 작은 온기가 스몄다. 나는 유품을 찾아 돌아오겠다고 약속했다."),
        scene("resurrection","돌아온 것은 나뿐","flame",
            "나" to "눈을 뜨니 다시 던전 입구였다. 상처는 사라졌지만 장비와 소지품은 돌아오지 않았다.",
            "등불지기" to "불씨는 몸과 영혼을 다시 엮을 뿐이네. 익힌 힘은 남아도, 물건까지 건너오지는 못하지.",
            "나" to "죽음도, 불씨에 몸을 맡기는 탐험 포기도 같은 값을 치른다. 그래도 다시 내려갈 수 있다."),
        scene("contract","이름이 새겨진 계약","contract",
            "나" to "문지기 뒤 제단에는 유품 대신 낡은 계약서가 놓여 있었다. 손을 대자 빈칸에 내 이름이 새겨졌다.",
            "문 너머의 목소리" to "계약을 확인했다. 가장 아래에서 너를 기다리겠다.",
            "나" to "가슴의 불씨가 타올랐다. 지상으로는 돌아갈 수 있어도, 이곳을 떠나려 하면 몸이 무너지리라는 것을 알았다.",
            "나" to "10층까지만이라던 약속은 끝났다. 계약을 끊으려면 50층으로 가야 한다."),
        scene("surface_contract","입구의 노인","lantern",
            "나" to "제단에 유품은 없었어요. 계약서에 내 이름이 새겨졌어요.",
            "등불지기" to "…이미 응답했군. 불씨는 입구까지는 데려오지만, 이 땅 밖으로는 보내 주지 않아.",
            "등불지기" to "계약의 주인을 만나야 하네. 가장 아래로 가게. 그곳에서 끝낼 수 있을 걸세."),
        scene("warning20","이전 계약자의 기록","record",
            "낡은 기록" to "나도 10층까지만 다녀오면 된다는 말을 믿었다. 내 이름이 적힌 뒤로, 던전 밖의 길은 사라졌다."),
        scene("warning30","축복의 다른 이름","record",
            "찢어진 기록" to "불씨는 우리를 살려 주는 것이 아니다. 다시 내려보내고 있다. 죽을 때마다 그 목소리가 더 가까워진다."),
        scene("warning40","마지막 경고","chains",
            "돌에 새긴 글" to "마지막 문지기를 쓰러뜨리지 마라. 그는 우리를 가둔 자가 아니다.",
            "나" to "그렇다면 누가 계약의 주인인가. 다른 탈출구는 보이지 않는다. 직접 만나 진실을 물어야 한다."),
        scene("guardian","마지막 문지기","guardian",
            "마지막 문지기" to "또 한 명이 여기까지 왔군. 이번에도 돌아가라는 말은 듣지 않겠지.",
            "나" to "이 계약을 끊으러 왔다. 당신이 길을 막는다면, 비키게 하겠어.",
            "마지막 문지기" to "네 가슴의 불씨가 누구의 것인지조차 모르는가… 그렇다면 이 문 앞에서 멈춰라."),
        scene("revelation","열어 버린 문","door",
            "마지막 문지기" to "나는 계약의 주인이 아니다. 그가 밖으로 나오지 못하도록 이 문을 지켰을 뿐….",
            "문 너머의 목소리" to "잘했다, 계약자여. 몇 번을 죽어도 다시 일어나는 자가 필요했다. 내 이름은 회수자다.",
            "나" to "문이 벌어지며 어둠이 문지기의 몸으로 스며들었다. 나를 살리던 힘이, 나를 이용해 스스로의 문을 연 것이다.",
            "마지막 문지기" to "이 파편을 가져가라. 다시 돌아와… 네가 연 문을 닫아다오."),
        scene("inheritance","영혼에 새기는 장비","fragment",
            "나" to "봉인의 파편에는 장비 하나를 새길 수 있다. 새로운 회차를 여는 순간, 새긴 장비가 내 곁에서 복원된다.",
            "나" to "이전에 새긴 장비도 함께 이어진다. 다만 평소의 죽음과 탐험 포기는 여전히 짐을 돌려보내 주지 않는다."),
        scene("confession","돌아온 이유","lantern",
            "등불지기" to "유품 이야기는 거짓이었네. 내 아들도 계약자였어. 다음 사람을 보내면 아들을 돌려주겠다는 말을 믿었지.",
            "등불지기" to "아들은 돌아오지 않았고… 이제 입구의 등불마저 꺼져 가는군. 내가 자네를 그곳으로 보냈어.",
            "나" to "회수자는 문지기의 몸으로 남은 봉인을 부수고 있어요. 문이 완전히 열리기 전에 다시 내려가야 해요.",
            "나" to "이번에는 탈출하기 위해서만이 아니다. 내가 연 문을 닫고, 다음 계약자가 생기지 않게 하러 간다."),
        scene("possessed","빌려 입은 문지기","possessed",
            "회수자" to "돌아왔구나. 한 번 더 문을 열어 주러 왔나? 이 몸도, 네 불씨도 나와 이어져 있다.",
            "나" to "이번에는 네 뜻대로 움직이지 않아. 이 몸과 너를 잇는 사슬을 끊겠다.",
            "회수자" to "파편 하나로 모든 이름을 돌려받을 수 있을 것 같으냐. 그렇다면 끝까지 내려와 증명해라."),
        scene("second_victory","끊어진 첫 사슬","fragment",
            "나" to "문지기의 몸에서 검은 사슬 하나가 끊어졌다. 잠시, 그의 눈이 원래의 빛을 되찾았다.",
            "문지기의 잔향" to "아직 문 너머에 묶인 이름들이 남아 있다. 파편을 이어라. 이제 그도 너를 두려워한다.",
            "나" to "모든 봉인이 풀리기 전에 연결을 하나씩 끊어야 한다. 이번 파편에도 장비를 새기고, 다시 길을 연다."),
        scene("recap_start","지금까지의 이야기","lantern",
            "회상" to "등불지기는 아들의 유품을 찾아 달라며, 10층까지만 내려가면 된다고 했다. 그가 건넨 불씨는 몸과 기억을 되살리지만 잃은 짐까지 옮기지는 못한다."),
        scene("recap_contract","지금까지의 이야기","contract",
            "회상" to "10층의 제단에는 유품 대신 계약서가 있었다. 이름이 새겨진 뒤, 이곳을 떠날 수 없게 됐다. 계약의 주인은 50층에서 기다린다."),
        scene("recap_revealed","지금까지의 이야기","door",
            "회상" to "50층 문지기는 회수자를 가두고 있었다. 그를 쓰러뜨려 문이 열렸고, 회수자는 문지기의 몸을 차지했다. 봉인의 파편을 이어 문을 다시 닫아야 한다."),
    )
    val scenes: List<StoryScene> = major + listOf(
        scene("return_10","지하 10층 · 다시 찾은 제단","contract","나" to "계약서에는 아직 내 이름이 남아 있다. 이번에는 그 이름을 지우기 위해 제단을 지나간다."),
        scene("return_20","지하 20층 · 약속의 대가","record","나" to "노인도 같은 거짓 약속에 붙잡혀 있었다. 그를 용서할 수 있을지는 모르지만, 다음 사람을 보내게 둘 수는 없다."),
        scene("return_30","지하 30층 · 내 뜻으로","flame","나" to "나를 되살리는 힘은 여전히 회수자의 것이다. 하지만 그 힘으로 무엇을 할지는 내가 정한다."),
        scene("return_40","지하 40층 · 경고에 대한 대답","chains","나" to "이제 경고의 뜻을 안다. 문지기의 몸을 해방하고, 그 안으로 뻗은 회수자의 사슬을 끊겠다."),
    ) + firstNotes.map { (floor,note) ->
        scene("floor_$floor","지하 ${floor}층 · ${note.first}","record","나" to note.second)
    } + echoNotes.map { (floor,note) ->
        scene("echo_$floor","지하 ${floor}층 · 돌아온 길","chains","나" to note)
    }
    fun find(id: String) = scenes.firstOrNull { it.id == id }
    fun enabled(s: State) = !s.training && !s.debug
    private fun unlock(s: State, id: String, enqueue: Boolean = true) {
        if (!enabled(s) || find(id) == null || id in s.story.unlocked) return
        s.story.unlocked.add(id)
        if (enqueue) s.story.queue.add(id)
    }

    fun initialize(s: State, fresh: Boolean) {
        val p = s.story
        if (p.version > 0) {
            p.unlocked.removeAll { find(it) == null }
            val head = p.queue.firstOrNull()
            p.queue.removeAll { it !in p.unlocked || find(it) == null }
            p.page = if (head != p.queue.firstOrNull()) 0 else p.page.coerceIn(0,(p.queue.firstOrNull()?.let(::find)?.lines?.lastIndex ?: 0))
            return
        }
        p.version = 1
        if (fresh) { unlock(s,"prologue"); return }
        // Missing story metadata identifies an old save. Do not replay the plot in a live fight.
        unlock(s,"prologue",false)
        val completed = s.clears > 0 || s.cycle > 1
        fun passed(floor: Int) = completed || s.highest > floor || floor in s.devices
        if (passed(10)) { unlock(s,"contract",false); unlock(s,"surface_contract",false) }
        for ((floor,id) in listOf(20 to "warning20",30 to "warning30",40 to "warning40"))
            if (passed(floor)) unlock(s,id,false)
        val firstVictory = completed || s.floor == 50 && (s.dungeon?.bossCleared == true || s.mode == Mode.INHERIT)
        if (firstVictory) for (id in listOf("guardian","revelation")) unlock(s,id,false)
        if (completed || s.mode == Mode.INHERIT) unlock(s,"inheritance",false)
        if (completed) unlock(s,"confession",false)
        for (floor in firstNotes.keys.filter { it <= s.highest }) unlock(s,"floor_$floor",false)
        p.recap = when { firstVictory -> "recap_revealed"; passed(10) -> "recap_contract"; else -> "recap_start" }
        if (s.mode == Mode.SURFACE) onSurface(s)
    }

    fun onFloor(s: State) {
        val prefix = if (s.cycle > 1) "echo" else "floor"
        if (s.floor in firstNotes) unlock(s,"${prefix}_${s.floor}")
    }
    fun beforeBoss(s: State) { if (s.floor == 50) unlock(s,if(s.cycle > 1) "possessed" else "guardian") }
    fun afterBoss(s: State) {
        val id = if (s.cycle > 1) when(s.floor) { 50 -> "second_victory"; else -> null }
        else when(s.floor) { 10 -> "contract"; 20 -> "warning20"; 30 -> "warning30"; 40 -> "warning40"; 50 -> "revelation"; else -> null }
        id?.let { unlock(s,it) }
        // Revisited milestone floors also offer a short, distinct second-journey reflection.
        if (s.cycle > 1 && s.floor in listOf(10,20,30,40)) unlock(s,"return_${s.floor}")
    }
    fun onDeath(s: State) { unlock(s,"resurrection") }
    fun onSurface(s: State) {
        s.story.recap?.let { unlock(s,it); s.story.recap = null }
        if (s.cycle == 1 && "contract" in s.story.unlocked) unlock(s,"surface_contract")
    }
    fun onInheritance(s: State) { unlock(s,"inheritance") }
    fun onNewCycle(s: State) {
        onSurface(s)
        if(s.cycle == 2) unlock(s,"confession")
    }
    fun current(s: State): StoryScene? = if(enabled(s) && s.mode != Mode.RUN_RESULT) s.story.queue.firstOrNull()?.let(::find) else null
    fun advance(s: State) {
        val scene = current(s) ?: return
        if (s.story.page < scene.lines.lastIndex) s.story.page++ else skip(s)
    }
    fun skip(s: State) {
        if (current(s) == null) return
        s.story.queue.removeAt(0)
        s.story.page = 0
    }
    fun history(s: State): List<StoryScene> = s.story.unlocked.mapNotNull(::find)
    fun objective(s: State): String = when {
        s.cycle > 1 -> "회수자의 사슬을 끊고 마지막 문을 닫기"
        "revelation" in s.story.unlocked -> "봉인의 파편에 장비를 새기고 다시 돌아가기"
        "contract" in s.story.unlocked -> "50층에서 계약의 주인 찾기"
        else -> "10층 제단에서 등불지기의 아들이 남긴 유품 찾기"
    }
}
