package com.soyeb.ieltspractice.feature.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.Clocks
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.NbTextField
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.nav.Nav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun PlanScreen(nav: Nav) {
    val graph = LocalGraph.current
    val api = graph.api
    val scope = rememberCoroutineScope()
    val me by api.me.collectAsState()
    val quota by api.quota.collectAsState()
    val hasAccount by api.hasAccount.collectAsState()
    var plan by remember { mutableStateOf(paidPlans.last()) }
    var checkout by remember { mutableStateOf(LocalDemo.current?.tab == "Checkout") }

    // Back from the payment page: the plan may have changed.
    LifecycleResumeEffect(hasAccount) {
        if (hasAccount) scope.launch { runCatching { api.refreshQuota(); api.loadMe() } }
        onPauseOrDispose { }
    }

    val q = quota ?: me?.quota()
    val tier = q?.tier ?: me?.tier
    val until = q?.subscribedUntil ?: me?.subscribedUntil
    val paid = paidUntil(tier, until)
    val unlimited = tier == "unlimited"
    val ending = paid != null && daysLeft(paid) <= ENDING_DAYS

    PushedScreen(
        "Plan", nav::back,
        bottomBar = {
            if (!unlimited) StickyActionBar {
                if (hasAccount) NbButton(
                    (if (paid != null) "Extend" else "Continue") + " · ${bdt(plan.bdt)}", { checkout = true },
                    fill = true, size = BtnSize.Large, icon = Icons.Rounded.Lock, haptic = true,
                ) else NbButton("Create a free account to upgrade", { nav.requireAccount("Create an account to upgrade.") { } }, fill = true, size = BtnSize.Large)
            }
        },
    ) { pad ->
        PushedList(pad) {
            item { StatusCard(tier, until) }
            if (ending && paid != null) item { Banner("Your plan ends soon. Buying again adds the time to the end, so nothing you paid for is lost.", Icons.Rounded.Info, tone = com.soyeb.ieltspractice.design.Tone.Warn) }
            if (!unlimited) item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Choose a plan")
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        paidPlans.forEach { p -> PlanOption(p, p.key == plan.key) { plan = p } }
                    }
                    Text("One payment, no auto-renewal. Prices in BDT.", style = NB.type.caption, color = NB.colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("What each plan includes")
                    NbCard(tone = CardTone.Hero) {
                        Text("Pro", style = NB.type.title2, color = NB.colors.ink)
                        Spacer(Modifier.height(8.dp))
                        Features(paidFeatures)
                    }
                    NbCard {
                        Text("Free", style = NB.type.title2, color = NB.colors.ink)
                        Spacer(Modifier.height(8.dp))
                        Features(freeFeatures)
                    }
                }
            }
        }
    }

    if (checkout) CheckoutSheet(
        plan = plan, email = me?.user?.email.orEmpty(), current = paid,
        onDismiss = { checkout = false },
        onPay = { phone -> api.checkout(plan.key, cleanPhone(phone), true) },
    )
}

@Composable
private fun StatusCard(tier: String?, until: String?) {
    NbCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your plan", style = NB.type.title2, color = NB.colors.ink, modifier = Modifier.weight(1f))
            Tag(tierLabel(tier, until), fg = NB.colors.brandText, bg = NB.colors.brandSoft)
        }
        Spacer(Modifier.height(6.dp))
        Text(planLine(tier, until), style = NB.type.bodySmall, color = NB.colors.muted)
    }
}

@Composable
private fun Features(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { f ->
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Check, null, Modifier.size(20.dp).padding(top = 2.dp), tint = NB.colors.brandText)
                Spacer(Modifier.width(10.dp))
                Text(f, style = NB.type.body, color = NB.colors.ink)
            }
        }
    }
}

@Composable
private fun PlanOption(p: PaidPlan, selected: Boolean, onSelect: () -> Unit) {
    val c = NB.colors
    val best = p.key == "quarter"
    val sub = if (best) "${bdt(Math.round(p.bdt / 3.0).toInt())} a month, save ${bdt(paidPlans.first().bdt * 3 - p.bdt)}" else "No auto-renewal"
    NbCard(
        Modifier.selectable(selected, role = Role.RadioButton, onClick = onSelect).let { if (selected) it.border(2.dp, c.brand, NB.shape.card) else it },
        tone = if (selected) CardTone.Hero else CardTone.Default,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).border(2.dp, if (selected) c.brand else c.faint, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Box(Modifier.size(12.dp).background(c.brand, CircleShape)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.label, style = NB.type.title3, color = c.ink)
                    if (best) Tag("Best value", fg = c.brandText, bg = c.surface)
                }
                Text(sub, style = NB.type.caption, color = c.muted)
            }
            Text(bdt(p.bdt), style = NB.type.title2.copy(fontFeatureSettings = "tnum"), color = c.ink)
        }
    }
}

/** The checkout form of the web: mobile number for the payment record, and the terms. Pay opens PayStation's page in a Custom Tab. */
@Composable
private fun CheckoutSheet(plan: PaidPlan, email: String, current: Instant?, onDismiss: () -> Unit, onPay: suspend (String) -> String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ok = phoneOk(phone)
    val end = formatDate(newEnd(plan, current, Clocks.now()))
    val c = NB.colors

    fun pay() {
        if (!ok || !agree || busy) return
        scope.launch {
            busy = true
            error = null
            try {
                val url = onPay(phone)
                openCustomTab(context, url)
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                error = e.message
            } catch (e: Exception) {
                error = "Couldn't open the payment page. Try again."
            }
            busy = false
        }
    }

    NbSheet(
        onDismiss, title = "Checkout", subtitle = "You pay on PayStation's secure page and come straight back.",
        actions = {
            NbButton("Pay ${bdt(plan.bdt)}", ::pay, fill = true, size = BtnSize.Large, icon = Icons.Rounded.Lock, enabled = ok && agree, loading = busy, haptic = true)
        },
    ) {
        NbCard(tone = CardTone.Inset) {
            SummaryRow("Account", email)
            SummaryRow("Plan", "Pro, ${plan.label} (${plan.days} days)")
            SummaryRow(if (current != null) "New end date" else "Runs until", end)
            SummaryRow("Total", "${bdt(plan.bdt)} BDT", strong = true)
        }
        Text("One payment, no auto-renewal. VAT included where it applies; no gateway fee is added.", style = NB.type.caption, color = c.muted)
        NbTextField(
            phone, { phone = it.take(20); error = null }, "Mobile number", placeholder = "01712345678",
            error = if (phone.isNotEmpty() && !ok) "Enter a Bangladeshi mobile number, like 01712345678" else error,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Text("PayStation needs it for the payment record.", style = NB.type.caption, color = c.muted)
        Row(
            Modifier.fillMaxWidth().toggleable(agree, role = Role.Checkbox) { agree = it }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(agree, null, colors = CheckboxDefaults.colors(checkedColor = c.brand, checkmarkColor = c.onBrand, uncheckedColor = c.faint))
            Spacer(Modifier.width(12.dp))
            Text("I have read and agree to the Terms & Conditions, the Return & Refund Policy and the Privacy Policy.", style = NB.type.bodySmall, color = c.ink)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NbButton("Terms", { openCustomTab(context, "$SITE/terms") }, kind = BtnKind.Text, size = BtnSize.Small)
            NbButton("Refunds", { openCustomTab(context, "$SITE/refunds") }, kind = BtnKind.Text, size = BtnSize.Small)
            NbButton("Privacy", { openCustomTab(context, "$SITE/privacy") }, kind = BtnKind.Text, size = BtnSize.Small)
        }
        if (error != null && ok) Banner(error.orEmpty(), Icons.Rounded.Lock, tone = com.soyeb.ieltspractice.design.Tone.Bad)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = NB.type.bodySmall, color = NB.colors.muted)
        Text(
            value, style = if (strong) NB.type.title3.copy(fontFeatureSettings = "tnum") else NB.type.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = NB.colors.ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
