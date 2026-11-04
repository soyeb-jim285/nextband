package com.soyeb.ieltspractice.feature

import com.soyeb.ieltspractice.feature.you.bdt
import com.soyeb.ieltspractice.feature.you.cleanPhone
import com.soyeb.ieltspractice.feature.you.daysLeft
import com.soyeb.ieltspractice.feature.you.examDateText
import com.soyeb.ieltspractice.feature.you.isPaymentUrl
import com.soyeb.ieltspractice.feature.you.isWebUrl
import com.soyeb.ieltspractice.feature.you.legalPages
import com.soyeb.ieltspractice.feature.you.newEnd
import com.soyeb.ieltspractice.feature.you.paidPlans
import com.soyeb.ieltspractice.feature.you.phoneOk
import com.soyeb.ieltspractice.feature.you.planLine
import com.soyeb.ieltspractice.feature.you.siteUrl
import com.soyeb.ieltspractice.feature.you.targetBands
import com.soyeb.ieltspractice.feature.you.tierLabel
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouLogicTest {
    private val now = Instant.parse("2026-10-03T18:00:00Z")
    private val utc = ZoneId.of("UTC")

    @Test fun targetBandsAreHalfStepsFourToNine() {
        assertEquals(11, targetBands.size)
        assertEquals(4.0, targetBands.first())
        assertEquals(9.0, targetBands.last())
        assertTrue(targetBands.contains(6.5))
    }

    @Test fun tierLabelNamesThePlan() {
        assertEquals("Guest", tierLabel("guest", null, now, utc))
        assertEquals("Free", tierLabel("community", null, now, utc))
        assertEquals("Pro until 3 Mar", tierLabel("subscriber", "2027-03-03T10:00:00Z", now, utc))
        assertEquals("Free", tierLabel("subscriber", "2026-09-01T10:00:00Z", now, utc)) // ended
        assertEquals("Unlimited", tierLabel("unlimited", null, now, utc))
    }

    @Test fun planLineMatchesTheWeb() {
        assertEquals("You are on the free plan.", planLine("community", null, now, utc))
        assertTrue(planLine("subscriber", "2026-10-06T10:00:00Z", now, utc).contains("3 days left"))
    }

    @Test fun planLineHasNoBuyingHintWhenTheBuildCannotSell() {
        assertTrue(planLine("subscriber", "2026-10-06T10:00:00Z", now, utc).contains("Buying again"))
        assertFalse(planLine("subscriber", "2026-10-06T10:00:00Z", now, utc, canBuy = false).contains("Buying"))
    }

    @Test fun daysLeftRoundsUp() {
        assertEquals(3, daysLeft(Instant.parse("2026-10-06T10:00:00Z"), now))
        assertEquals(0, daysLeft(Instant.parse("2026-10-01T10:00:00Z"), now))
    }

    @Test fun bangladeshiMobileNumbers() {
        assertTrue(phoneOk("01712345678"))
        assertTrue(phoneOk("+880 1712-345678"))
        assertTrue(phoneOk("8801312345678"))
        assertFalse(phoneOk("01212345678"))
        assertFalse(phoneOk("0171234567"))
        assertEquals("01712345678", cleanPhone("017 1234-5678"))
    }

    @Test fun pricesAndNewEndDate() {
        assertEquals("৳499", bdt(499))
        assertEquals("৳1,199", bdt(1199))
        val month = paidPlans.first()
        assertEquals(Instant.parse("2026-11-02T18:00:00Z"), newEnd(month, null, now))
        assertEquals(Instant.parse("2026-12-05T10:00:00Z"), newEnd(month, Instant.parse("2026-11-05T10:00:00Z"), now))
    }

    @Test fun examDateText() {
        assertEquals("27 Oct 2026", examDateText("2026-10-27"))
        assertNull(examDateText(null))
        assertNull(examDateText("soon"))
    }

    @Test fun onlyHttpsPagesOpen() {
        assertTrue(isWebUrl("https://pay.paystation.com.bd/checkout/abc?x=1"))
        assertTrue(isWebUrl("HTTPS://nextband.app/terms"))
        assertFalse(isWebUrl("http://nextband.app/terms"))
        assertFalse(isWebUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isWebUrl("javascript:alert(1)"))
        assertFalse(isWebUrl("https:///nohost"))
        assertFalse(isWebUrl("not a url"))
    }

    @Test fun checkoutOpensOnlyPayStation() {
        assertTrue(isPaymentUrl("https://api.paystation.com.bd/checkout/abc"))
        assertTrue(isPaymentUrl("https://sandbox.paystation.com.bd/checkout/abc"))
        assertFalse(isPaymentUrl("https://paystation.com.bd.evil.example/checkout"))
        assertFalse(isPaymentUrl("https://evilpaystation.com.bd/checkout"))
        assertFalse(isPaymentUrl("http://api.paystation.com.bd/checkout"))
    }

    @Test fun theStoreBuildOpensSitePagesMarkedAndWithoutTheRefundPolicy() {
        assertEquals("https://nextband.app/terms?app=play", siteUrl("/terms", canBuy = false))
        assertEquals("https://nextband.app/terms", siteUrl("/terms", canBuy = true))
        assertFalse(legalPages(canBuy = false).any { it.second == "/refunds" })
        assertTrue(legalPages(canBuy = true).any { it.second == "/refunds" })
    }
}
