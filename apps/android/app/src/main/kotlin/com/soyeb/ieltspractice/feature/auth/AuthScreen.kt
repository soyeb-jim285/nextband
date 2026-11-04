package com.soyeb.ieltspractice.feature.auth

import com.soyeb.ieltspractice.design.components.heroFill
import com.soyeb.ieltspractice.design.components.heroInk
import com.soyeb.ieltspractice.design.components.Logo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateContentSize
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.model.EmailStatus
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BandScale
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbTextField
import com.soyeb.ieltspractice.design.components.readable
import com.soyeb.ieltspractice.BuildConfig
import com.soyeb.ieltspractice.feature.you.siteUrl
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Onboarding
import com.soyeb.ieltspractice.nav.TodayTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Everything the five steps share, kept above the step animation so typed text survives moving between steps. */
@Stable
private class AuthForm(email: String) {
    var name by mutableStateOf("")
    var email by mutableStateOf(email)
    var password by mutableStateOf("")
    var confirm by mutableStateOf("")
    var code by mutableStateOf("")
    var showPassword by mutableStateOf(false)
    var busy by mutableStateOf(false)
    /** The primary button was pressed once: show what is wrong with each field. */
    var tried by mutableStateOf(false)
    var banner by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)
    /** Sign-up with an address that already has an account. */
    var exists by mutableStateOf(false)
    var codeIssue by mutableStateOf<String?>(null)
    /** Verify copy: just signed up (true) or signing in with an unverified address. */
    var fromSignUp by mutableStateOf(false)
    /** The address a code was sent to. */
    var address by mutableStateOf(email.trim())
    /** Proof of the request that sent the code, for the delivery status. */
    var token by mutableStateOf<String?>(null)
    /** Why that first send failed ([sendFailureKind]), shown until a resend works. */
    var sendError by mutableStateOf<String?>(null)

    companion object {
        /** Rotation / process death mid-flow keeps the step's context (who the code went to). Passwords are left out on purpose. */
        val Saver = listSaver<AuthForm, Any?>(
            save = { listOf(it.name, it.email, it.code, it.fromSignUp, it.address, it.token, it.sendError) },
            restore = {
                AuthForm(it[1] as String).apply {
                    name = it[0] as String; code = it[2] as String; fromSignUp = it[3] as Boolean
                    address = it[4] as String; token = it[5] as String?; sendError = it[6] as String?
                }
            },
        )
    }
}

/**
 * Sign in, create an account, verify the emailed code, forgot password and reset: one screen, five steps.
 * [reason] is why a guest was sent here (nav.requireAccount); finishing then continues that action. Otherwise the user lands on Today
 * (or Onboarding the first time). The primary button sits at the bottom, above the keyboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(nav: Nav, mode: String, reason: String?, email: String? = null) {
    val graph = LocalGraph.current
    val api = graph.api
    val prefs = graph.prefs
    val scope = rememberCoroutineScope()
    val c = NB.colors
    val reduced = LocalReducedMotion.current
    val demoTab = LocalDemo.current?.tab
    val demoMail = demoTab in listOf("Verify", "Forgot", "Reset")
    val demoStatus = if (demoTab == "Verify" || demoTab == "Reset") EmailStatus("sent", "2026-10-03T17:59:20Z", "m•••@example.com") else null
    var step by rememberSaveable {
        mutableStateOf(
            when (demoTab) { "SignIn" -> AuthStep.SignIn; "Signup" -> AuthStep.SignUp; "Verify" -> AuthStep.Verify; "Forgot" -> AuthStep.Forgot; "Reset" -> AuthStep.Reset; else -> stepOf(mode) },
        )
    }
    val f = rememberSaveable(saver = AuthForm.Saver) { AuthForm(if (demoMail) "maya@example.com" else email.orEmpty()) }

    fun go(s: AuthStep) {
        step = s
        f.banner = null; f.codeIssue = null; f.code = ""; f.tried = false; f.exists = false
    }
    fun close() { if (reason != null) nav.authFinished(false) else nav.back() }
    // The step Auth opened at closes too (Settings > Delete account > Forgot password: back returns to Settings, not a sign-in form).
    val closes = step.top || step == stepOf(mode)
    fun leave() { if (closes) close() else go(AuthStep.SignIn) }
    BackHandler { leave() }

    /** Signed in: continue what the guest was doing, or land on Today (Onboarding the first time). */
    fun finished() {
        // After an expired session, only the same account continues where it was; another account starts clean (Nav.reset).
        val sameAccount = api.expiredUser.let { it == null || it == api.me.value?.user?.id }
        if (reason != null && sameAccount) nav.authFinished(true)
        else scope.launch {
            val me = api.me.value ?: runCatching { api.loadMe() }.getOrNull()
            val needs = if (me == null) !prefs.onboardingDone.first() // profile didn't load: the device flag is the best guess
            else needsOnboarding(me.settings, prefs.getString(onboardedKey(me.user.id)) != null)
            nav.reset(if (needs) Onboarding else TodayTab)
        }
    }

    /** Run a code step. [codeOnly] puts every failure under the boxes (verify); otherwise only code problems go there. */
    // One request at a time: the IME Done action and the 6th-digit auto-verify bypass the disabled button. busy is set before
    // launch so a second call in the same frame sees it.
    fun attempt(codeOnly: Boolean, block: suspend () -> Unit) {
        if (f.busy) return
        f.busy = true
        scope.launch {
            f.banner = null; f.codeIssue = null
            try {
                block()
            } catch (x: CancellationException) {
                throw x
            } catch (x: ApiError) {
                val m = otpError(x.status, x.code, x.message)
                if (codeOnly || isCodeError(x.code)) f.codeIssue = m else f.banner = m
            } catch (x: Exception) {
                f.banner = "Something went wrong. Try again."
            }
            f.busy = false
        }
    }

    fun submit() {
        if (f.busy) return
        f.tried = true
        val signUp = step == AuthStep.SignUp
        if (!(if (signUp) signUpErrors(f.name, f.email, f.password, f.confirm) else signInErrors(f.email, f.password)).ok) return
        f.busy = true
        scope.launch {
            f.banner = null; f.info = null; f.exists = false
            val a = f.email.trim()
            try {
                if (signUp) {
                    if (api.signUp(f.name.trim(), a, f.password)) finished()
                    else { f.address = a; f.token = api.statusToken; f.fromSignUp = true; go(AuthStep.Verify) } // the server emailed a code
                } else {
                    api.signIn(a, f.password)
                    finished()
                }
            } catch (x: CancellationException) {
                throw x
            } catch (x: ApiError) {
                if (!signUp && x.code == "EMAIL_NOT_VERIFIED") { // other 403s show the server's message
                    f.sendError = null
                    f.token = try { api.sendOtp(a, "email-verification") } catch (e: CancellationException) { throw e } catch (e: Exception) { f.sendError = sendFailureKind(e); null }
                    f.address = a; f.fromSignUp = false; go(AuthStep.Verify)
                } else if (signUp) {
                    if (x.status == 422 || x.code == "USER_ALREADY_EXISTS" || x.message.contains("already exists", true)) f.exists = true
                    else f.banner = x.message.ifEmpty { "Could not create the account." }
                } else {
                    f.banner = if (x.status == 401) "That email and password don't match." else x.message.ifEmpty { "Could not sign in. Try again." }
                }
            } catch (x: Exception) {
                f.banner = if (signUp) "Could not create the account." else "Could not sign in. Try again."
            }
            f.busy = false
        }
    }

    fun verify() {
        f.tried = true
        if (codeError(f.code) == null) attempt(codeOnly = true) { api.verifyEmail(f.address, f.code); finished() }
    }

    fun sendReset() {
        f.tried = true
        if (emailError(f.email) == null) attempt(codeOnly = false) {
            val a = f.email.trim()
            f.token = api.sendOtp(a, "forget-password"); f.address = a
            go(AuthStep.Reset)
        }
    }

    fun updatePassword() {
        f.tried = true
        if (resetErrors(f.code, f.password, f.confirm).ok) attempt(codeOnly = false) {
            api.resetPassword(f.address, f.code, f.password)
            f.password = ""; f.confirm = ""
            go(AuthStep.SignIn)
            f.info = "Password updated. Sign in with your new password."
        }
    }

    val primary: () -> Unit = when (step) {
        AuthStep.SignIn, AuthStep.SignUp -> ::submit
        AuthStep.Verify -> ::verify
        AuthStep.Forgot -> ::sendReset
        AuthStep.Reset -> ::updatePassword
    }

    // Light: deep teal header with white type (7:1). Dark: a teal-tinted night surface, ink type and the bright teal for accents.
    val hero = heroFill()
    val onHero = heroInk().fg
    val onHeroMuted = heroInk().muted
    val keyboard = WindowInsets.isImeVisible
    // Landscape phone or very large text: the full hero would leave the form no room, so use the compact header.
    val short = LocalConfiguration.current.screenHeightDp < 480
    val compact = keyboard || short || LocalDensity.current.fontScale > 1.5f

    val actions: @Composable (Modifier) -> Unit = { m -> AuthActions(step, f, primary, keyboard, { go(it) }, m) }

    Box(Modifier.fillMaxSize().background(hero)) {
        BandScale(onHero, Modifier.align(Alignment.TopEnd).offset(x = 96.dp, y = (-40).dp).size(300.dp))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.statusBarsPadding().padding(start = 4.dp, end = NB.space.gutter, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                NbIconButton(if (closes) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, if (closes) "Close" else "Back to sign in", ::leave, tint = onHero)
                Spacer(Modifier.weight(1f))
                Logo(height = 24.dp, dark = true)
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (reduced) EnterTransition.None togetherWith ExitTransition.None
                    else fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(90))
                },
                modifier = Modifier.animateContentSize(tween(if (reduced) 0 else 220)),
                label = "authTitle",
            ) { s ->
                val (title, lede) = stepCopy(s, f)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = NB.space.gutter).padding(top = if (compact) 4.dp else 20.dp, bottom = if (compact) 20.dp else 36.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        title, style = if (compact) NB.type.title1 else NB.type.heroTitle,
                        color = onHero, modifier = Modifier.semantics { heading() },
                    )
                    if (!compact) Text(lede, style = NB.type.body, color = onHeroMuted, modifier = Modifier.widthIn(max = 420.dp))
                }
            }
            // The form rises over the header as a sheet; the primary action and the switch sit at its foot, in thumb reach.
            Column(Modifier.weight(1f).fillMaxWidth().clip(NB.shape.sheet).background(c.surface)) {
                // Short screens (landscape): the action scrolls with the form instead of pinning over the fields.
                Column(
                    Modifier.weight(1f).fillMaxWidth().then(if (short) Modifier.navigationBarsPadding().imePadding() else Modifier)
                        .verticalScroll(rememberScrollState()).readable(480.dp).padding(horizontal = NB.space.gutter).padding(top = 28.dp, bottom = 16.dp),
                ) {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            if (reduced) EnterTransition.None togetherWith ExitTransition.None
                            else (fadeIn(tween(220, delayMillis = 90)) + slideInHorizontally(tween(260)) { it / 10 }) togetherWith fadeOut(tween(90))
                        },
                        label = "authStep",
                    ) { s ->
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            f.banner?.let { Banner(it, Icons.Rounded.ErrorOutline, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, tone = Tone.Bad) }
                            if (s == AuthStep.SignIn) f.info?.let { Banner(it, Icons.Rounded.CheckCircle, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, tone = Tone.Good) }
                            when (s) {
                                AuthStep.SignIn, AuthStep.SignUp -> CredentialsStep(s == AuthStep.SignUp, f, reason, primary) { go(it) }
                                AuthStep.Verify -> VerifyStep(f, demoStatus, ::verify) { go(it) }
                                AuthStep.Forgot -> ForgotStep(f, primary) { go(AuthStep.SignIn) }
                                AuthStep.Reset -> ResetStep(f, demoStatus, primary) { go(it) }
                            }
                        }
                    }
                    if (short) Spacer(Modifier.height(16.dp))
                    if (short) actions(Modifier)
                }
                if (!short) actions(Modifier.navigationBarsPadding().imePadding().readable(480.dp).padding(horizontal = NB.space.gutter))
            }
        }
    }
}

@Composable
private fun AuthActions(
    step: AuthStep, f: AuthForm, primary: () -> Unit, keyboard: Boolean, go: (AuthStep) -> Unit, modifier: Modifier,
) {
    val c = NB.colors
    Column(
        modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NbButton(
            when (step) { AuthStep.SignIn -> "Sign in"; AuthStep.SignUp -> "Create account"; AuthStep.Verify -> "Verify email"; AuthStep.Forgot -> "Send code"; AuthStep.Reset -> "Update password" },
            primary, fill = true, size = BtnSize.Large, loading = f.busy, haptic = true,
        )
        if ((step == AuthStep.SignIn || step == AuthStep.SignUp) && !keyboard) {
            val signUp = step == AuthStep.SignUp
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (signUp) "Already have an account?" else "New to NextBand?", style = NB.type.bodySmall, color = c.muted)
                NbButton(
                    if (signUp) "Sign in" else "Create an account",
                    { go(if (signUp) AuthStep.SignIn else AuthStep.SignUp); f.info = null; f.confirm = "" },
                    kind = BtnKind.Text, size = BtnSize.Small,
                )
            }
        }
    }
}

private fun stepCopy(s: AuthStep, f: AuthForm): Pair<String, String> = when (s) {
    AuthStep.SignIn -> "Welcome back" to "Sign in to continue your practice."
    AuthStep.SignUp -> "Create your account" to "Keep your results, watch your bands and unlock your coach."
    AuthStep.Verify -> if (f.fromSignUp) "Check your email" to "One step left: verify your address." else "Verify your email" to "One step left before you can sign in."
    AuthStep.Forgot -> "Reset your password" to "Enter your account email and we'll send you a 6-digit code."
    AuthStep.Reset -> "Enter your code" to "If an account exists for ${f.address}, we sent it a 6-digit code. It expires in 10 minutes."
}

@Composable
private fun CredentialsStep(signUp: Boolean, f: AuthForm, reason: String?, onDone: () -> Unit, go: (AuthStep) -> Unit) {
    val errs = if (!f.tried) FieldErrors() else if (signUp) signUpErrors(f.name, f.email, f.password, f.confirm) else signInErrors(f.email, f.password)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (reason != null) Banner(reason, Icons.Rounded.Info)
        if (signUp) AuthField(f.name, { f.name = it }, "Name", errs.name, keyboard = KeyboardType.Text, capitalize = true, autofill = ContentType.PersonFullName)
        Column {
            AuthField(
                f.email, { f.email = it; f.exists = false }, "Email",
                errs.email ?: if (f.exists) "An account with this email already exists." else null,
                keyboard = KeyboardType.Email, autofill = ContentType.EmailAddress,
            )
            if (f.exists && errs.email == null) NbButton("Sign in instead", { go(AuthStep.SignIn); f.confirm = "" }, kind = BtnKind.Text, size = BtnSize.Small)
        }
        if (signUp) PasswordPair(f, "Password", errs, onDone)
        else Column {
            AuthField(
                f.password, { f.password = it }, "Password", errs.password, keyboard = KeyboardType.Password, ime = ImeAction.Done, onImeAction = onDone,
                password = true, show = f.showPassword, onToggleShow = { f.showPassword = !f.showPassword }, autofill = ContentType.Password,
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                NbButton("Forgot password?", { go(AuthStep.Forgot); f.info = null }, kind = BtnKind.Text, size = BtnSize.Small)
            }
        }
        if (signUp) TermsNote()
    }
}

@Composable
private fun PasswordPair(f: AuthForm, label: String, errs: FieldErrors, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AuthField(
                f.password, { f.password = it }, label, errs.password, keyboard = KeyboardType.Password,
                password = true, show = f.showPassword, onToggleShow = { f.showPassword = !f.showPassword }, autofill = ContentType.NewPassword,
            )
            if (errs.password == null) Text(
                if (f.password.length >= 8) "8 characters or more" else "At least 8 characters.",
                style = NB.type.caption, color = if (f.password.length >= 8) NB.colors.goodText else NB.colors.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        AuthField(
            f.confirm, { f.confirm = it }, "Confirm password", errs.confirm ?: liveMismatch(f.password, f.confirm), keyboard = KeyboardType.Password,
            ime = ImeAction.Done, onImeAction = onDone, password = true, show = f.showPassword, autofill = ContentType.NewPassword,
        )
    }
}

@Composable
private fun VerifyStep(f: AuthForm, demo: EmailStatus?, onComplete: () -> Unit, go: (AuthStep) -> Unit) {
    val api = LocalGraph.current.api
    val errs = if (f.tried) codeError(f.code) else null
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            buildAnnotatedString {
                append("Enter the 6-digit code for ")
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = NB.colors.ink)); append(f.address); pop()
                append(". It expires in 10 minutes.")
            },
            style = NB.type.body, color = NB.colors.muted,
        )
        CodeField(f.code, errs ?: f.codeIssue) { f.code = it; f.codeIssue = null; if (it.length == 6) onComplete() }
        Column {
            CodeDelivery(f.token, demo, f.sendError) { api.sendOtp(f.address, "email-verification") }
            // A typo in the address: back to the sign-up form with name and email still filled in.
            if (f.fromSignUp) NbButton("Use a different email", { go(AuthStep.SignUp) }, kind = BtnKind.Text, size = BtnSize.Small)
        }
        // The server answers a sign-up for a taken address like a new one (no account enumeration) and emails a note instead of a code.
        if (f.fromSignUp) Column {
            Text(
                "Already have an account with this email? We emailed you a note instead of a code. Sign in, or reset your password.",
                style = NB.type.bodySmall, color = NB.colors.muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                NbButton("Sign in", { go(AuthStep.SignIn); f.confirm = "" }, kind = BtnKind.Text, size = BtnSize.Small)
                NbButton("Forgot password?", { f.email = f.address; go(AuthStep.Forgot) }, kind = BtnKind.Text, size = BtnSize.Small)
            }
        }
    }
}

@Composable
private fun ForgotStep(f: AuthForm, onDone: () -> Unit, back: () -> Unit) {
    val err = if (f.tried) emailError(f.email) else null
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AuthField(f.email, { f.email = it }, "Email", err, keyboard = KeyboardType.Email, ime = ImeAction.Done, onImeAction = onDone, autofill = ContentType.EmailAddress)
        NbButton("Back to sign in", back, kind = BtnKind.Text, size = BtnSize.Small)
    }
}

@Composable
private fun ResetStep(f: AuthForm, demo: EmailStatus?, onDone: () -> Unit, go: (AuthStep) -> Unit) {
    val api = LocalGraph.current.api
    val errs = if (f.tried) resetErrors(f.code, f.password, f.confirm) else FieldErrors()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CodeField(f.code, errs.code ?: f.codeIssue) { f.code = it; f.codeIssue = null }
        PasswordPair(f, "New password", errs, onDone)
        Column {
            CodeDelivery(f.token, demo) { api.sendOtp(f.address, "forget-password") }
            NbButton("Use a different email", { go(AuthStep.Forgot) }, kind = BtnKind.Text, size = BtnSize.Small)
        }
        NbButton("Back to sign in", { go(AuthStep.SignIn) }, kind = BtnKind.Text, size = BtnSize.Small)
    }
}

/** A labelled field with show/hide for passwords, IME actions and autofill hints. */
@Composable
private fun AuthField(
    value: String, onChange: (String) -> Unit, label: String, error: String?,
    keyboard: KeyboardType, ime: ImeAction = ImeAction.Next, onImeAction: () -> Unit = {},
    password: Boolean = false, show: Boolean = false, onToggleShow: (() -> Unit)? = null,
    autofill: ContentType? = null, capitalize: Boolean = false,
) {
    val focus = LocalFocusManager.current
    NbTextField(
        value, onChange, label,
        fieldModifier = Modifier.semantics { if (autofill != null) contentType = autofill },
        error = error,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard, imeAction = ime,
            capitalization = if (capitalize) androidx.compose.ui.text.input.KeyboardCapitalization.Words else androidx.compose.ui.text.input.KeyboardCapitalization.None,
        ),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { onImeAction() }),
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        trailing = if (password && onToggleShow != null) ({
            NbIconButton(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password", onToggleShow)
        }) else null,
    )
}

@Composable
private fun TermsNote() {
    val link = TextLinkStyles(SpanStyle(color = NB.colors.ink, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline))
    Text(
        buildAnnotatedString {
            append("By creating an account you agree to the ")
            withLink(LinkAnnotation.Url(siteUrl("/terms", BuildConfig.WEB_CHECKOUT), link)) { append("terms") }
            append(" and ")
            withLink(LinkAnnotation.Url(siteUrl("/privacy", BuildConfig.WEB_CHECKOUT), link)) { append("privacy policy") }
            append(".")
        },
        Modifier.fillMaxWidth(), style = NB.type.caption, color = NB.colors.muted, textAlign = TextAlign.Center,
    )
}

/**
 * Six-digit code as six boxes. One invisible BasicTextField holds the value, so the keyboard, paste, Backspace and the
 * one-time-code autofill all work; the boxes only draw it. Digits only.
 */
@Composable
private fun CodeField(code: String, error: String?, onChange: (String) -> Unit) {
    val c = NB.colors
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicTextField(
            value = code, onValueChange = { onChange(otpDigits(it)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = NB.type.body.copy(color = Color.Transparent),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }
                .semantics { contentType = ContentType.SmsOtpCode; contentDescription = "6-digit code" },
            decorationBox = { inner ->
                Box {
                    Box(Modifier.size(1.dp)) { inner() }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(6) { i ->
                            val active = focused && i == minOf(code.length, 5)
                            Box(
                                Modifier.weight(1f).heightIn(min = 56.dp).background(c.surface, NB.shape.tile)
                                    .border(if (active || error != null) 2.dp else 1.dp, if (error != null) c.bad else if (active) c.brand else c.faint, NB.shape.tile),
                                contentAlignment = Alignment.Center,
                            ) { Text(code.getOrNull(i)?.toString() ?: "", style = NB.type.title1.copy(fontFeatureSettings = "tnum"), color = c.ink) }
                        }
                    }
                }
            },
        )
        if (error != null) Text(error, style = NB.type.bodySmall, color = c.badText, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/**
 * Delivery status plus "Resend code" with a countdown. [first] is the status token of the request that sent the first code (null when that call failed);
 * the status is polled every 2 s for 20 s after each send. [demo] (screenshots) is shown as is, with no network.
 */
@Composable
private fun CodeDelivery(first: String?, demo: EmailStatus?, firstError: String? = null, send: suspend () -> String?) {
    val api = LocalGraph.current.api
    val c = NB.colors
    var token by remember { mutableStateOf(first) }
    var status by remember { mutableStateOf(demo) }
    var wait by remember { mutableIntStateOf(demo?.resendAvailableIn ?: 0) }
    // Why the latest send failed (an EmailStatus.error kind), null while none has.
    var sendFailed by remember { mutableStateOf(if (first == null && demo == null) firstError ?: "network" else null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(token) {
        val t = token ?: return@LaunchedEffect
        if (demo != null) return@LaunchedEffect
        repeat(10) {
            runCatching { api.emailStatus(t) }.getOrNull()?.let { status = it; wait = it.resendAvailableIn }
            delay(2000)
        }
    }
    LaunchedEffect(wait) { if (wait > 0) { delay(1000); wait-- } }
    val shown = sendFailed?.let { EmailStatus("failed", error = it) } ?: status?.copy(resendAvailableIn = wait)
    val (line, bad) = emailStatusLine(shown, System.currentTimeMillis())
    Column {
        Text(line, style = NB.type.bodySmall, color = if (bad) c.badText else c.muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        NbButton(
            if (wait > 0) "Resend code in ${wait}s" else "Resend code",
            {
                scope.launch {
                    sendFailed = null
                    try {
                        val t = send()
                        if (t == null) sendFailed = "network" else { status = null; token = t }
                    } catch (x: CancellationException) { throw x } catch (x: Exception) { sendFailed = sendFailureKind(x) }
                }
            },
            kind = BtnKind.Text, size = BtnSize.Small, enabled = wait == 0,
        )
    }
}
