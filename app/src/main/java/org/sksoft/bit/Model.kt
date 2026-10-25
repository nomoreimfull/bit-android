package org.sksoft.bit

import kotlin.math.*

/** Original timer: floor(100 / (4 * 1.7)) = 14ms; every tick advances base by 1/4. */
object Motion {
    const val TICK_MS = 14L
    const val RAD = 0.017444444444444446 // original executable's slightly rounded pi/180
    const val RESPONSE_TICKS = 61 // 17 ticks at 5.5deg, then 44 ticks at 2deg
    fun responsePhase(ticks: Long): Double = if (ticks <= 17) ticks * 5.5 else 93.5 + (ticks - 17) * 2.0
    fun responseBlend(ticks: Long): Float = if (ticks >= RESPONSE_TICKS) 0f else sin(responsePhase(ticks) * RAD).toFloat().coerceAtLeast(0f)
    fun neutralScales(ticks: Long, yes: Float, no: Float): FloatArray {
        val angle = (ticks * 1.25 % 360) * RAD
        return floatArrayOf((abs(sin(angle)) * .5 + .9 - yes * .5 - no).toFloat(),
            (abs(sin(angle + 90 * RAD)) * .5 + 1 - yes * .5 - no).toFloat())
    }
    val neutral = floatArrayOf(0.3725490272f, 0.6235294342f, 0.9176470637f)
    val yes = floatArrayOf(0.9879999757f, 0.8000000119f, 0.25f)
    val no = floatArrayOf(0.7879999876f, 0.3499999940f, 0.0750000030f)
}

object Placement {
    fun size(value: Int, density: Float, width: Int, height: Int): Int {
        val cap = min(min(width, height).toFloat(), 280f * density).coerceAtLeast(1f)
        val low = min(56f * density, cap)
        return (low + (cap - low) * value.coerceIn(0, 100) / 100f).roundToInt().coerceAtLeast(1)
    }
    /** Half a window at most: its center stays in the usable screen, including at corners. */
    fun extension(size: Int, percent: Int) = (size * percent.coerceIn(0, 50) / 100f).roundToInt().coerceAtMost(size / 2)
    fun clamp(pos: Int, edge: Int, size: Int, percent: Int = 0): Int {
        val over = extension(size, percent)
        return pos.coerceIn(-over, max(0, edge - size) + over)
    }
    fun centerFraction(pos: Int, edge: Int, size: Int) = ((pos + size / 2f) / edge.coerceAtLeast(1)).coerceIn(0f, 1f)
    fun fromCenter(fraction: Float, edge: Int, size: Int): Int {
        val value = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else .5f
        return (value * edge - size / 2f).roundToInt()
    }
    fun fraction(pos: Int, edge: Int, size: Int): Float = if (edge <= size) .5f else pos.toFloat() / (edge - size)
}

/** MSVC rand() recovered at 0x403afa, seeded with time(NULL) as in the EXE. */
class OriginalRandom(private var seed: Int) {
    fun nextValue(): Int { seed = seed * 214013 + 2531011; return (seed ushr 16) and 0x7fff }
    fun nextYes() = (nextValue() and 1) != 0
}
