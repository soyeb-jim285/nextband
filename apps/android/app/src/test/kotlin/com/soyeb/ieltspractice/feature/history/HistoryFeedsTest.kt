package com.soyeb.ieltspractice.feature.history

import com.soyeb.ieltspractice.data.MemoryTokenStore
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.design.Skill
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Loaded History pages belong to one session: an expired account replaced by another must not see them. */
class HistoryFeedsTest {
    private val api = Api("https://t.test", MemoryTokenStore())

    @Test fun keptWithinSessionDroppedAcrossSessions() {
        val feeds = HistoryFeeds()
        val a = feeds.feed("tokenA", Skill.Writing) { Feed(api, Skill.Writing) }
        assertSame(a, feeds.feed("tokenA", Skill.Writing) { Feed(api, Skill.Writing) })
        assertNotSame(a, feeds.feed("tokenB", Skill.Writing) { Feed(api, Skill.Writing) })
    }
}
