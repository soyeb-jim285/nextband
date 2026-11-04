package com.soyeb.ieltspractice.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.soyeb.ieltspractice.feature.you.isWebUrl
import java.io.File
import java.io.IOException

/**
 * One 100 MB disk cache for every ExoPlayer in the app, so a Listening part, a practice replay or a result transcript clip is
 * downloaded once rather than on every play. SimpleCache allows a single instance per folder, hence the app-wide object.
 */
@OptIn(UnstableApi::class)
object MediaCache {
    @Volatile private var cache: SimpleCache? = null

    private fun cache(app: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(File(app.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(100L * 1024 * 1024), StandaloneDatabaseProvider(app))
            .also { cache = it }
    }

    /** For `ExoPlayer.Builder.setMediaSourceFactory`. */
    fun sourceFactory(context: Context): DefaultMediaSourceFactory {
        val app = context.applicationContext
        val data = CacheDataSource.Factory()
            .setCache(cache(app))
            // Every URL played here comes from a server answer: only https is fetched, never file://, content:// or asset://.
            .setUpstreamDataSourceFactory(ResolvingDataSource.Factory(DefaultDataSource.Factory(app)) { spec ->
                if (isWebUrl(spec.uri.toString())) spec else throw IOException("Refused to play a non-https address.")
            })
            .setCacheKeyFactory { spec -> spec.key ?: cacheKey(spec.uri.toString()) }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DefaultMediaSourceFactory(data)
    }

    /** Drops every cached clip (sign-out, account delete): the cache holds the learner's own recordings. Blocking: call off the main thread. */
    fun clear(context: Context) {
        val c = cache(context.applicationContext)
        c.keys.toList().forEach(c::removeResource)
    }
}

/** A presigned URL gets a fresh signature on every request: key it by the object (no query) so the next load still hits the cache. */
fun cacheKey(uri: String): String = if ("X-Amz-Signature=" in uri) uri.substringBefore('?') else uri
