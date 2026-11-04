package com.soyeb.ieltspractice.media

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * The device's text-to-speech voice ("Hear the word" in Review and the pronunciation list; the examiner line in the recorded test when a prompt has no audio).
 * The engine starts on first use; a [speak] before it is ready is said as soon as it is. Call [shutdown] when the owner goes away.
 * ponytail: not the test speaker's voice; play the clip from the test audio if that matters.
 */
class Tts(context: Context) {
    private val app = context.applicationContext
    private val ready = CompletableDeferred<Boolean>()
    @Volatile private var engine: TextToSpeech? = null
    @Volatile private var ok = false
    @Volatile private var pending: Pair<String, String>? = null

    private fun start(): TextToSpeech = engine ?: synchronized(this) {
        engine ?: TextToSpeech(app) { status ->
            ok = status == TextToSpeech.SUCCESS
            ready.complete(ok)
            pending?.let { (text, accent) -> engine?.let { say(it, text, accent) } }
            pending = null
        }.also { engine = it }
    }

    /** The engine started and failed: no voice on this device, [speak] says nothing. */
    val unavailable: Boolean get() = ready.isCompleted && !ok

    /** Says [text] now, cutting off any word still being spoken. [accent]: "uk" (default) or "us". A little slow (0.85). */
    fun speak(text: String, accent: String = "uk") {
        val t = start()
        if (ready.isCompleted) { if (ok) say(t, text, accent) } else pending = text to accent
    }

    private fun say(t: TextToSpeech, text: String, accent: String) {
        t.setLanguage(if (accent == "us") Locale.US else Locale.UK)
        t.setSpeechRate(0.85f)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts")
    }

    /** Reads [text] and returns when it ends (or after a timeout proportional to its length). Returns at once when the engine can't start. */
    suspend fun speakAndAwait(text: String) {
        val t = start()
        if (withTimeoutOrNull(2_000) { ready.await() } != true) return
        val done = CompletableDeferred<Unit>()
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { done.complete(Unit) }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { done.complete(Unit) }
            override fun onError(id: String?, code: Int) { done.complete(Unit) }
        })
        t.language = if (t.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE) Locale.UK else Locale.ENGLISH
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "examiner")
        try { withTimeoutOrNull(2_000L + text.length * 90L) { done.await() } } finally { t.stop() }
    }

    fun stop() { engine?.stop() }

    fun shutdown() {
        engine?.let { runCatching { it.shutdown() } }
        engine = null
        pending = null
    }
}
