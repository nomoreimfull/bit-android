package org.sksoft.bit

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*

class SettingsActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs by lazy{getSharedPreferences("bit",MODE_PRIVATE)}
    private lateinit var enabled:Switch
    private lateinit var status:TextView
    private lateinit var audio:Audio
    private var updating=false
    private var awaitingPermission=false
    private val handler=Handler(Looper.getMainLooper())
    private val refresh=object:Runnable{override fun run(){sync();handler.postDelayed(this,1000)}}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private val cyan=Color.rgb(84,231,244)
    private val red=Color.rgb(255,53,73)
    private val graphite=Color.rgb(23,26,30)
    private fun label(value:String,size:Float=14f,color:Int=Color.rgb(171,183,191))=TextView(this).apply {
        text=value;textSize=size;setTextColor(color);typeface=Typeface.MONOSPACE
        setPadding(0,dp(7),0,dp(7))
    }
    private fun panel(accent:Int=cyan)=GradientDrawable().apply {
        setColor(graphite);setStroke(dp(1).coerceAtLeast(1),accent);cornerRadius=dp(2).toFloat()
    }
    private fun button(title:String,accent:Int=cyan,action:()->Unit)=Button(this).apply {
        text=title;textSize=12f;typeface=Typeface.MONOSPACE;letterSpacing=.06f
        setTextColor(accent);isAllCaps=true;minHeight=dp(52)
        background=android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x3354e7f4),panel(accent),null)
        setPadding(dp(12),dp(8),dp(12),dp(8));setOnClickListener{action()}
    }
    override fun onCreate(state:Bundle?){
        super.onCreate(state);audio=Audio(this);audio.volume=prefs.getInt("volume",70)
        awaitingPermission=state?.getBoolean("awaiting",false)?:false
        prefs.registerOnSharedPreferenceChangeListener(this)
        if(state==null && intent.action==Intent.ACTION_MAIN && !BitService.running){
            val splash=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)}
            val logo=ImageView(this).apply {
                setImageResource(R.drawable.splash_logo);scaleType=ImageView.ScaleType.FIT_CENTER
                contentDescription="BIT";setPadding(dp(20),dp(20),dp(20),dp(20))
            }
            splash.addView(logo,FrameLayout.LayoutParams(-1,-1));setContentView(splash)
            handler.postDelayed({if(!isFinishing && !isDestroyed)showSettings()},850)
        } else showSettings()
    }
    private fun showSettings(){
        val scroll=ScrollView(this).apply{setBackgroundColor(Color.BLACK);isFillViewport=true}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(22),dp(22),dp(32))}
        scroll.addView(root);setContentView(scroll)
        root.setOnApplyWindowInsetsListener{v,ins->
            if(Build.VERSION.SDK_INT>=30){val b=ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout());v.setPadding(dp(22)+b.left,dp(22)+b.top,dp(22)+b.right,dp(32)+b.bottom)};ins}
        root.requestApplyInsets()
        fun rule(color:Int){root.addView(View(this).apply{setBackgroundColor(color)},LinearLayout.LayoutParams(-1,dp(2)).apply{topMargin=dp(8);bottomMargin=dp(12)})}
        fun card(title:String,accent:Int=cyan):LinearLayout {
            val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=panel(Color.rgb(50,59,65));setPadding(dp(16),dp(12),dp(16),dp(16))}
            root.addView(body,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(14)})
            body.addView(label(title,11f,accent).apply{letterSpacing=.14f})
            return body
        }
        root.addView(label("BIT",42f,cyan).apply{letterSpacing=.22f;setShadowLayer(dp(8).toFloat(),0f,0f,0x6654e7f4)})
        root.addView(label("PERSONAL / DIGITAL COMPANION",11f,Color.rgb(137,151,160)).apply{letterSpacing=.1f})
        rule(red)
        root.addView(label("YES. NO. A LITTLE CERTAINTY.",13f,cyan))
        root.addView(label("Tap Bit for an answer. Drag to move.",13f))
        val control=card("01 / CONNECTION")
        enabled=Switch(this).apply {
            text="Enable floating Bit";textSize=17f;typeface=Typeface.MONOSPACE;setTextColor(Color.rgb(224,249,251))
            setPadding(0,dp(12),0,dp(12));minHeight=dp(56)
            thumbTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(cyan,Color.rgb(124,132,139)))
            trackTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(Color.rgb(30,100,108),Color.rgb(56,61,66)))
        }
        control.addView(enabled)
        status=label("",12f);control.addView(status)
        enabled.setOnCheckedChangeListener{_,checked->if(!updating){
            if(checked)enable() else {stopService(Intent(this,BitService::class.java));prefs.edit().putBoolean("enabled",false).apply();sync()}
        }}
        fun slider(body:LinearLayout,title:String,key:String,default:Int,accent:Int,maximum:Int=100,suffix:String=""){
            val value=label("$title: ${prefs.getInt(key,default)}$suffix",18f,accent);body.addView(value)
            val slider=SeekBar(this).apply {
                max=maximum;progress=prefs.getInt(key,default);contentDescription=title
                progressTintList=ColorStateList.valueOf(accent);thumbTintList=ColorStateList.valueOf(accent)
                progressBackgroundTintList=ColorStateList.valueOf(Color.rgb(64,71,77))
            }
            body.addView(slider,LinearLayout.LayoutParams(-1,dp(52)))
            slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(s:SeekBar,n:Int,user:Boolean){value.text="$title: $n$suffix";if(user)prefs.edit().putInt(key,n).apply()}
                override fun onStartTrackingTouch(s:SeekBar){}
                override fun onStopTrackingTouch(s:SeekBar){}
            })
        }
        val size=card("02 / GEOMETRY")
        slider(size,"Size","size",35,cyan)
        size.addView(label("0 is the smallest usable Bit. Maximum size fits your screen.",12f))
        slider(size,"Edge extension","edge_extension",0,cyan,50,"%")
        size.addView(label("Increase, then drag Bit toward an edge. 0% keeps its square window inside; 50% allows half its width or height beyond each edge. Shapes can be clipped. Reset position brings it back.",12f))
        val sound=card("03 / AUDIO",red)
        slider(sound,"Volume","volume",70,red)
        sound.addView(label("Media volume · 0 mutes Bit. Your phone’s volume stays unchanged.",12f))
        val previews=LinearLayout(this)
        for(yes in listOf(true,false)){
            val b=button(if(yes)"Preview yes" else "Preview no",if(yes)cyan else red){
                audio.volume=prefs.getInt("volume",70)
                if(audio.volume==0)Toast.makeText(this,"Bit is muted",Toast.LENGTH_SHORT).show()
                if(!audio.play(yes))Toast.makeText(this,"Audio is unavailable right now",Toast.LENGTH_SHORT).show()
            }
            previews.addView(b,LinearLayout.LayoutParams(0,-2,1f).apply{if(yes)rightMargin=dp(8)})
        };sound.addView(previews,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)})
        val system=card("04 / SYSTEM")
        system.addView(button("Reset position"){
            prefs.edit().putFloat("px",.75f).putFloat("py",.3f).remove("center_x").remove("center_y").apply()
            if(BitService.running)startService(Intent(this,BitService::class.java).setAction(BitService.RESET))
            Toast.makeText(this,"Position reset",Toast.LENGTH_SHORT).show()
        })
        system.addView(button("Notification settings"){
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
        },LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
        val about=card("ARCHIVE / ABOUT")
        about.addView(label("Bit 1.0.0.2\nOriginal Windows application © 2005 Anders Hovmöller, SK Soft.\n\nThis independent Android port uses recovered geometry and original response audio. Audio metadata credits Tron, Walt Disney Pictures; source: SciFlicks.com. Splash artwork supplied by the user.\n\nAndroid may hide overlays on protected screens and stop background processes. Re-enable Bit here if needed. No boot start. Screen-off and lock pause animation. Android 11+ is recommended for precise screen bounds.",12f))
        rule(cyan)
        root.addView(label("END OF LINE",11f,red).apply{letterSpacing=.2f;gravity=Gravity.END})
        sync()
    }
    private fun enable(){
        if(!Settings.canDrawOverlays(this)){
            updating=true;enabled.isChecked=false;updating=false
            AlertDialog.Builder(this).setTitle("Allow Bit to float")
                .setMessage("Bit needs ‘Display over other apps’ to show a small draggable companion while you use other apps. Allow it on the next screen, then return here. You can turn it off at any time.")
                .setPositiveButton("Open permission settings"){_,_->
                    awaitingPermission=true
                    try{startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}catch(_:Exception){awaitingPermission=false;status.text="Overlay settings are unavailable on this device."}
                }.setNegativeButton("Cancel"){_,_->sync()}.show()
            return
        }
        prefs.edit().remove("error").apply()
        try{startForegroundService(Intent(this,BitService::class.java))}catch(e:Exception){prefs.edit().putString("error","Android could not start Bit: ${e.javaClass.simpleName}").apply();sync()}
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),7)
        handler.postDelayed({sync()},200)
    }
    private fun sync(){
        if(!::enabled.isInitialized)return
        val allowed=Settings.canDrawOverlays(this)
        if(!allowed && BitService.running)stopService(Intent(this,BitService::class.java))
        updating=true;enabled.isChecked=allowed && BitService.running;updating=false
        val notifications=getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        status.text=when{
            !allowed->"Overlay permission is off. Enable Bit to open Android’s permission settings."
            BitService.running->"Bit is enabled. You can close settings." + if(!notifications) " Notifications are off; enable them below for persistent controls. Android still lists the foreground service in Active apps." else ""
            else->prefs.getString("error",null)?:"Bit is stopped. Enable it to float over your apps."
        }
    }
    override fun onResume(){super.onResume()
        if(awaitingPermission){awaitingPermission=false;if(Settings.canDrawOverlays(this))enable() else Toast.makeText(this,"Permission was not granted. Bit remains off.",Toast.LENGTH_LONG).show()}
        handler.post(refresh)
    }
    override fun onPause(){handler.removeCallbacks(refresh);audio.stop();super.onPause()}
    override fun onSaveInstanceState(out:Bundle){out.putBoolean("awaiting",awaitingPermission);super.onSaveInstanceState(out)}
    override fun onSharedPreferenceChanged(p:SharedPreferences,key:String?){if(key=="enabled" || key=="error")sync();if(key=="volume")audio.volume=p.getInt("volume",70)}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);prefs.unregisterOnSharedPreferenceChangeListener(this);audio.stop();super.onDestroy()}
}
