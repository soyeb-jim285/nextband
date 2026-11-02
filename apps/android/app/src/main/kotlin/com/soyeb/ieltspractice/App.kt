package com.soyeb.ieltspractice

import android.app.Application

class App : Application() {
    /** Created on first use so a demo launch never builds the production graph. */
    val graph: AppGraph by lazy { AppGraph(this) }
}
