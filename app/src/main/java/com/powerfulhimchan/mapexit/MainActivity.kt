package com.powerfulhimchan.mapexit

import android.app.Application
import android.os.Bundle
import android.util.AtomicFile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.powerfulhimchan.mapexit.core.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GameModel(app: Application) : AndroidViewModel(app) {
    private val saveFile = AtomicFile(File(app.filesDir, "mapexit-save-v1.json"))
    private val writer = Executors.newSingleThreadExecutor()
    private var saveClock = 0L
    private val mapRandom = java.security.SecureRandom()
    private val downContext = arrayOf("", "")
    private var lastActionAt = 0L
    private var observedImpact = 0L
    internal val dungeonCamera = DungeonCamera()
    internal var battleVisual by mutableStateOf<BattleVisual?>(null)
        private set

    internal fun battleBusy(now: Long = android.os.SystemClock.uptimeMillis()) =
        battleVisual?.active(now) == true

    internal fun presentationState(now: Long): State {
        val motion = battleVisual
        if (motion != null && motion.active(now)) {
            val beforeHit = now < motion.startedAt + BattleAnimation.HIT_MS
            if (engine.state.mode == Mode.RUN_RESULT)
                return motion.scene.copy(hp = if (beforeHit) motion.scene.hp else 0.0)
            if (motion.impact.target == ImpactTarget.PLAYER && beforeHit)
                return engine.state.copy(hp = motion.scene.hp)
        }
        return engine.state
    }

    private fun animateBattle(action: String?, before: State, now: Long) {
        val impact = engine.state.lastImpact ?: return
        if (
            action !in listOf("attack", "strong", "guard", "dodge") ||
                before.enemy == null ||
                impact.serial == before.impactSequence
        )
            return
        battleVisual = BattleVisual(before, action!!, now, impact)
        visualEffect = action
        visualEffectAt = now
    }

    var currentImpact by mutableStateOf<BattleImpact?>(null)
        private set

    var revision by mutableIntStateOf(0)
        private set

    var visualEffect by mutableStateOf("")
        private set

    var visualEffectAt by mutableLongStateOf(0L)
        private set

    var goldNoticeSerial by mutableLongStateOf(0L)
        private set

    var goldNoticeVisible by mutableStateOf(false)
        private set

    fun clearGoldNotice(serial: Long) {
        if (serial == goldNoticeSerial) goldNoticeVisible = false
    }

    fun dismissAchievementNotices(ids: List<String>) {
        engine.state.achievements.notices.removeAll(ids.toSet())
        save()
        refresh()
    }

    fun trainingMenu() {
        engine.trainingMenu()
        battleVisual = null
        currentImpact = null
        save()
        refresh()
    }

    fun exitTraining() {
        engine.exitTraining()
        battleVisual = null
        currentImpact = null
        save()
        refresh()
    }

    var active = false
    var loadError: String? = null
        private set

    var saveError by mutableStateOf<String?>(null)
        private set

    var engine: GameEngine

    init {
        val saved =
            if (saveFile.baseFile.exists())
                try {
                    SaveCodec.decode(saveFile.openRead().bufferedReader().use { it.readText() })
                } catch (e: Exception) {
                    loadError = "저장 파일을 읽지 못했습니다. 새로 시작하면 기존 파일을 따로 보존합니다."
                    null
                }
            else null
        engine =
            GameEngine(
                saved ?: State(rng = mapRandom.nextLong()),
                BuildConfig.DEBUG,
                mapSeed = { mapRandom.nextLong() },
            )
        dungeonCamera.observe(engine.state, android.os.SystemClock.uptimeMillis(), animate = false)
        observedImpact = engine.state.impactSequence
    }

    fun down(button: Int) {
        if (loadError != null) return
        downContext[button] = if (battleBusy()) "BUSY" else engine.controls.inputToken()
        refresh()
    }

    fun up(button: Int, released: Boolean = true) {
        if (loadError != null) {
            if (button == 1) {
                val f = saveFile.baseFile
                if (f.exists())
                    f.copyTo(
                        File(f.parentFile, "mapexit-unreadable-${System.currentTimeMillis()}.json"),
                        overwrite = false,
                    )
                loadError = null
                engine =
                    GameEngine(
                        State(rng = mapRandom.nextLong()),
                        BuildConfig.DEBUG,
                        mapSeed = { mapRandom.nextLong() },
                    )
                save()
            }
            refresh()
            return
        }
        if (!released) {
            engine.pause()
            refresh()
            return
        }
        if (downContext[button] == engine.controls.inputToken()) {
            val now = android.os.SystemClock.uptimeMillis()
            if ((engine.state.mode == Mode.TRAP || now - lastActionAt >= 180) && !battleBusy(now)) {
                val action = engine.controls.buttons().getOrNull(button)?.choice?.action
                val before = engine.state.battleSnapshot()
                engine.press(button)
                animateBattle(action, before, now)
                lastActionAt = now
            }
        }
        if (engine.state.mode != Mode.TRAP) save()
        refresh()
    }

    fun choose(choice: Choice) {
        if (loadError != null) return
        if (
            battleBusy() &&
                choice.action in
                    listOf(
                        "attack",
                        "strong",
                        "guard",
                        "dodge",
                        "potion",
                        "strongPotion",
                        "flee",
                        "battleContinue",
                        "runContinue",
                    )
        )
            return
        val valid =
            (engine.choices()+engine.lootChoices()).any { it.action == choice.action && it.argument == choice.argument }
        if (valid && choice.action == "purchase") {
            val item =
                engine.state.shop.find {
                    it.id ==
                        choice.argument
                }
            val count = if (choice.action == "buyQuantity") choice.argument.toInt() else 1
            if (
                item != null &&
                    count > 0 &&
                    engine.state.gold < Equipment.buyPrice(engine.state, item) * count
            ) {
                goldNoticeSerial++
                goldNoticeVisible = true
                return
            }
        }
        val before = engine.state.battleSnapshot()
        engine.choose(choice)
        animateBattle(choice.action, before, android.os.SystemClock.uptimeMillis())
        save()
        refresh()
    }

    fun purchase(itemId: Long, count: Int) {
        if (loadError != null || battleBusy()) return
        val item=engine.state.shop.find { it.id==itemId } ?: return
        if(engine.state.gold < Equipment.buyPrice(engine.state,item)*count) {
            goldNoticeSerial++; goldNoticeVisible=true
            return
        }
        engine.purchase(itemId,count)
        save(); refresh()
    }

    fun openLootInventory() {
        if (loadError != null || battleBusy()) return
        if (engine.openLootInventory()) { save(); refresh() }
    }

    fun openLevelUpGrowth() {
        if (loadError != null || battleBusy()) return
        if(engine.openLevelUpGrowth()) { save(); refresh() }
    }

    fun openDungeonPanel(type: String) {
        if (loadError != null || battleBusy()) return
        if (engine.openDungeonPanel(type)) {
            save()
            refresh()
        }
    }

    fun dismissLevelUp() {
        engine.state.levelUpNotice = null
        save()
        refresh()
    }

    fun back() {
        engine.navigateBack()
        save()
        refresh()
    }

    fun refresh() {
        dungeonCamera.observe(engine.state, android.os.SystemClock.uptimeMillis(), animate = active)
        if (observedImpact != engine.state.impactSequence) {
            observedImpact = engine.state.impactSequence
            currentImpact = engine.state.lastImpact
        } else if (engine.state.lastImpact == null) currentImpact = null
        revision++
    }

    fun clearImpact(serial: Long) {
        if (currentImpact?.serial == serial) currentImpact = null
    }

    fun tick(delta: Long) {
        if (!active || loadError != null) return
        val animate =
            engine.state.mode == Mode.MOVING ||
                engine.state.mode in listOf(Mode.TRAP, Mode.TRAP_GUIDE, Mode.CHALLENGE)
        engine.tick(delta)
        saveClock += delta
        if (saveClock >= 1500) {
            saveClock = 0
            save()
        }
        if (animate) refresh()
    }

    fun pause() {
        active = false
        dungeonCamera.observe(engine.state, android.os.SystemClock.uptimeMillis(), animate = false)
        engine.pause()
        save(blocking = true)
        refresh()
    }

    fun save(blocking: Boolean = false) {
        if (loadError != null) return
        val json = SaveCodec.encode(engine.state)
        val target =
            if (engine.state.debug)
                AtomicFile(File(getApplication<Application>().filesDir, "mapexit-test-save.json"))
            else saveFile
        val job =
            writer.submit {
                var stream: java.io.FileOutputStream? = null
                try {
                    stream = target.startWrite()
                    stream.write(json.toByteArray(Charsets.UTF_8))
                    target.finishWrite(stream)
                    saveError = null
                } catch (e: Exception) {
                    if (stream != null) target.failWrite(stream)
                    saveError = "저장 실패: 기기 저장 공간을 확인하세요"
                }
            }
        if (blocking)
            try {
                job.get(3, TimeUnit.SECONDS)
            } catch (_: Exception) {}
    }

    override fun onCleared() {
        writer.shutdown()
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    lateinit var game: GameModel
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = ViewModelProvider(this)[GameModel::class.java]
        setContent { MapExitScreen(game) }
    }

    override fun onResume() {
        super.onResume()
        if (::game.isInitialized) game.active = true
    }

    override fun onPause() {
        if (::game.isInitialized) game.pause()
        super.onPause()
    }
}
