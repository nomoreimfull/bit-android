package org.sksoft.bit

import java.io.File
import kotlin.math.*

fun main(args: Array<String>) {
    val lines=File(args[0]).readLines()
    lines.forEachIndexed { i,line ->
        val expected=line.toDouble();val actual=if(i+1>=Motion.RESPONSE_TICKS)0.0 else Motion.responsePhase((i+1).toLong())
        check(abs(actual-expected)<1e-8){"Original timer mismatch at tick ${i+1}: $actual / $expected"}
    }
    check(Motion.RESPONSE_TICKS*Motion.TICK_MS==854L)
    check(Motion.responseBlend(0)==0f && Motion.responseBlend(61)==0f)
    check(Motion.responseBlend(17)>.99f)
    check(Motion.responseBlend(1)>Motion.responseBlend(60))
    val random=OriginalRandom(1)
    check(List(5){random.nextValue()}==listOf(41,18467,6334,26500,19169))
    val decision=OriginalRandom(1)
    check(List(5){decision.nextYes()}==listOf(true,true,false,false,true))
    var cases=0
    for(d in listOf(.75f,1f,2f,3f,4f)) for((w,h) in listOf(1080 to 2200,2200 to 1080,320 to 240,80 to 60,1 to 1)) {
        var prev=0
        for(v in 0..100){
            val s=Placement.size(v,d,w,h)
            check(s in 1..min(w,h) && s>=prev);prev=s
            for(x in listOf(-10000,-1,0,17,w,10000)){
                val clamped=Placement.clamp(x,w,s)
                check(clamped>=0 && clamped+s<=w)
                val fraction=Placement.fraction(clamped,w,s)
                check(fraction in 0f..1f)
                val restored=((w-s)*fraction).roundToInt()
                check(abs(restored-clamped)<=1)
            };cases++
        }
    }
    // Experimental extension: fixed examples prevent an internally consistent but wrong clamp.
    check(Placement.clamp(-500,1080,200,25)==-50)
    check(Placement.clamp(2000,1080,200,25)==930)
    check(Placement.clamp(-500,1080,200,50)==-100)
    check(Placement.clamp(2000,1080,200,50)==980)
    check(Placement.clamp(-100,1080,200,0)==0) // turning it off recovers the window
    check(Placement.fromCenter(Placement.centerFraction(-100,1080,200),1080,200)==-100)
    check(Placement.fromCenter(Placement.centerFraction(980,1080,200),1080,200)==980)
    // Same centered location persists across restart/rotation, including partial off-screen poses.
    var extendedCases=0
    for((w,h) in listOf(1080 to 2200,2200 to 1080,320 to 240,80 to 60,1 to 1)) {
        for(v in listOf(0,35,100)) for(percent in 0..50) {
            val size=Placement.size(v,1f,w,h)
            for(edge in listOf(w,h)) for(pos in listOf(-10000,-1,0,edge/2,edge,10000)) {
                val x=Placement.clamp(pos,edge,size,percent)
                val center=x+size/2f
                check(center>=0f && center<=edge) // cannot lose the entire object
                check(x+size>=size/2f && edge-x>=size/2f)
                val f=Placement.centerFraction(x,edge,size)
                val restored=Placement.clamp(Placement.fromCenter(f,edge,size),edge,size,percent)
                check(abs(restored-x)<=1)
                val rotated=Placement.clamp(Placement.fromCenter(f,h,size),h,size,percent)
                check(rotated+size/2f>=0f && rotated+size/2f<=h)
                val strict=Placement.clamp(x,edge,size,0)
                check(strict>=0 && strict+size<=edge)
                extendedCases++
            }
        }
    }
    // Legacy position migration preserves the current window before any resize.
    val legacy=(1080-200)*.75f
    val migrated=Placement.centerFraction(legacy.roundToInt(),1080,200)
    check(Placement.fromCenter(migrated,1080,200)==legacy.roundToInt())
    val enlarged=Placement.fromCenter(migrated,1080,300)
    check(abs((enlarged+150)-(legacy+100))<=1f)
    check(Placement.extension(200,-20)==0 && Placement.extension(200,200)==100)
    check(Placement.fromCenter(Float.NaN,1000,200)==400)
    check(Motion.neutralScales(0,0f,0f).contentEquals(Motion.neutralScales(288,0f,0f)))
    // Exact neutral scale reference at t=0 and two response extremes.
    val s=Motion.neutralScales(0,0f,0f)
    check(abs(s[0]-.9f)<1e-6 && abs(s[1]-1.5f)<1e-4)
    check(Motion.neutralScales(0,1f,0f)[0]<s[0])
    println("PASS: actual x86 timer fixture, return to neutral, MSVC selection, $cases size/bounds/position cases, $extendedCases extension/persistence/recovery cases")
}
