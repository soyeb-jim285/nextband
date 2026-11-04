package com.soyeb.ieltspractice.feature.you

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** Only https pages with a host leave the app: the checkout URL comes from a server response. */
fun isWebUrl(url: String): Boolean =
    runCatching { java.net.URI(url) }.getOrNull()?.let { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrEmpty() } ?: false

/** Open a web page in a Custom Tab (falls back to any browser). Used for legal pages and checkout. Throws on a non-https URL. */
fun openCustomTab(context: Context, url: String) {
    require(isWebUrl(url)) { "Refused to open a non-https page." }
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
