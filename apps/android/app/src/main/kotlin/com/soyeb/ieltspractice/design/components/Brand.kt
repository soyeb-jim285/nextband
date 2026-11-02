package com.soyeb.ieltspractice.design.components

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.R
import com.soyeb.ieltspractice.design.NB

/** The NextBand wordmark. Its colours come from values(-night)/colors.xml, resolved for the app's theme (Settings can differ from the device). */
@Composable
fun Logo(modifier: Modifier = Modifier, height: Dp = 26.dp, dark: Boolean = NB.colors.isDark) {
    val context = LocalContext.current
    val vector = remember(context, dark) {
        val c = Configuration(context.resources.configuration)
        c.uiMode = (c.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        ImageVector.vectorResource(null, context.createConfigurationContext(c).resources, R.drawable.logo_wordmark)
    }
    Image(vector, "NextBand", modifier.height(height))
}
