package com.soyeb.ieltspractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.soyeb.ieltspractice.data.demo.DemoConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val demo = DemoConfig.from(intent) // `--ez demo true --es screen <name> --es theme dark` (nav/ScreenCatalog.kt)
        val graph = if (demo != null) AppGraph(this, demo) else (application as App).graph
        this.graph = graph
        setContent { NbApp(graph, demo) }
    }

    private var graph: AppGraph? = null

    // Notifications switched on or off in Android's settings while we were away: the server's device row follows (Push.sync).
    // A tapped push arrives through PushOpenActivity (not exported), never as extras on this exported activity.
    override fun onStart() {
        super.onStart()
        graph?.push?.resync()
    }

    // The TTS engine binds a system service on first use; let it go when the app leaves the screen. The next speak() starts it again.
    override fun onStop() {
        super.onStop()
        graph?.tts?.shutdown()
    }
}
