package com.soyeb.ieltspractice.feature

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.runtime.Composable
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.TabScreen
import com.soyeb.ieltspractice.nav.Nav

// ponytail: temporary placeholders so the shell compiles before each feature lands; delete when the last graph is real.
@Composable
fun StubTab(title: String) = TabScreen(title) {
    item { EmptyState(Icons.Rounded.Construction, title, "Being rebuilt.") }
}

@Composable
fun StubPushed(title: String, nav: Nav) = PushedScreen(title, nav::back) { _ ->
    EmptyState(Icons.Rounded.Construction, title, "Being rebuilt.")
}
