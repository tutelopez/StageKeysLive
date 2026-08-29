package com.midi.mainstage

import android.os.Debug
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.roundToInt

class AndroidPerformanceMonitor {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastUserTime = 0L
    private var lastSysTime = 0L
    private var lastTime = 0L
    private var listener: ((PerformanceStats) -> Unit)? = null

    fun setListener(onStats: (PerformanceStats) -> Unit) {
        this.listener = onStats
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val cpu = getCpuUsage()
                val ramMb = getNativeHeapMb()
                listener?.invoke(PerformanceStats(cpu, ramMb))
                delay(1500)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun getCpuUsage(): Int? {
        return try {
            val statFile = File("/proc/self/stat")
            if (!statFile.exists()) return null
            val content = statFile.readText()
            val tokens = content.split(" ")
            if (tokens.size < 15) return null
            
            // utime = 13, stime = 14 (0-indexed)
            val utime = tokens[13].toLong()
            val stime = tokens[14].toLong()
            val currentTime = System.currentTimeMillis()
            
            if (lastTime != 0L) {
                val dt = currentTime - lastTime
                if (dt > 0) {
                    val du = utime - lastUserTime
                    val ds = stime - lastSysTime
                    val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                    // Convert clock ticks to ms. In Android, _SC_CLK_TCK is usually 100 Hz (10ms per tick)
                    val cpuTicksMs = (du + ds) * 10
                    val percent = ((cpuTicksMs.toFloat() / dt.toFloat()) / numCores.toFloat() * 100f).roundToInt()
                    
                    lastUserTime = utime
                    lastSysTime = stime
                    lastTime = currentTime
                    return percent.coerceIn(0, 100)
                }
            }
            
            lastUserTime = utime
            lastSysTime = stime
            lastTime = currentTime
            
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun getNativeHeapMb(): Int {
        val bytes = Debug.getNativeHeapAllocatedSize()
        return (bytes / (1024 * 1024)).toInt()
    }
}
