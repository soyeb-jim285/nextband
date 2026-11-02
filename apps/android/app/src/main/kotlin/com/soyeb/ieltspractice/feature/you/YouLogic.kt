package com.soyeb.ieltspractice.feature.you

import com.soyeb.ieltspractice.data.logic.Clocks
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val SITE = "https://nextband.app"

/** Band choices for the target: 4.0 to 9.0 in half bands. */
val targetBands: List<Double> = (8..18).map { it / 2.0 }

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.US)
private val longDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

fun formatDate(at: Instant, zone: ZoneId = Clocks.zone()): String = at.atZone(zone).format(longDate)

/** "12 Nov 2026" for a `yyyy-MM-dd` exam date; null when unset or unreadable. */
fun examDateText(examDate: String?): String? =
    examDate?.let { runCatching { LocalDate.parse(it).format(longDate) }.getOrNull() }

/** True while a paid plan still runs. A subscriber whose end has passed is back on the free plan (the server counts them as community). */
fun paidUntil(tier: String?, subscribedUntil: String?, now: Instant = Clocks.now()): Instant? {
    val until = subscribedUntil?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    return until.takeIf { (tier == "subscriber" || tier == "unlimited") && it.isAfter(now) }
}

/** The tag under your name: "Free", "Pro until 3 Mar", "Unlimited", "Guest". */
fun tierLabel(tier: String?, subscribedUntil: String?, now: Instant = Clocks.now(), zone: ZoneId = Clocks.zone()): String = when (tier) {
    "guest" -> "Guest"
    "unlimited" -> "Unlimited"
    "subscriber" -> paidUntil(tier, subscribedUntil, now)?.let { "Pro until ${it.atZone(zone).format(shortDate)}" } ?: "Free"
    else -> "Free"
}

/** Whole days left on a paid plan, rounded up; the plan is "ending" at 7 or fewer (web ENDING_DAYS). */
fun daysLeft(until: Instant, now: Instant = Clocks.now()): Int = Math.ceil(Duration.between(now, until).toMillis() / 86_400_000.0).toInt().coerceAtLeast(0)
const val ENDING_DAYS = 7

fun leftText(days: Int) = if (days <= 1) "Ends within a day" else "$days days left"

/** One line on where the plan stands (web planLine). */
fun planLine(tier: String?, subscribedUntil: String?, now: Instant = Clocks.now(), zone: ZoneId = Clocks.zone()): String = when (tier) {
    "unlimited" -> "You have unlimited access. There is nothing to buy."
    "guest" -> "You are using NextBand as a guest. Create a free account to keep your results."
    else -> paidUntil(tier, subscribedUntil, now)?.let {
        "Your paid plan runs until ${it.atZone(zone).format(longDate)} (${leftText(daysLeft(it, now)).lowercase()}). Buying again adds the time to the end."
    } ?: "You are on the free plan."
}

class PaidPlan(val key: String, val label: String, val days: Int, val bdt: Int)

/** Prices in BDT, one payment, no auto-renewal (packages/core/src/pricing.ts). */
val paidPlans = listOf(PaidPlan("month", "1 month", 30, 499), PaidPlan("quarter", "3 months", 90, 1199))

/** "৳1,199" */
fun bdt(n: Int): String = "৳" + if (n >= 1000) "${n / 1000},${String.format(Locale.US, "%03d", n % 1000)}" else "$n"

/** The plan's new end date: the days go on after the current end, or from now (matches the server, routes/pay.ts). */
fun newEnd(plan: PaidPlan, current: Instant?, now: Instant = Clocks.now()): Instant {
    val from = if (current != null && current.isAfter(now)) current else now
    return from.plus(Duration.ofDays(plan.days.toLong()))
}

private val PHONE = Regex("^(\\+?88)?01[3-9]\\d{8}$")

/** Spaces and dashes are allowed in what is typed; the server gets them stripped. */
fun cleanPhone(raw: String) = raw.replace(Regex("[\\s-]"), "")

fun phoneOk(raw: String) = PHONE.matches(cleanPhone(raw))

val freeFeatures = listOf("5 Writing tests a week", "10 recorded Speaking tests a month", "5 live examiner sessions a month", "Most Listening and Reading tests")
val paidFeatures = listOf("Unlimited Writing tests", "Unlimited recorded Speaking tests", "Live examiner: 5 a day, 40 a month", "Every Listening and Reading test")

/** The Help & legal pages (title to path on the site). */
val legalPages = listOf("Terms and conditions" to "/terms", "Privacy policy" to "/privacy", "Refund policy" to "/refunds", "Contact us" to "/contact", "About NextBand" to "/about")
