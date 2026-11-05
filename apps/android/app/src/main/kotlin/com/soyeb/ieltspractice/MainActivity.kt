package com.soyeb.ieltspractice

import android.content.Intent
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
        if (savedInstanceState == null) graph.push.handle(intent) // opened by tapping a push
        setContent { NbApp(graph, demo) }
    }

    private var graph: AppGraph? = null

    // A push tapped while the app is open: the notification's intent comes here (CLEAR_TOP | SINGLE_TOP).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        graph?.push?.handle(intent)
    }

    // The TTS engine binds a system service on first use; let it go when the app leaves the screen. The next speak() starts it again.
    override fun onStop() {
        super.onStop()
        graph?.tts?.shutdown()
    }
}
