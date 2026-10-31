// iOS: AppleSignInButton.swift, SignInPromptSheet.swift, ProfileView.guestSignInCard, AppConfig.swift
// (termsURL, privacyPolicyURL)
package com.onezee.triptrack.feature.account

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.BuildConfig
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.SheetScope
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// iOS: AppConfig.termsURL / privacyPolicyURL. The documents the person accepts by continuing.
object LegalLinks {
    private const val BASE = "https://onezee23.github.io/trip-track-ios"
    fun terms(language: AppLanguage): String = BASE + if (language == AppLanguage.ru) "/terms-ru.html" else "/terms.html"
    fun privacyPolicy(language: AppLanguage): String =
        BASE + if (language == AppLanguage.ru) "/privacy-policy-ru.html" else "/privacy-policy.html"
}

/** The words of a sign-in outcome, or null when it deserves none (success, a closed sheet). */
fun signInMessage(outcome: SignInOutcome, context: Context): String? = when (outcome) {
    is SignInOutcome.SignedIn, SignInOutcome.Cancelled, SignInOutcome.Busy -> null
    // A developer note in debug builds; a person in a release build gets the ordinary failure.
    SignInOutcome.NotConfigured -> context.getString(if (BuildConfig.DEBUG) R.string.android_sign_in_not_configured else R.string.android_sign_in_google_failed)
    is SignInOutcome.Failed -> context.getString(R.string.android_sign_in_google_failed)
}

/**
 * Starts a sign-in on the service's own scope: closing the sheet that started it must not cancel a
 * login already on its way (iOS: «dismissal doesn't cancel the request»). [onResult] runs on the
 * main thread.
 */
fun AuthService.launchSignIn(context: Context, flow: GoogleSignInFlow, scope: CoroutineScope, onResult: (SignInOutcome) -> Unit) {
    scope.launch { onResult(signIn(context, flow)) }
}

/**
 * iOS: AppleSignInButton, as «Sign in with Google». White fill and dark ink in both themes with our
 * own hairline, the Google «G», the title from the app's strings in the app's language. While the
 * server round-trip runs it turns into the loading button: the mark, a spinner and «Signing in…».
 */
@Composable
fun GoogleSignInButton(
    isAuthenticating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    height: Dp = 50.dp,
) {
    val strings = appStrings
    val shape = RoundedCornerShape(cornerRadius)
    val dark = AppTheme.isDark
    if (isAuthenticating) {
        val ink = if (dark) Color.Black else Color.White
        val fill = if (dark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f)
        Row(
            modifier.fillMaxWidth().height(height).background(fill, shape).testTag("google_sign_in_loading"),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoogleMark(17.dp)
            Spinner(ink, size = 16.dp)
            Text(strings.signInLoading(), style = inter(16f, InterWeight.semibold), color = ink, maxLines = 1)
        }
        return
    }
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val stroke = if (dark) Color.White.copy(alpha = 0.14f) else Color(0xFFDBDBE0)
    val title = stringResource(R.string.android_sign_in_with_google)
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .plainPressFeedback(interaction)
            .background(Color.White, shape)
            .border(1.2.dp, stroke, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.tap()
                onClick()
            }
            .semantics { contentDescription = title }
            .testTag("google_sign_in_button"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoogleMark(18.dp)
        Text(title, style = inter(17f, InterWeight.semibold), color = Color.Black, maxLines = 1)
    }
}

@Composable
private fun GoogleMark(size: Dp) {
    Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(size))
}

/** iOS: SignInPromptSheet.Action. A headline that says why the guest is asked converts better. */
enum class SignInAction {
    react, comment, follow, share, sync, publish, companions, generic;

    fun headline(strings: AppStrings): String = when (this) {
        react -> strings.signInPromptReact()
        comment -> strings.signInPromptComment()
        follow -> strings.signInPromptFollow()
        share -> strings.signInPromptShare()
        sync -> strings.signInPromptSync()
        publish -> strings.signInPromptPublish()
        companions -> strings.signInPromptCompanions()
        generic -> strings.signInPromptGeneric()
    }
}

/**
 * iOS: SignInPromptSheet. Shown when a guest tries something that needs an account. The grabber and
 * the «×» are the way out; there is no «maybe later». [onAuthenticated] runs once, on a successful
 * sign-in, right before the sheet closes, so the presenter can resume what the guest tapped.
 */
@Composable
fun SignInPromptSheet(
    action: SignInAction,
    auth: AuthService,
    onDismissRequest: () -> Unit,
    onAuthenticated: () -> Unit = {},
    authScope: CoroutineScope? = null,
) {
    val state by auth.state.collectAsState()
    SignInPromptContent(
        action = action,
        isAuthenticating = state.isAuthenticating,
        onDismissRequest = onDismissRequest,
        onSignIn = { context, done -> auth.launchSignIn(context, GoogleSignInFlow.button, authScope ?: auth.serviceScope, done) },
        onAuthenticated = onAuthenticated,
    )
}

/** The sheet without the service: the sign-in is a callback. */
@Composable
fun SignInPromptContent(
    action: SignInAction,
    isAuthenticating: Boolean,
    onDismissRequest: () -> Unit,
    onSignIn: (Context, (SignInOutcome) -> Unit) -> Unit,
    onAuthenticated: () -> Unit = {},
) {
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = AppTheme.colors.bg) {
        SignInPromptBody(action, isAuthenticating, onSignIn, onAuthenticated)
    }
}

/** The sheet's column, for screenshots outside the dialog window. */
@Composable
fun SheetScope.SignInPromptBody(
    action: SignInAction,
    isAuthenticating: Boolean,
    onSignIn: (Context, (SignInOutcome) -> Unit) -> Unit,
    onAuthenticated: () -> Unit = {},
    initialError: String? = null,
) {
    val colors = AppTheme.colors
    val strings = appStrings
    val context = LocalContext.current
    var error by remember { mutableStateOf(initialError) }
    val authenticated by rememberUpdatedState(onAuthenticated)
    run {
        val sheet = this
        SheetGrabber()
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 44.dp), horizontalArrangement = Arrangement.End) {
            val interaction = remember { MutableInteractionSource() }
            // Dimmed while signing in, never disabled: a slow login must not trap the person.
            Box(
                Modifier
                    .size(28.dp)
                    .alpha(if (isAuthenticating) 0.4f else 1f)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { sheet.dismiss() }
                    .semantics { contentDescription = strings.close() }
                    .testTag("sign_in_close"),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(AppGlyph.xmark, contentDescription = null, tint = colors.textSecondary, size = 20.dp)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SignInIdleRing(Modifier.padding(top = 40.dp))
            Text(
                action.headline(strings),
                style = inter(21f, InterWeight.bold),
                letterSpacing = points(-0.21f),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp).testTag("sign_in_headline"),
            )
            Text(
                strings.signInPromptSubtitle(),
                style = inter(14f).copy(lineHeight = inter(14f).fontSize * 1.42f),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            GoogleSignInButton(
                isAuthenticating = isAuthenticating,
                onClick = {
                    error = null
                    onSignIn(context) { outcome ->
                        if (outcome is SignInOutcome.SignedIn) {
                            authenticated()
                            sheet.dismiss()
                        } else error = signInMessage(outcome, context)
                    }
                },
                modifier = Modifier.padding(top = 22.dp),
            )
            Box(Modifier.padding(top = 12.dp)) {
                val shown = error
                if (shown != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("sign_in_error")) {
                        AccountIcon(AccountGlyph.warning, AppTheme.red, size = 16.dp)
                        Text(shown, style = inter(13f, InterWeight.bold), color = AppTheme.red, textAlign = TextAlign.Center)
                    }
                } else {
                    Text(
                        linkedText(strings.signInLegalMarkdown(LegalLinks.terms(strings.language)), if (isAuthenticating) colors.textTertiary else AppTheme.accent),
                        style = inter(11f),
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * iOS: SignInIdleRing. A pale outer ring and an accent inner ring around the pixel car, which stands
 * on the sprite plate every vehicle in the app stands on.
 */
@Composable
fun SignInIdleRing(modifier: Modifier = Modifier) {
    Box(modifier.size(100.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(98.dp).border(2.dp, AppTheme.accent.copy(alpha = 0.25f), CircleShape))
        Box(
            Modifier
                .size(82.dp)
                .background(Brush.verticalGradient(listOf(AppTheme.spritePlateTop, AppTheme.spritePlateBottom)), CircleShape)
                .border(2.5.dp, AppTheme.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.pixel_car),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(width = 56.dp, height = 34.dp),
            )
        }
    }
}

/**
 * iOS: ProfileView.guestSignInCard. The guest pitch on «Я»; a soft expiry wears the same card with
 * other words: the person did nothing wrong and lost nothing, so it does not pitch Cloud Sync again
 * and drops «you can do it later». After a soft expiry the button tries the returning account first,
 * one tap back into the same session.
 */
@Composable
fun GuestSignInCard(
    expired: Boolean,
    isAuthenticating: Boolean,
    onSignIn: (Context, GoogleSignInFlow, (SignInOutcome) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    initialError: String? = null,
) {
    val colors = AppTheme.colors
    val strings = appStrings
    val context = LocalContext.current
    var error by remember { mutableStateOf(initialError) }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.cardAlt, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("profile_guest_signin"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(30.dp).background(AppTheme.accentBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                AccountIcon(if (expired) AccountGlyph.personExclamation else AccountGlyph.cloudFill, AppTheme.accent, size = 19.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.syncCardKicker(), style = inter(11f, InterWeight.bold), letterSpacing = points(0.22f), color = colors.textTertiary)
                Text(if (expired) strings.sessionExpiredTitle() else strings.syncCardTitle(), style = inter(15f, InterWeight.bold), color = colors.text)
                Text(if (expired) strings.sessionExpiredBody() else strings.syncCardBody(), style = inter(12.5f), color = colors.textSecondary)
            }
        }
        GoogleSignInButton(
            isAuthenticating = isAuthenticating,
            cornerRadius = 999.dp,
            height = 43.dp,
            onClick = {
                error = null
                onSignIn(context, if (expired) GoogleSignInFlow.returning else GoogleSignInFlow.button) { outcome ->
                    error = signInMessage(outcome, context)
                }
            },
        )
        val shown = error
        if (shown != null) {
            Text(shown, style = inter(12f, InterWeight.semibold), color = AppTheme.red, textAlign = TextAlign.Center, modifier = Modifier.testTag("guest_sign_in_error"))
        } else if (!expired) {
            Text(strings.syncCardLater(), style = inter(11f), color = colors.textTertiary)
        }
    }
}
