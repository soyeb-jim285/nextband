package com.soyeb.ieltspractice.media

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Microphone side of the speaking flows (port of iOS Audio/Recorder.swift, LiveAudio.swift). One AudioRecord pipeline
// feeds everything: an AAC .m4a file (what the server expects), a 50 ms energy timeline on the web scale, and level meters.

/** Voice threshold on the web energy scale (matches web VOICE and core computeSpeechMetrics voiceThreshold). */
const val VOICE_ENERGY = 60

/** dBFS-free energy byte on the web scale: byte = sqrt(rms) * 255. */
fun energyByte(rms: Double): Int = min(255, (sqrt(max(0.0, rms)) * 255).roundToInt())

/** Level 0..1 on a -60..0 dBFS scale (meters and the VAD). */
fun levelOf(rms: Double): Double = ((20 * log10(max(rms, 1e-6)) + 60) / 60).coerceIn(0.0, 1.0)

fun rmsOf(pcm: ShortArray, n: Int): Double {
    if (n <= 0) return 0.0
    var sum = 0.0
    for (i in 0 until n) { val s = pcm[i] / 32768.0; sum += s * s }
    return sqrt(sum / n)
}

/** Syllable-like energy peaks in the last 10 s -> rough words/min (web estimateWpm). ponytail: a pacing hint, not a measurement. */
fun estimateWpm(energy: List<Int>): Int {
    val win = energy.takeLast(200)
    if (win.size < 40) return 0
    var peaks = 0
    var last = -10
    for (i in 1 until win.size - 1) {
        if (win[i] >= VOICE_ENERGY && win[i] > win[i - 1] && win[i] >= win[i + 1] && i - last >= 3) { peaks++; last = i }
    }
    return (peaks / 1.5 * (200.0 / win.size) * 6).roundToInt()
}

/** Mono PCM16 capture on its own thread, in ~40 ms chunks. Needs RECORD_AUDIO granted. [onPcm] runs on the capture thread. */
class PcmCapture(val rate: Int) {
    @Volatile var onPcm: ((ShortArray, Int) -> Unit)? = null
    @Volatile private var running = false
    /** The capture loop died (a read or [onPcm] threw, e.g. the encoder or muxer failed); the microphone is already released. */
    @Volatile var failed = false; private set
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val chunk = rate / 25
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, chunk * 8))
        } catch (e: Exception) { return false }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        try { rec.startRecording() } catch (e: Exception) { rec.release(); return false }
        running = true
        failed = false
        thread = Thread({
            val buf = ShortArray(chunk)
            // A raw thread: an exception here would crash the app and skip the release below.
            try {
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    // ERROR_DEAD_OBJECT etc.: report it so callers recover on [failed] instead of recording silence on a frozen clock.
                    if (n < 0) { failed = true; break }
                    if (n > 0) onPcm?.invoke(buf, n)
                }
            } catch (e: Exception) {
                failed = true
            } finally {
                running = false
                runCatching { rec.stop() }
                rec.release()
            }
        }, "mic-capture").apply { start() }
        return true
    }

    fun stop() {
        running = false
        val t = thread ?: return
        thread = null
        if (t !== Thread.currentThread()) runCatching { t.join(500) }
    }
}

/** PCM16 mono -> AAC-LC in an MPEG-4 container (.m4a, `audio/mp4`). Thread-safe; call [finish] once. */
class AacWriter(val file: File, private val rate: Int) {
    private val codec: MediaCodec
    private val muxer: MediaMuxer
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var muxing = false
    private var done = false

    /** PCM frames written so far. */
    var frames = 0L; private set

    init {
        file.parentFile?.mkdirs()
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        // A failed configure/start or muxer open releases the codec here: the caller only sees the exception, and retries must not leak codecs.
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        muxer = try {
            c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            c.start()
            MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Throwable) {
            runCatching { c.stop() }
            c.release()
            throw e
        }
        codec = c
    }

    @Synchronized fun write(pcm: ShortArray, n: Int) {
        if (done || n <= 0) return
        val bytes = ByteArray(n * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, 0, n)
        var pos = 0
        while (pos < bytes.size) {
            val i = codec.dequeueInputBuffer(10_000)
            if (i < 0) { drain(); continue }
            val buf = codec.getInputBuffer(i) ?: continue
            buf.clear()
            val len = min(buf.remaining(), bytes.size - pos) and 1.inv()
            buf.put(bytes, pos, len)
            codec.queueInputBuffer(i, 0, len, frames * 1_000_000L / rate, 0)
            frames += len / 2
            pos += len
            drain()
        }
    }

    /** Flush the encoder and close the file. False when nothing was written (the file is deleted). */
    @Synchronized fun finish(): Boolean {
        if (done) return false
        done = true
        var ok = false
        try {
            if (frames > 0) {
                var i = codec.dequeueInputBuffer(1_000_000)
                if (i >= 0) codec.queueInputBuffer(i, 0, 0, frames * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                var tries = 0
                while (!drain() && tries++ < 200) Thread.sleep(10)
                ok = muxing
            }
        } catch (e: Exception) {
            ok = false
        } finally {
            runCatching { codec.stop() }
            codec.release()
            runCatching { if (muxing) muxer.stop() }
            runCatching { muxer.release() }
        }
        if (!ok) file.delete()
        return ok
    }

    /** Move encoded output to the muxer. True once the end of stream was reached. */
    private fun drain(): Boolean {
        while (true) {
            val o = codec.dequeueOutputBuffer(info, 0)
            when {
                o == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start(); muxing = true }
                o >= 0 -> {
                    val out = codec.getOutputBuffer(o)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (out != null && info.size > 0 && muxing) {
                        out.position(info.offset); out.limit(info.offset + info.size)
                        muxer.writeSampleData(track, out, info)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                }
            }
        }
    }
}

/** 50 ms energy frames, level and pace for a stream of PCM chunks. Fed from the capture thread; read [energy] only after the stream ends. */
class Meter(private val rate: Int) {
    val energy = ArrayList<Int>()
    @Volatile var level = 0.0; private set
    @Volatile var liveWpm = 0; private set
    /** Frames above / just below the voice threshold (the mic check's "we can hear you"). */
    @Volatile var loud = 0; private set
    @Volatile var soft = 0; private set
    var samples = 0L; private set

    private val frame = max(1, rate / 20)
    private var acc = 0.0
    private var cnt = 0
    private var chunks = 0

    fun feed(pcm: ShortArray, n: Int) {
        var chunk = 0.0
        for (i in 0 until n) {
            val s = pcm[i] / 32768.0
            chunk += s * s; acc += s * s
            if (++cnt == frame) {
                val e = energyByte(sqrt(acc / cnt))
                energy.add(e)
                if (e > VOICE_ENERGY) loud++ else if (e > 30) soft++
                acc = 0.0; cnt = 0
            }
        }
        samples += n
        level = levelOf(sqrt(chunk / max(n, 1)))
        if (chunks++ % 5 == 0) liveWpm = estimateWpm(energy) // ~5 Hz is plenty for a pacing hint
    }
}

class RecordResult(val durationMs: Int, val energy: List<Int>)

/**
 * The practice recorder (iOS Recorder): 16 kHz mono AAC m4a to [start]'s file, plus meter values. With a null file it only meters (the mic check).
 * The meters are plain volatile fields written by the capture thread; the screen copies them on its own 10 Hz tick, so a chunk never invalidates
 * Compose. [stop] joins the capture thread and flushes the encoder (up to ~2 s): call it off the main thread when recording to a file.
 */
class MicRecorder {
    private val rate = 16_000
    private val capture = PcmCapture(rate)
    private var meter = Meter(rate)
    private var writer: AacWriter? = null
    private var onLevel: ((Double, Double) -> Unit)? = null
    /** While true the microphone stays open but nothing is written or measured, so the recording clock ([elapsed]) stands still (the examiner is talking). */
    @Volatile var paused = false
    /**
     * The app is in the background: Android hands it silence, so the microphone is released (the encoder stays open) and the clock stands
     * still until it returns. A microphone that will not reopen on return reads as [failed].
     */
    @Volatile var away = false
        set(v) {
            synchronized(this) {
                if (field == v) return
                field = v
                if (!isRecording) return
                if (v) capture.stop() else if (!capture.start()) reopenFailed = true
            }
        }
    @Volatile private var reopenFailed = false

    var isRecording by mutableStateOf(false); private set
    /** The capture or the encoder failed mid-recording: [stop] it (what was written is kept when the file closes cleanly). */
    val failed: Boolean get() = capture.failed || reopenFailed
    @Volatile var elapsed = 0.0; private set
    @Volatile var level = 0.0; private set
    @Volatile var silence = 0.0; private set
    @Volatile var liveWpm = 0; private set
    @Volatile var loud = 0; private set
    @Volatile var soft = 0; private set
    private val ring = DoubleArray(48)
    @Volatile private var head = 0
    /** The last 48 chunk levels, oldest first (a fresh list per call: read it at the screen's tick rate, not per chunk). */
    val levels: List<Double> get() = List(ring.size) { ring[(head + it) % ring.size] }

    /** Start metering (and recording into [file] when given). False when the microphone could not start. [dt] callback gets (level, seconds). */
    @Synchronized fun start(file: File? = null, startPaused: Boolean = false, onLevel: ((Double, Double) -> Unit)? = null): Boolean {
        stop()
        paused = startPaused
        meter = Meter(rate)
        writer = file?.let { runCatching { AacWriter(it, rate) }.getOrNull() ?: return false }
        this.onLevel = onLevel
        elapsed = 0.0; silence = 0.0; liveWpm = 0; loud = 0; soft = 0; level = 0.0
        ring.fill(0.0); head = 0
        reopenFailed = false
        capture.onPcm = { pcm, n ->
            if (!paused && !away) {
                writer?.write(pcm, n)
                meter.feed(pcm, n)
                val dt = n.toDouble() / rate
                val l = meter.level
                level = l
                elapsed = meter.samples.toDouble() / rate
                silence = if (l < 0.3) silence + dt else 0.0
                ring[head] = l; head = (head + 1) % ring.size
                liveWpm = meter.liveWpm
                loud = meter.loud; soft = meter.soft
                this.onLevel?.invoke(l, dt)
            }
        }
        // Started while away (a preparation minute ran out in the background): the microphone opens when the app returns.
        if (!away && !capture.start()) { writer?.finish(); writer = null; return false }
        isRecording = true
        return true
    }

    /** Stops; the duration and energy timeline (the file, if any, is closed and complete). Blocking, and synchronized so a background stop and a main-thread start never interleave. */
    @Synchronized fun stop(): RecordResult {
        if (!isRecording) return RecordResult(0, emptyList())
        capture.stop()
        capture.onPcm = null
        isRecording = false
        level = 0.0
        // An encoder that already failed can throw on flush: drop the unreadable file rather than upload it.
        writer?.let { w -> if (runCatching { w.finish() }.isFailure) w.file.delete() }
        writer = null
        return RecordResult((meter.samples * 1000 / rate).toInt(), meter.energy)
    }

    /** Screenshots: show a recording in progress without touching the microphone. */
    fun demo(elapsed: Double, wpm: Int = 0, loud: Int = 0, soft: Int = 0) {
        isRecording = true
        this.elapsed = elapsed
        liveWpm = wpm
        this.loud = loud; this.soft = soft
        level = 0.5
        for (i in ring.indices) ring[i] = 0.15 + 0.7 * kotlin.math.abs(kotlin.math.sin(i * 0.7))
        head = 0
    }
}
