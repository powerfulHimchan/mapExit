package com.powerfulhimchan.mapexit

import android.app.Application
import android.os.Bundle
import android.util.AtomicFile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    private val downInTrap = BooleanArray(2)
    var revision by mutableIntStateOf(0)
        private set

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
        engine = GameEngine(saved ?: State(), BuildConfig.DEBUG)
    }

    fun down(button: Int) {
        if (loadError != null) return
        downInTrap[button] = engine.state.mode == Mode.TRAP
        if (downInTrap[button]) engine.rawDown(button)
        revision++
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
                engine = GameEngine(State(), BuildConfig.DEBUG)
                save()
            }
            revision++
            return
        }
        if (!released) {
            engine.pause()
            revision++
            return
        }
        if (downInTrap[button]) engine.rawUp(button)
        else if (engine.state.mode != Mode.TRAP) engine.press(button)
        if (engine.state.mode != Mode.TRAP) save()
        revision++
    }

    fun tick(delta: Long) {
        if (!active || loadError != null) return
        val animate =
            engine.state.mode == Mode.MOVING ||
                (engine.state.mode == Mode.TRAP && engine.state.trap?.phase != TrapPhase.RESULT)
        engine.tick(delta)
        saveClock += delta
        if (saveClock >= 1500) {
            saveClock = 0
            save()
        }
        if (animate) revision++
    }

    fun pause() {
        active = false
        engine.pause()
        save(blocking = true)
        revision++
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
