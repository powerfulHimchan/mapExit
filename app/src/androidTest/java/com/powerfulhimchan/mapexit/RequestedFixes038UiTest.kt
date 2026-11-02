package com.powerfulhimchan.mapexit

import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import org.junit.Assert.*
import org.junit.Test

class RequestedFixes038UiTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo?,label:String): Rect? {
        if(node==null)return null
        if(node.contentDescription?.toString()==label || node.text?.toString()==label)
            return Rect().also {node.getBoundsInScreen(it)}
        for(i in 0 until node.childCount)find(node.getChild(i),label)?.let {return it}
        return null
    }
    private fun bounds(label:String):Rect {
        val end=SystemClock.uptimeMillis()+8000
        while(SystemClock.uptimeMillis()<end){
            find(instrumentation.uiAutomation.rootInActiveWindow,label)?.let{return it}
            SystemClock.sleep(50)
        }
        error("Missing UI: $label")
    }
    private fun tap(label:String){
        val r=bounds(label);val start=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
            MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,r.exactCenterX(),r.exactCenterY(),0).also{
                instrumentation.sendPointerSync(it);it.recycle()
            };SystemClock.sleep(60)
        }
        SystemClock.sleep(150)
    }
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use{it.readBytes()}
    private fun capture(name:String){SystemClock.sleep(150);shell("screencap -p /sdcard/Download/mapexit-fix038-$name.png")}
    private fun seed(a:MainActivity,e:GameEngine){
        a.game.storyPlaybackEnabled=false;a.game.engine=e;a.startGame();a.game.active=false;a.game.refresh()
    }
    private fun battle():GameEngine=GameEngine().also{e->
        e.enterFloor(1);e.state.base=e.state.base.copy(hp=10000.0);e.state.hp=10000.0
        e.startBattle(false);e.state.enemy=e.state.enemy!!.copy(maxHp=100000.0,hp=100000.0)
        e.state.level=3;e.state.points=3;e.state.levelUpNotice=LevelUpNotice(2,3,3)
        e.state.message="전투 준비"
    }

    @Test fun bannerAndTwoLineMessageKeepTheirBoundsDuringAttackAndGuardAndGrowthIsHiddenInCombat(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{seed(it,battle())}
            bounds("레벨 업 LV.2에서 LV.3");SystemClock.sleep(500)
            val canvas=bounds("메인 장면");val message=bounds("현재 상황")
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow,"성장 배분 열기"))
            for(action in listOf("attack","guard")){
                scenario.onActivity{it.game.choose(Choice("실행",action))}
                SystemClock.sleep(100)
                bounds("레벨 업 LV.2에서 LV.3")
                assertEquals("Canvas moved during $action",canvas,bounds("메인 장면"))
                assertEquals(message,bounds("현재 상황"))
                assertNull(find(instrumentation.uiAutomation.rootInActiveWindow,"성장 배분 열기"))
                capture("banner-$action")
                SystemClock.sleep(BattleAnimation.DURATION_MS+100)
            }
            scenario.onActivity{it.game.engine.state.message="행운 공격 강화 · 행운 치명 확률 강화 · 빈틈 공격 · 치명타! 12345 피해 · 다음 공격 준비";it.game.refresh()}
            assertEquals(message,bounds("현재 상황"));assertEquals(canvas,bounds("메인 장면"))
            capture("two-line-message")
            scenario.onActivity{it.game.engine.state.enemy=null;it.game.engine.state.mode=Mode.EXPLORE;it.game.refresh()}
            tap("성장 배분 열기");bounds("능력치 성장")
            scenario.onActivity{assertEquals("growth",it.game.engine.state.menu?.type);assertNull(it.game.engine.state.levelUpNotice)}
        }
    }

    @Test fun expandedMapReturnsToTheExactMovementCombatAndTrapStateAndBlocksInputs(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            for(mode in listOf(Mode.MOVING,Mode.COMBAT_DEFEND,Mode.TRAP)){
                lateinit var saved:String
                scenario.onActivity{a->
                    val e=battle();e.state.mode=mode
                    if(mode!=Mode.COMBAT_DEFEND)e.state.enemy=null
                    if(mode==Mode.TRAP)e.state.trap=Traps.create(e.state,4).also{it.phase=TrapPhase.ACTIVE;it.elapsedMs=500}
                    e.state.movementMs=123
                    seed(a,e)
                    saved=SaveCodec.encode(e.state)
                }
                tap("주변 미니맵");bounds("확대 지도")
                assertNull(find(instrumentation.uiAutomation.rootInActiveWindow,"현재 상황"))
                scenario.onActivity{a->
                    a.game.active=true;a.game.tick(250);a.game.down(0);a.game.up(0)
                    a.game.choose(Choice("방어","guard"));a.game.openDungeonPanel("inventory")
                    a.game.active=false
                    assertEquals(saved,SaveCodec.encode(a.game.engine.state))
                }
                capture("expanded-map-${mode.name.lowercase()}")
                scenario.recreate();bounds("확대 지도");scenario.onActivity{it.game.active=false}
                tap("돌아가기");bounds("메인 장면")
                scenario.onActivity{a->a.game.active=false;assertFalse(a.game.mapExpanded);assertEquals(saved,SaveCodec.encode(a.game.engine.state))}
            }
        }
    }

    @Test fun counterAnimationFollowsTheGuardAndPreservesHpUntilEachImpact(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{a->
                val e=battle();e.state.mode=Mode.COMBAT_DEFEND
                e.state.base=e.state.base.copy(agility=120.0,luck=100.0)
                val p=Equipment.stats(e.state)
                val rollSeed=(1L..100000L).first{n->val s=State(rng=n);s.random();s.random();s.random()<Luck.counter(p)}
                e.state.rng=rollSeed;seed(a,e)
                val hp=e.state.hp;val enemyHp=e.state.enemy!!.hp
                a.game.choose(Choice("방어","guard"))
                val motion=a.game.battleVisual!!;assertNotNull(motion.impact.counterHit)
                assertEquals(ImpactTarget.PLAYER,motion.frame(motion.startedAt+10).impact.target)
                val second=motion.startedAt+BattleAnimation.DURATION_MS
                assertEquals("attack",motion.frame(second+10).action)
                assertEquals(ImpactTarget.ENEMY,motion.frame(second+10).impact.target)
                assertEquals(hp,a.game.presentationState(motion.startedAt+10).hp,.00001)
                assertEquals(e.state.hp,a.game.presentationState(second+10).hp,.00001)
                assertEquals(enemyHp,a.game.presentationState(second+10).enemy!!.hp,.00001)
                assertEquals(e.state.enemy!!.hp,a.game.presentationState(second+BattleAnimation.HIT_MS+10).enemy!!.hp,.00001)
                assertEquals(Mode.COMBAT_ATTACK,e.state.mode)
            }
            bounds("메인 장면");capture("guard-counter")
            SystemClock.sleep(BattleAnimation.DURATION_MS*2+100)
        }
    }

    @Test fun beamBarShowsTheSafeWindowBeforeAndDuringTheRealCrouchIntervalWithoutReadyText(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            for(ready in listOf(false,true)){
                scenario.onActivity{a->val e=battle();e.state.enemy=null;e.state.mode=Mode.TRAP
                    e.state.trap=Traps.create(e.state,4).also{it.phase=TrapPhase.ACTIVE;it.elapsedMs=if(ready)it.beamCrouchStartMs+50 else 100}
                    seed(a,e)
                }
                bounds("메인 장면");capture(if(ready)"beam-bar-ready" else "beam-bar-wait")
                assertNull(find(instrumentation.uiAutomation.rootInActiveWindow,"지금 숙이기!"))
            }
        }
    }
}
