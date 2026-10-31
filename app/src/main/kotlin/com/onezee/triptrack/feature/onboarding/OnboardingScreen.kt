// iOS: OnboardingView.swift
package com.onezee.triptrack.feature.onboarding

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.pressStart2P
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.account.AccountGlyph
import com.onezee.triptrack.feature.account.AccountIcon
import com.onezee.triptrack.feature.account.LegalLinks
import com.onezee.triptrack.feature.account.Spinner
import com.onezee.triptrack.feature.account.linkedText
import com.onezee.triptrack.location.permissions.PermissionLedger
import com.onezee.triptrack.location.permissions.PermissionPlan
import com.onezee.triptrack.location.permissions.RecordingPermission
import com.onezee.triptrack.logic.distanceParts
import com.onezee.triptrack.logic.metresPerSecond
import com.onezee.triptrack.logic.speedParts
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.location.R as LocationWords

/**
 * iOS: OnboardingView. Five pages: the product, an example trip, location, background recording and
 * notifications. Navigation is explicit (no swipe past an explanation), every Continue reaches the
 * system's question, and a refusal still moves the flow forward. Sign-in is not a page: as on iOS
 * the app asks for it only where an account is needed (the profile card, a guest action).
 */
@Composable
fun OnboardingScreen(
    ledger: PermissionLedger,
    distanceUnit: DistanceUnit,
    resumeTick: Int,
    onFinished: () -> Unit,
    startPage: Int = 0,
) {
    val context = LocalContext.current
    val coordinator = remember { OnboardingPermissionCoordinator(ledger, Build.VERSION.SDK_INT) { grantedPermissions(context) } }
    var page by rememberSaveable { mutableIntStateOf(startPage) }
    // Re-read on every page and every return from the system's question or its settings page.
    var answers by remember { mutableIntStateOf(0) }
    val granted = remember(page, resumeTick, answers) { grantedPermissions(context) }
    val notificationsOn = remember(page, resumeTick, answers) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    var pendingToken by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        answers++
        coordinator.answered(pendingToken)
    }
    coordinator.ask = { permission, token ->
        pendingToken = token
        launcher.launch(OnboardingPermissionCoordinator.manifestNames(permission))
    }

    OnboardingContent(
        page = page,
        distanceUnit = distanceUnit,
        isRequesting = coordinator.isRequesting,
        locationGranted = RecordingPermission.fineLocation in granted,
        backgroundGranted = RecordingPermission.backgroundLocation in granted,
        notificationsGranted = notificationsOn,
        onContinue = { current ->
            when (current) {
                0 -> page = 1
                1 -> page = 2
                2 -> coordinator.proceed(OnboardingPermissionCoordinator.Step.location) { page = 3 }
                3 -> coordinator.proceed(OnboardingPermissionCoordinator.Step.background) { page = 4 }
                else -> coordinator.proceed(OnboardingPermissionCoordinator.Step.notifications, onFinished)
            }
        },
    )
}

private fun grantedPermissions(context: Context): Set<RecordingPermission> =
    PermissionPlan.effectiveGrants(Build.VERSION.SDK_INT) { context.checkSelfPermission(it.manifestName) == PackageManager.PERMISSION_GRANTED }

/** The pages without the system questions, for screenshots: [onContinue] gets the page it came from. */
@Composable
fun OnboardingContent(
    page: Int,
    distanceUnit: DistanceUnit,
    isRequesting: Boolean,
    locationGranted: Boolean,
    backgroundGranted: Boolean,
    notificationsGranted: Boolean,
    onContinue: (Int) -> Unit,
) {
    val colors = AppTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bg).testTag("onboarding")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            when (page) {
                0 -> WelcomePage { onContinue(0) }
                1 -> ValuePage(distanceUnit) { onContinue(1) }
                2 -> PermissionPage(2, R.drawable.onboard_fog, appStrings.onboardingLocation(), appStrings.onboardingLocationSub(), locationGranted, isRequesting,
                    spacing = 12.dp, consentTop = 4.dp, "onboarding_continue_location") { onContinue(2) }
                3 -> PermissionPage(3, R.drawable.onboard_track, appStrings.onboardingBackgroundTitle(), backgroundBody(), backgroundGranted, isRequesting,
                    spacing = 10.dp, consentTop = 6.dp, "onboarding_continue_background") { onContinue(3) }
                else -> PermissionPage(4, R.drawable.onboard_notify, appStrings.onboardingNotificationsTitle(), appStrings.onboardingNotificationsSub(),
                    notificationsGranted, isRequesting, spacing = 10.dp, consentTop = 6.dp, "onboarding_continue_notifications") { onContinue(4) }
            }
        }
        PaginationDots(page, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(bottom = 12.dp))
    }
}

/**
 * From Android 11 «all the time» lives on the app's location settings page, which the system opens
 * instead of a dialog: the page says so before the person lands there.
 */
@Composable
private fun backgroundBody(): String {
    val body = appStrings.onboardingBackgroundSub()
    return if (Build.VERSION.SDK_INT >= 30) body + "\n\n" + LocalContext.current.getString(LocationWords.string.android_permission_background_hint) else body
}

/** Inactive 7 pt grey dots, the active one an 18 × 7 accent capsule. */
@Composable
private fun PaginationDots(page: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(5) { index ->
            val width by animateDpAsState(if (index == page) 18.dp else 7.dp, tween(250), label = "dot")
            Box(Modifier.size(width, 7.dp).background(if (index == page) AppTheme.accent else Color(0x669B9BA5), CircleShape))
        }
    }
}

/**
 * The illustration at the top of a page, sized as artwork: the scenes are drawn on brand navy, and
 * at icon size a dark square reads as something unstyled. Filled and cropped to a 200 pt band;
 * every scene keeps its subject in the middle.
 */
@Composable
private fun OnboardingHero(drawable: Int) {
    Image(
        bitmap = ImageBitmap.imageResource(drawable),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // Pixel art: neighbours, never a blur.
        filterQuality = FilterQuality.None,
        modifier = Modifier
            .padding(top = 56.dp)
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(26.dp)),
    )
}

@Composable
private fun PageTitle(text: String, tag: String, top: Dp = 36.dp) {
    Text(
        text,
        style = inter(24f, InterWeight.heavy),
        color = AppTheme.colors.text,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = top).semantics { heading() }.testTag(tag),
    )
}

@Composable
private fun BodyText(text: String, top: Dp) {
    Text(
        text,
        style = inter(15f),
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = top),
    )
}

/** The primary CTA: 56 tall, radius 14, accent with a faint accent shadow. */
@Composable
private fun PrimaryButton(title: String, tag: String, isRequesting: Boolean = false, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .plainPressFeedback(interaction)
            .iosShadow(shape, AppTheme.accent.copy(alpha = 0.3f), radius = 3.dp, y = 1.dp)
            .background(AppTheme.accent, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = !isRequesting, role = Role.Button) {
                haptics.tap()
                onClick()
            }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = inter(18f, InterWeight.bold), color = Color.White)
        if (isRequesting) Spinner(Color.White, 18.dp, Modifier.align(Alignment.CenterEnd).padding(end = 20.dp))
    }
}

@Composable
private fun ColumnScope.WelcomePage(onContinue: () -> Unit) {
    val strings = appStrings
    OnboardingHero(R.drawable.onboard_road)
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 47.dp).semantics(mergeDescendants = true) { heading() }.testTag("onboarding_page_0"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(strings.onboardingWelcomeTitle1(), style = inter(24f, InterWeight.heavy), color = AppTheme.colors.text, textAlign = TextAlign.Center)
        Text(strings.onboardingWelcomeTitle2(), style = inter(24f, InterWeight.heavy), color = AppTheme.accent, textAlign = TextAlign.Center)
    }
    BodyText(strings.onboardingWelcomeSub(), top = 16.dp)
    // A trust builder before any permission question.
    Row(
        Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 28.dp)
            .background(AppTheme.green.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 13.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountIcon(AccountGlyph.globe, AppTheme.green, size = 15.dp)
        Text(strings.onboardingPrivacyPill(), style = inter(12f, InterWeight.bold), color = AppTheme.green)
    }
    Spacer(Modifier.weight(1f))
    Box(Modifier.padding(horizontal = 32.dp).padding(bottom = 56.dp)) {
        PrimaryButton(strings.onboardingContinue(), "onboarding_continue_welcome", onClick = onContinue)
    }
}

/**
 * «What do I get?» in two seconds: an example card, pinned Continue 56 pt above the bottom like
 * the other pages. A shorter screen scrolls the story above the button.
 */
@Composable
private fun ColumnScope.ValuePage(distanceUnit: DistanceUnit, onContinue: () -> Unit) {
    val strings = appStrings
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        OnboardingHero(R.drawable.onboard_memory)
        PageTitle(strings.onboardingValueTitle(), "onboarding_page_1")
        Box(Modifier.padding(horizontal = 28.dp).padding(top = 20.dp, bottom = 24.dp)) { MockTripCard(distanceUnit) }
    }
    Box(Modifier.padding(horizontal = 32.dp).padding(top = 8.dp, bottom = 56.dp)) {
        PrimaryButton(strings.onboardingContinue(), "onboarding_continue_value", onClick = onContinue)
    }
}

@Composable
private fun ColumnScope.PermissionPage(
    index: Int,
    hero: Int,
    title: String,
    body: String,
    alreadyGranted: Boolean,
    isRequesting: Boolean,
    spacing: Dp,
    consentTop: Dp,
    tag: String,
    onContinue: () -> Unit,
) {
    val strings = appStrings
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        OnboardingHero(hero)
        PageTitle(title, "onboarding_page_$index")
        BodyText(body, top = 12.dp)
    }
    Column(
        Modifier.padding(horizontal = 32.dp).padding(top = 12.dp, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (alreadyGranted) Text(strings.onboardingAlreadyGranted(), style = inter(13f, InterWeight.semibold), color = AppTheme.green)
        PrimaryButton(strings.onboardingContinue(), tag, isRequesting, onContinue)
        // Acceptance stands before the FIRST system question, on this page too: it names both
        // documents with links and an explicit verb, a deliberate change from the shorter canon.
        ConsentText(strings, Modifier.padding(top = consentTop))
    }
}

@Composable
private fun ConsentText(strings: AppStrings, modifier: Modifier = Modifier) {
    val language = strings.language
    Text(
        linkedText(strings.onboardingConsentMarkdown(LegalLinks.terms(language), LegalLinks.privacyPolicy(language)), AppTheme.accent),
        style = inter(11f),
        color = AppTheme.colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * A «screenshot» of a recorded trip on the app's light map style: theme-independent on purpose.
 * The numbers are the canonical 246 km and 82 km/h in SI, through `Measure`, so a miles phone never
 * meets «km» on its first screen.
 */
@Composable
private fun MockTripCard(distanceUnit: DistanceUnit) {
    val strings = appStrings
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().iosShadow(shape, Color.Black.copy(alpha = 0.12f), radius = 24.dp, y = 8.dp).clip(shape).background(Color.White)) {
        MockMap(strings)
        val lang = strings.language.code
        val distance = Measure.distanceParts(246_000.0, distanceUnit, lang, strings)
        val average = Measure.speedParts(DistanceUnit.km.metresPerSecond(82.0), distanceUnit, lang, strings)
        Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.onboardingMockTripTitle(), style = inter(13.5f, InterWeight.bold), color = Color(0xFF1E1E23),
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Metric(distance.value, distance.unit, AppTheme.green, strings.distance())
                Box(Modifier.size(1.dp, 30.dp).background(Color.Black.copy(alpha = 0.05f)))
                Metric("2:59", "", AppTheme.accent, strings.duration())
                Box(Modifier.size(1.dp, 30.dp).background(Color.Black.copy(alpha = 0.05f)))
                Metric(average.value, average.unit, AppTheme.blue, strings.onboardingStatAvg())
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Metric(value: String, unit: String, color: Color, label: String) {
    Column(Modifier.weight(1f).padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = inter(17f, InterWeight.heavy, monospacedDigits = true), color = color, maxLines = 1)
            if (unit.isNotEmpty()) Text(unit, style = inter(9f, InterWeight.semibold), color = Color(0xFF64646E), maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(label.uppercase(appStrings.language.locale), style = inter(8f, InterWeight.bold), letterSpacing = points(0.32f), color = Color(0xFF9B9BA5), maxLines = 1)
    }
}

/** Parchment, faint roads, a speed-gradient route from the start (red dot) to the finish (green). */
@Composable
private fun MockMap(strings: AppStrings) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFFE8E1D3))) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val roads = Path().apply {
                moveTo(0f, height * 0.35f)
                cubicTo(width * 0.35f, height * 0.15f, width * 0.6f, height * 0.75f, width, height * 0.55f)
                moveTo(width * 0.25f, 0f)
                cubicTo(width * 0.3f, height * 0.4f, width * 0.42f, height * 0.6f, width * 0.45f, height)
            }
            drawPath(roads, Color(0xFFD6CDBA), style = Stroke(3.dp.toPx()))
            val route = Path().apply {
                moveTo(width * 0.11f, height * 0.83f)
                cubicTo(width * 0.38f, height * 0.95f, width * 0.45f, height * 0.18f, width * 0.72f, height * 0.11f)
            }
            drawPath(route, Brush.linearGradient(listOf(AppTheme.green, AppTheme.yellow, AppTheme.red), Offset(0f, height), Offset(width, 0f)),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            for ((point, color) in listOf(Offset(width * 0.11f, height * 0.83f) to AppTheme.red, Offset(width * 0.72f, height * 0.11f) to AppTheme.green)) {
                drawCircle(Color.White, 6.5.dp.toPx(), point)
                drawCircle(color, 4.5.dp.toPx(), point)
            }
        }
        Image(
                bitmap = ImageBitmap.imageResource(R.drawable.pixel_car),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
            modifier = Modifier.padding(start = w * 0.5f - 23.5.dp, top = 150.dp * 0.3f - 23.5.dp).size(47.dp).rotate(-24f),
        )
        // «Example»: this is nobody's trip.
        Text(
            strings.proDemoExample(),
            style = inter(12.5f, InterWeight.semibold),
            color = Color.White,
            modifier = Modifier
                .padding(10.dp)
                .background(Color(0xB828282A), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        // The brand mark, not copy: the same letters in every language, as on iOS.
        Text(
            "TRIP TRACK",
            style = pressStart2P(7f),
            letterSpacing = points(0.56f),
            color = Color(0x8C9B9BA5),
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
        )
    }
}
