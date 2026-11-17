package com.powerfulhimchan.mapexit

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.powerfulhimchan.mapexit.core.*

/** Audio belongs to the resumed game. Every resource is bundled for offline use. */
internal class GameAudio(context:Context) {
    private val app=context.applicationContext
    private val handler=Handler(Looper.getMainLooper())
    private val manager=app.getSystemService(AudioManager::class.java)
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val pool=SoundPool.Builder().setMaxStreams(8).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ids=mutableMapOf<String,Int>()
    private val loaded=mutableSetOf<Int>()
    private val waiting=mutableMapOf<Int,Pair<Long,Float>>()
    private val streams=ArrayDeque<Int>()
    private var player:MediaPlayer?=null
    private var fading:MediaPlayer?=null
    private var generation=0
    private var active=false
    private var focused=false
    private var duck=1f
    private var desired:String?=null
    internal var currentMusic:String?=null; private set
    internal val isPlaying:Boolean get()=player?.isPlaying==true
    private var previous:AudioSnapshot?=null
    private var pendingAction:String?=null
    private var rightFoot=false
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            when(change) {
                AudioManager.AUDIOFOCUS_GAIN -> if(active) {
                    focused=true;duck=1f
                    if(currentMusic!=desired || player==null)switch(desired)
                    else try { player?.setVolume(.32f,.32f);player?.start() }catch(_:RuntimeException){releaseMusic()}
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {duck=.25f;player?.setVolume(.08f,.08f)}
                AudioManager.AUDIOFOCUS_LOSS,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    focused=false;handler.removeCallbacksAndMessages(null);generation++
                    fading?.release();fading=null;stopEffects()
                    try{player?.pause()}catch(_:RuntimeException){releaseMusic()}
                }
            }
        },handler).build()
    init {
        pool.setOnLoadCompleteListener { _,id,status ->
            if(status==0) {
                loaded+=id
                waiting.remove(id)?.let { (at,gain)->if(SystemClock.uptimeMillis()-at<120)playId(id,gain) }
            }
        }
        AudioResources.sounds.forEach { (key,res)->try{ids[key]=pool.load(app,res,1)}catch(_:RuntimeException){} }
    }
    fun resume(s:State) {
        previous=AudioSnapshot.capture(s);active=true;desired=AudioPolicy.music(s)
        focused=try{manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED}catch(_:RuntimeException){false}
        if(focused)switch(desired)
    }
    fun pause() {
        active=false;focused=false;pendingAction=null;handler.removeCallbacksAndMessages(null)
        stopEffects();releaseMusic();manager.abandonAudioFocusRequest(focus)
    }
    fun close(){pause();pool.release()}
    fun action(key:String?){pendingAction=key}
    fun error(){play("ui_error",.5f)}
    fun updateMusic(s:State,busy:Boolean) {
        desired=AudioPolicy.music(s,busy)
        if(active && focused && currentMusic!=desired)switch(desired)
    }
    private fun releaseMusic(){generation++;player?.release();player=null;fading?.release();fading=null;currentMusic=null}
    private fun switch(key:String?) {
        if(!active || !focused)return
        fading?.release();fading=player;player=null;currentMusic=key
        val old=fading
        val p=key?.let{AudioResources.music[it]}?.let{try{MediaPlayer.create(app,it,attributes,0)}catch(_:RuntimeException){null}}
        player=p
        p?.apply {
            isLooping=true;setVolume(0f,0f)
            setOnErrorListener { failed,_,_->if(player===failed){player=null;currentMusic=null};true }
            try{start()}catch(_:RuntimeException){release();player=null}
        }
        val token=++generation;val start=SystemClock.uptimeMillis()
        fun fade() {
            if(token!=generation || !active || !focused)return
            val part=((SystemClock.uptimeMillis()-start)/380f).coerceIn(0f,1f)
            try{player?.setVolume(.32f*part*duck,.32f*part*duck);old?.setVolume(.32f*(1-part)*duck,.32f*(1-part)*duck)}catch(_:RuntimeException){}
            if(part<1f)handler.postDelayed({fade()},24)
            else {old?.release();if(fading===old)fading=null}
        }
        fade()
    }
    private fun stopEffects(){streams.forEach{pool.stop(it)};streams.clear();waiting.clear()}
    private fun playId(id:Int,gain:Float) {
        if(!active || !focused)return
        val volume=(gain*.72f*duck).coerceIn(0f,1f)
        val stream=pool.play(id,volume,volume,1,0,1f)
        if(stream!=0){streams.addLast(stream);if(streams.size>16)pool.stop(streams.removeFirst())}
    }
    private fun play(key:String,gain:Float=1f) {
        if(!active || !focused)return
        val id=ids[key]?:return
        if(id in loaded)playId(id,gain)else waiting[id]=SystemClock.uptimeMillis() to gain
    }
    private fun cue(c:AudioCue){if(c.delay<=0)play(c.key,c.gain)else handler.postDelayed({play(c.key,c.gain)},c.delay)}
    fun observe(s:State) {
        val now=AudioSnapshot.capture(s);val old=previous;previous=now
        val action=pendingAction;pendingAction=null
        if(!active || !focused || old==null)return
        val impact=now.impact!=old.impact && s.lastImpact!=null
        var rewardDelay=0L
        if(impact) {
            val enemy=s.enemy?:s.battleResult?.enemy
            val family=enemy?.let{MonsterCatalog.describe(it,s.floor).family}?:old.enemyFamily?:"golem"
            val kind=s.inventory.firstOrNull{it.id in s.equipment.values && it.weapon}?.kind
            val sounds=AudioPolicy.battle(s.lastImpact!!,kind,family,action=="strong",old.mode==Mode.COMBAT_DEFEND,
                s.mode==Mode.VICTORY,s.mode==Mode.RUN_RESULT && s.runResult?.retreated==false,now.level>old.level)
            sounds.forEach(::cue);rewardDelay=(sounds.maxOfOrNull{it.delay}?:0)+140
        }
        if(now.floor!=old.floor && now.floor>0) {
            play("step_stairs_l",.65f);cue(AudioCue("step_stairs_r",260,.65f))
        } else if(now.x!=null && old.x!=null && (now.x!=old.x || now.y!=old.y) && now.mode!=Mode.RUN_RESULT) {
            val material=if(now.floor in 1..10)"dirt" else "stone"
            play("step_${material}_${if(rightFoot)"r" else "l"}",.72f);rightFoot=!rightFoot
        }
        if(now.battleId!=old.battleId && s.enemy?.boss==true)play("monster_roar",.7f)
        if(now.mode==Mode.ENCOUNTER && (old.mode!=now.mode || old.encounter!=now.encounter) &&
            now.encounter in listOf(Event.NPC,Event.MERCHANT,Event.HEAL))play("npc_encounter",.7f)
        if(now.chests>old.chests)play("chest_open",.8f)
        if(now.gold>old.gold)cue(AudioCue("coin_pickup",rewardDelay,.55f))
        if(now.level>old.level && !impact)play("level_up",.75f)
        if(now.gear!=old.gear)play("equip",.8f)
        if(now.upgrade!=null && now.upgrade!=old.upgrade)play("enhance",.75f)
        if(now.hp>old.hp && action in listOf("potion","strongPotion","useItem","heal","growthHeal","npcHeal"))play("potion_use",.75f)
        if(now.trapPhase==TrapPhase.ACTIVE && old.trapPhase!=TrapPhase.ACTIVE)play(AudioPolicy.trap(now.trapType?:0),.7f)
        if(now.trapType==0 && now.trapPhase==TrapPhase.ACTIVE && now.progress!=old.progress)play("trap_water",.55f)
        if(now.trapPhase==TrapPhase.RESULT && old.trapPhase!=TrapPhase.RESULT)
            play(if(s.trap?.success==true){if(s.trap?.type==1)"landing" else "dodge"}else "player_hit",.75f)
        if(now.mode==Mode.CHALLENGE && s.challenge?.phase==ChallengePhase.HAZARD && now.hp<old.hp)play("trap_blade",.7f)
        if(action=="back")play("ui_back",.45f)
        else if(action!=null && action !in listOf("attack","strong","guard","dodge","move","stop","challengeMove","challengeStop") && now.gear==old.gear && now.chests==old.chests)play("ui_select",.4f)
    }
}
