package com.midi.mainstage

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private const val TAG = "StageKeysBenchmark"

data class BenchmarkSample(
    val elapsedSec: Long,
    val activeVoices: Int,
    val dspCpuLoadPercent: Double,
    val xRuns: Int,
    val ramPssMb: Double,
    val nativeHeapMb: Double,
    val batteryTempC: Double,
    val thermalStatus: Int,
    val limiterActive: Boolean
)

data class BenchmarkSummary(
    val modeName: String,
    val logicalChannels: Int,
    val globalPolyphony: Int,
    val durationSeconds: Long,
    val avgDspCpuPercent: Double,
    val peakDspCpuPercent: Double,
    val totalXRuns: Int,
    val avgRamPssMb: Double,
    val peakRamPssMb: Double,
    val startTempC: Double,
    val endTempC: Double,
    val maxTempC: Double,
    val tempDeltaC: Double,
    val maxThermalStatus: Int,
    val thermalStatusDescription: String,
    val samplesCount: Int
)

class BenchmarkRunner(
    private val context: Context,
    private val synth: PlatformAudioSynth
) {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    fun startBenchmark(
        durationSeconds: Long = 900L, // 15 minutes default
        onProgress: (Int, BenchmarkSample) -> Unit = { _, _ -> },
        onComplete: (BenchmarkSummary) -> Unit = {}
    ) {
        job?.cancel()
        job = scope.launch {
            val modeName = synth.getBenchmarkModeName()
            val numChannels = synth.getLogicalChannelCount()
            val polyphony = synth.getGlobalPolyphony()

            Log.i(TAG, "=== INICIANDO BENCHMARK: $modeName (Canales=$numChannels, Polifonia=$polyphony, Duracion=${durationSeconds}s) ===")
            synth.onBenchmarkStarted?.invoke(numChannels)

            // 1. Gather all real, distinct SF2 files from filesDir and cacheDir
            val candidates = mutableListOf<File>()
            context.filesDir.listFiles { _, name -> name.endsWith(".sf2") }?.let { candidates.addAll(it) }
            File(context.filesDir, "soundfonts").listFiles { _, name -> name.endsWith(".sf2") }?.let { candidates.addAll(it) }
            context.cacheDir.listFiles { _, name -> name.endsWith(".sf2") && !name.startsWith("bench_sf2_") }?.let { candidates.addAll(it) }
            
            val realSf2Files = candidates.filter { it.exists() && it.length() > 0 }
                .distinctBy { it.name }
                .sortedByDescending { it.length() }

            val baseSf2 = File(context.cacheDir, "PianoDefault.sf2")
            if (!baseSf2.exists()) {
                context.assets.open("PianoDefault.sf2").use { input ->
                    FileOutputStream(baseSf2).use { output -> input.copyTo(output) }
                }
            }

            Log.i(TAG, "Cargando $numChannels SoundFonts REALES para el benchmark de memoria (encontrados ${realSf2Files.size} SF2 reales):")
            for (ch in 0 until numChannels) {
                val targetFile = if (ch < realSf2Files.size) {
                    realSf2Files[ch]
                } else {
                    val fallback = File(context.cacheDir, "bench_sf2_ch$ch.sf2")
                    if (!fallback.exists() || fallback.length() != baseSf2.length()) {
                        baseSf2.copyTo(fallback, overwrite = true)
                    }
                    fallback
                }
                
                val sizeMb = targetFile.length() / (1024.0 * 1024.0)
                Log.i(TAG, "  -> Canal $ch: ${targetFile.name} (${String.format("%.1f", sizeMb)} MB)")
                synth.loadSoundFont(targetFile.absolutePath, ch)
                synth.setChannelVolume(0.85f, ch)
                synth.setChannelReverbSend(ch, 0.65f)
                synth.setChannelChorusSend(ch, 0.55f)
                synth.setFilterCutoff(0.75f, ch)
            }

            synth.setMasterLimiterEnabled(true)
            synth.setVolume(0.9f)

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

            val samples = mutableListOf<BenchmarkSample>()
            val startTime = System.currentTimeMillis()
            var lastSampleTime = 0L

            // Launch chord player loop
            val playerJob = launch {
                val chords = listOf(
                    intArrayOf(48, 52, 55, 59, 62, 66), // Cmaj9#11
                    intArrayOf(45, 48, 52, 55, 59, 64), // Am9
                    intArrayOf(41, 45, 48, 53, 57, 60), // Fmaj7
                    intArrayOf(43, 47, 50, 55, 57, 62)  // Gsus4(9)
                )

                var chordIdx = 0
                while (isActive) {
                    val chord = chords[chordIdx % chords.size]
                    chordIdx++

                    // Trigger notes across all active logical channels with stagger
                    for (ch in 0 until numChannels) {
                        val transpose = (ch % 3) * 12
                        for (note in chord) {
                            synth.noteOn(note + transpose, 90 + (ch % 30), ch)
                        }
                        synth.onBenchmarkVuUpdate?.invoke(ch, 0.85f)
                    }

                    delay(800L) // Sustained chord duration

                    // Release some notes while keeping long release tail / sustain
                    for (ch in 0 until numChannels) {
                        val transpose = (ch % 3) * 12
                        for (note in chord) {
                            synth.noteOff(note + transpose, ch)
                        }
                        synth.onBenchmarkVuUpdate?.invoke(ch, 0f)
                    }

                    delay(200L)
                }
            }

            // Sampling loop: sample every 2s for first 2 minutes (120s), then every 5s thereafter
            val initialTemp = getBatteryTemperature()
            Log.i(TAG, "Temperatura inicial: ${initialTemp}°C")

            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsedSec = (now - startTime) / 1000L

                if (elapsedSec >= durationSeconds) {
                    break
                }

                val currentInterval = if (elapsedSec < 120L) 2000L else 5000L
                if (now - lastSampleTime >= currentInterval) {
                    lastSampleTime = now

                    val activeVoices = synth.getActiveVoiceCount()
                    val dspCpu = synth.getDspCpuLoad()
                    val xruns = synth.getXRunCount()
                    val ramPss = getProcessPssMb()
                    val nativeHeap = (Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0))
                    val currentTemp = getBatteryTemperature()
                    val thermalStatus = if (Build.VERSION.SDK_INT >= 29 && powerManager != null) {
                        powerManager.currentThermalStatus
                    } else 0
                    val limiterActive = synth.isMasterLimiterActive()

                    val sample = BenchmarkSample(
                        elapsedSec = elapsedSec,
                        activeVoices = activeVoices,
                        dspCpuLoadPercent = dspCpu,
                        xRuns = xruns,
                        ramPssMb = ramPss,
                        nativeHeapMb = nativeHeap,
                        batteryTempC = currentTemp,
                        thermalStatus = thermalStatus,
                        limiterActive = limiterActive
                    )
                    samples.add(sample)

                    val progressPercent = ((elapsedSec * 100) / durationSeconds).toInt()
                    onProgress(progressPercent, sample)

                    Log.i(TAG, "[$modeName ${progressPercent}% (${elapsedSec}s)] Voces: $activeVoices | DSP CPU: ${String.format("%.1f", dspCpu)}% | xRuns: $xruns | RAM: ${ramPss.toInt()}MB | Temp: ${currentTemp}°C | Thermal: $thermalStatus")
                    
                    // Continuously flush JSON to external storage
                    saveInterimData(modeName, numChannels, polyphony, durationSeconds, initialTemp, currentTemp, thermalStatus, xruns, samples)
                }

                delay(200L)
            }

            playerJob.cancelAndJoin()
            synth.allNotesOff()

            val finalTemp = getBatteryTemperature()
            val maxTemp = samples.maxOfOrNull { it.batteryTempC } ?: finalTemp
            val avgDspCpu = if (samples.isNotEmpty()) samples.map { it.dspCpuLoadPercent }.average() else 0.0
            val peakDspCpu = samples.maxOfOrNull { it.dspCpuLoadPercent } ?: 0.0
            val avgRam = if (samples.isNotEmpty()) samples.map { it.ramPssMb }.average() else 0.0
            val peakRam = samples.maxOfOrNull { it.ramPssMb } ?: 0.0
            val maxThermal = samples.maxOfOrNull { it.thermalStatus } ?: 0

            val thermalDesc = when (maxThermal) {
                0 -> "NONE (Sin limitación térmica)"
                1 -> "LIGHT (Aumento ligero de temperatura)"
                2 -> "MODERATE (Moderado)"
                3 -> "SEVERE (Throttling severo)"
                4 -> "CRITICAL (Crítico)"
                5 -> "EMERGENCY (Emergencia)"
                6 -> "SHUTDOWN (Apagado)"
                else -> "Estado $maxThermal"
            }

            val summary = BenchmarkSummary(
                modeName = modeName,
                logicalChannels = numChannels,
                globalPolyphony = polyphony,
                durationSeconds = durationSeconds,
                avgDspCpuPercent = avgDspCpu,
                peakDspCpuPercent = peakDspCpu,
                totalXRuns = synth.getXRunCount(),
                avgRamPssMb = avgRam,
                peakRamPssMb = peakRam,
                startTempC = initialTemp,
                endTempC = finalTemp,
                maxTempC = maxTemp,
                tempDeltaC = (finalTemp - initialTemp),
                maxThermalStatus = maxThermal,
                thermalStatusDescription = thermalDesc,
                samplesCount = samples.size
            )

            saveBenchmarkData(summary, samples)
            onComplete(summary)
            Log.i(TAG, "=== BENCHMARK COMPLETADO: $summary ===")
        }
    }

    fun stopBenchmark() {
        job?.cancel()
        synth.allNotesOff()
    }

    private fun getBatteryTemperature(): Double {
        return try {
            val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            temp / 10.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun getProcessPssMb(): Double {
        return try {
            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            memInfo.totalPss / 1024.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun saveInterimData(
        modeName: String,
        channels: Int,
        polyphony: Int,
        duration: Long,
        startTemp: Double,
        currTemp: Double,
        currThermal: Int,
        latestXRuns: Int,
        samples: List<BenchmarkSample>
    ) {
        try {
            val avgDspCpu = if (samples.isNotEmpty()) samples.map { it.dspCpuLoadPercent }.average() else 0.0
            val peakDspCpu = samples.maxOfOrNull { it.dspCpuLoadPercent } ?: 0.0
            val avgRam = if (samples.isNotEmpty()) samples.map { it.ramPssMb }.average() else 0.0
            val peakRam = samples.maxOfOrNull { it.ramPssMb } ?: 0.0
            val maxTemp = samples.maxOfOrNull { it.batteryTempC } ?: currTemp
            val maxThermal = samples.maxOfOrNull { it.thermalStatus } ?: currThermal

            val summary = BenchmarkSummary(
                modeName = modeName,
                logicalChannels = channels,
                globalPolyphony = polyphony,
                durationSeconds = duration,
                avgDspCpuPercent = avgDspCpu,
                peakDspCpuPercent = peakDspCpu,
                totalXRuns = latestXRuns,
                avgRamPssMb = avgRam,
                peakRamPssMb = peakRam,
                startTempC = startTemp,
                endTempC = currTemp,
                maxTempC = maxTemp,
                tempDeltaC = (currTemp - startTemp),
                maxThermalStatus = maxThermal,
                thermalStatusDescription = if (maxThermal == 0) "NONE" else "THROTTLED ($maxThermal)",
                samplesCount = samples.size
            )
            saveBenchmarkData(summary, samples)
        } catch (e: Exception) {
            // Non-critical
        }
    }

    private fun saveBenchmarkData(summary: BenchmarkSummary, samples: List<BenchmarkSample>) {
        try {
            val root = JSONObject()
            root.put("modeName", summary.modeName)
            root.put("logicalChannels", summary.logicalChannels)
            root.put("globalPolyphony", summary.globalPolyphony)
            root.put("durationSeconds", summary.durationSeconds)
            root.put("avgDspCpuPercent", summary.avgDspCpuPercent)
            root.put("peakDspCpuPercent", summary.peakDspCpuPercent)
            root.put("totalXRuns", summary.totalXRuns)
            root.put("avgRamPssMb", summary.avgRamPssMb)
            root.put("peakRamPssMb", summary.peakRamPssMb)
            root.put("startTempC", summary.startTempC)
            root.put("endTempC", summary.endTempC)
            root.put("maxTempC", summary.maxTempC)
            root.put("tempDeltaC", summary.tempDeltaC)
            root.put("maxThermalStatus", summary.maxThermalStatus)
            root.put("thermalStatusDescription", summary.thermalStatusDescription)

            val samplesArray = JSONArray()
            for (s in samples) {
                val obj = JSONObject()
                obj.put("sec", s.elapsedSec)
                obj.put("voices", s.activeVoices)
                obj.put("dspCpu", s.dspCpuLoadPercent)
                obj.put("xruns", s.xRuns)
                obj.put("ramMb", s.ramPssMb)
                obj.put("heapMb", s.nativeHeapMb)
                obj.put("tempC", s.batteryTempC)
                obj.put("thermal", s.thermalStatus)
                samplesArray.put(obj)
            }
            root.put("samples", samplesArray)

            val outFile = File(context.getExternalFilesDir(null) ?: context.filesDir, "benchmark_${summary.modeName}.json")
            outFile.writeText(root.toString(2))
            Log.i(TAG, "Benchmark guardado en: ${outFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando datos del benchmark", e)
        }
    }
}
