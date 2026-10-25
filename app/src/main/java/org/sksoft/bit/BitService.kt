package org.sksoft.bit

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.opengl.GLSurfaceView
import android.os.*
import android.provider.Settings
import android.view.*
import kotlin.math.*

class BitService : Service(), android.content.SharedPreferences.OnSharedPreferenceChangeListener, DisplayManager.DisplayListener {
    companion object {
        const val STOP="org.sksoft.bit.STOP"
        const val RESET="org.sksoft.bit.RESET"
        @Volatile var running=false
    }
    private val handler=Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("bit",MODE_PRIVATE) }
    private val wm by lazy { getSystemService(WindowManager::class.java) }
    private val displays by lazy { getSystemService(DisplayManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private lateinit var audio: Audio
    private var view: GLSurfaceView?=null
    private lateinit var renderer: BitRenderer
    private lateinit var params: WindowManager.LayoutParams
    private var safe=Rect()
    private var active=false
    private val random=OriginalRandom((System.currentTimeMillis()/1000).toInt())
    private var tick=0L
    private var lastTime=0L
    private var remainder=0L
    private var lastPermission=0L
    private var receiverRegistered=false
    private var ops: AppOpsManager?=null
    private val permissionListener=AppOpsManager.OnOpChangedListener { op, pkg ->
        if(op==AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW && pkg==packageName) handler.post { if(!Settings.canDrawOverlays(this)) stopSelf() }
    }
    private val screen=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){
        if(i.action==Intent.ACTION_SCREEN_OFF) { pause(); audio.stop() } else updateScreen()
    }}
    private val frame=object:Runnable { override fun run(){
        if(!active)return
        val now=SystemClock.uptimeMillis()
        if(now-lastPermission>=1000){lastPermission=now;if(!Settings.canDrawOverlays(this@BitService)){stopSelf();return}}
        remainder+=now-lastTime;lastTime=now
        tick+=remainder/Motion.TICK_MS;remainder%=Motion.TICK_MS
        renderer.ticks=tick
        if(renderer.response!=0 && tick-renderer.responseStart>=Motion.RESPONSE_TICKS) renderer.response=0
        view?.requestRender()
        handler.postDelayed(this,50) // 20fps, original logical clock remains 14ms
    }}
    override fun onBind(i:Intent?) : IBinder?=null
    override fun onCreate(){
        super.onCreate();audio=Audio(this);audio.volume=prefs.getInt("volume",70)
        prefs.registerOnSharedPreferenceChangeListener(this)
        displays.registerDisplayListener(this,handler)
        val filter=IntentFilter().apply{addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_USER_PRESENT)}
        if(Build.VERSION.SDK_INT>=33) registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(screen,filter)
        receiverRegistered=true
        ops=getSystemService(AppOpsManager::class.java)
        ops?.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,packageName,permissionListener)
    }
    override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int {
        if(i?.action==STOP){stopSelf();return START_NOT_STICKY}
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY}
        if(view==null){
            try {
                notification()
                renderer=BitRenderer(this)
                val v=GLSurfaceView(this)
                v.setZOrderOnTop(true);v.setEGLConfigChooser(8,8,8,8,16,0);v.holder.setFormat(PixelFormat.TRANSLUCENT)
                v.setRenderer(renderer);v.renderMode=GLSurfaceView.RENDERMODE_WHEN_DIRTY
                view=v
                params=WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT)
                params.gravity=Gravity.TOP or Gravity.LEFT
                params.setTitle("Floating Bit")
                updateEdgeFlag()
                if(Build.VERSION.SDK_INT>=30) params.setFitInsetsTypes(0)
                bounds();val size=desiredSize();params.width=size;params.height=size
                restorePosition();touch(v)
                wm.addView(v,params);running=true;save();prefs.edit().putBoolean("enabled",true).apply()
                updateScreen()
            } catch(e:Exception){prefs.edit().putString("error","Could not show Bit: ${e.javaClass.simpleName}").apply();stopSelf()}
        }
        if(i?.action==RESET && running){prefs.edit().putFloat("px",.75f).putFloat("py",.3f).remove("center_x").remove("center_y").apply();restorePosition();save();updateWindow()}
        return START_NOT_STICKY
    }
    private fun notification(){
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("bit","Floating Bit",NotificationManager.IMPORTANCE_LOW).apply{description="Controls for the enabled floating Bit";setSound(null,null)})
        val settings=PendingIntent.getActivity(this,0,Intent(this,SettingsActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,1,Intent(this,BitService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n=Notification.Builder(this,"bit").setSmallIcon(org.sksoft.bit.R.drawable.ic_notification).setContentTitle("Bit is floating")
            .setContentText("Tap Bit for a response • drag to move").setContentIntent(settings).setOngoing(true).setShowWhen(false)
            .addAction(Notification.Action.Builder(null,"Open settings",settings).build())
            .addAction(Notification.Action.Builder(null,"Stop Bit",stop).build()).build()
        if(Build.VERSION.SDK_INT>=34)startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1,n)
    }
    private fun bounds(){
        if(Build.VERSION.SDK_INT>=30){
            val m=wm.currentWindowMetrics
            val insets=m.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            safe=Rect(insets.left,insets.top,m.bounds.width()-insets.right,m.bounds.height()-insets.bottom)
        }else{
            val metrics=android.util.DisplayMetrics();@Suppress("DEPRECATION") wm.defaultDisplay.getMetrics(metrics)
            val top=resources.getIdentifier("status_bar_height","dimen","android").let{if(it>0)resources.getDimensionPixelSize(it) else 0}
            safe=Rect(0,top,metrics.widthPixels,metrics.heightPixels)
        }
        if(safe.width()<1 || safe.height()<1)safe=Rect(0,0,1,1)
    }
    private fun desiredSize()=Placement.size(prefs.getInt("size",35),resources.displayMetrics.density,safe.width(),safe.height())
    private fun edgePercent()=prefs.getInt("edge_extension",0).coerceIn(0,50)
    private fun updateEdgeFlag(){
        params.flags=if(edgePercent()>0) params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            else params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS.inv()
    }
    private fun restorePosition(){
        // Migrate legacy fully-on-screen fractions without moving existing installations.
        params.x=safe.left+if(prefs.contains("center_x"))
            Placement.fromCenter(prefs.getFloat("center_x",.5f),safe.width(),params.width)
            else ((safe.width()-params.width).coerceAtLeast(0)*prefs.getFloat("px",.75f)).roundToInt()
        params.y=safe.top+if(prefs.contains("center_y"))
            Placement.fromCenter(prefs.getFloat("center_y",.5f),safe.height(),params.height)
            else ((safe.height()-params.height).coerceAtLeast(0)*prefs.getFloat("py",.3f)).roundToInt()
        clamp()
    }
    private fun clamp(){
        val over=edgePercent()
        params.x=safe.left+Placement.clamp(params.x-safe.left,safe.width(),params.width,over)
        params.y=safe.top+Placement.clamp(params.y-safe.top,safe.height(),params.height,over)
    }
    private fun save(){prefs.edit()
        .putFloat("center_x",Placement.centerFraction(params.x-safe.left,safe.width(),params.width))
        .putFloat("center_y",Placement.centerFraction(params.y-safe.top,safe.height(),params.height)).apply()}
    private fun extensionChanged(){
        if(!running)return
        updateEdgeFlag();clamp();save();updateWindow()
    }
    private fun updateWindow(){try{view?.let{wm.updateViewLayout(it,params)}}catch(_:Exception){stopSelf()}}
    private fun resize(){
        if(!running)return
        val centerX=params.x+params.width/2f;val centerY=params.y+params.height/2f
        bounds();val size=desiredSize();params.width=size;params.height=size
        params.x=(centerX-size/2f).roundToInt();params.y=(centerY-size/2f).roundToInt();clamp();save();updateWindow()
    }
    private fun touch(v:GLSurfaceView){
        var downX=0f;var downY=0f;var startX=0;var startY=0;var dragged=false;var cancelled=false
        val slop=ViewConfiguration.get(this).scaledTouchSlop
        v.contentDescription="Bit. Tap for yes or no; drag to move."
        v.setOnTouchListener { _, e ->
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{downX=e.rawX;downY=e.rawY;startX=params.x;startY=params.y;dragged=false;cancelled=false}
                MotionEvent.ACTION_POINTER_DOWN->cancelled=true
                MotionEvent.ACTION_MOVE->{val dx=e.rawX-downX;val dy=e.rawY-downY
                    if(hypot(dx.toDouble(),dy.toDouble())>slop)dragged=true
                    if(dragged){params.x=startX+dx.roundToInt();params.y=startY+dy.roundToInt();clamp();updateWindow()}}
                MotionEvent.ACTION_UP->{
                    if(hypot((e.rawX-downX).toDouble(),(e.rawY-downY).toDouble())>slop)dragged=true
                    if(dragged)save() else if(!cancelled && renderer.response==0){
                        val yes=random.nextYes()
                        renderer.responseStart=tick;renderer.response=if(yes)1 else 2;audio.play(yes);v.performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL->{cancelled=true;if(dragged)save()}
            };true
        }
    }
    private fun pause(){
        handler.removeCallbacks(frame)
        if(active){active=false;view?.visibility=View.INVISIBLE;view?.onPause()}
    }
    private fun updateScreen(){
        if(!Settings.canDrawOverlays(this)){stopSelf();return}
        if(power.isInteractive && !keyguard.isKeyguardLocked){
            if(!active && view!=null){active=true;lastTime=SystemClock.uptimeMillis();view?.visibility=View.VISIBLE;view?.onResume();handler.post(frame)}
        } else {pause();view?.visibility=View.INVISIBLE;view?.onPause()}
    }
    override fun onSharedPreferenceChanged(p:SharedPreferences,key:String?){when(key){"size"->resize();"edge_extension"->extensionChanged();"volume"->audio.volume=p.getInt("volume",70)}}
    override fun onConfigurationChanged(c:android.content.res.Configuration){super.onConfigurationChanged(c);displayChanged()}
    private fun displayChanged(){if(running){bounds();val s=desiredSize();params.width=s;params.height=s;restorePosition();updateWindow()}}
    override fun onDisplayChanged(id:Int){displayChanged()}
    override fun onDisplayAdded(id:Int){}
    override fun onDisplayRemoved(id:Int){}
    override fun onDestroy(){
        pause();view?.let{v->try{wm.removeView(v)}catch(_:Exception){}};view=null
        audio.stop();prefs.unregisterOnSharedPreferenceChangeListener(this);displays.unregisterDisplayListener(this)
        if(receiverRegistered)unregisterReceiver(screen)
        ops?.stopWatchingMode(permissionListener)
        running=false;prefs.edit().putBoolean("enabled",false).apply();stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
