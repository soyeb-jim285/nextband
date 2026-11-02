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
        setContent { NbApp(graph, demo) }
    }
}
