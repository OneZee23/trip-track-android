package com.onezee.triptrack.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.onezee.triptrack.designsystem.components.ActionPopover
import com.onezee.triptrack.designsystem.components.ActionPopoverItem
import com.onezee.triptrack.designsystem.components.ActionPopoverList
import com.onezee.triptrack.designsystem.components.AppConfirmDialog
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.CardPressResponse
import com.onezee.triptrack.designsystem.components.CustomNavBar
import com.onezee.triptrack.designsystem.components.GlassPill
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.NavCircleIcon
import com.onezee.triptrack.designsystem.components.ProBadge
import com.onezee.triptrack.designsystem.components.SettingsOptionPicker
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.ToastItem
import com.onezee.triptrack.designsystem.components.ToastType
import com.onezee.triptrack.designsystem.components.ToastView
import com.onezee.triptrack.designsystem.components.cardPressFeedback
import com.onezee.triptrack.designsystem.components.edgeScrims
import com.onezee.triptrack.designsystem.components.glassBackground
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.ThemeMode
import com.onezee.triptrack.designsystem.theme.appStrings
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every house component in light and dark, recorded with Roborazzi and verified on every test
 * run. The phone is 360 × 640 dp at 2×: small files, and the same layout the iPhone canon uses.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class ComponentScreenshotTests(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterComponentActivity()).around(compose)

    private fun capture(name: String, settleMillis: Long = 0, content: @Composable () -> Unit) {
        compose.setContent { Specimen(dark, content) }
        // Idle is not enough for effects that wait on a delay; move virtual time past them.
        if (settleMillis > 0) compose.mainClock.advanceTimeBy(settleMillis)
        compose.onNodeWithTag(SHOT).captureRoboImage(shotPath(name, dark))
    }

    private fun captureScreen(name: String, content: @Composable () -> Unit) {
        compose.setContent { Themed(dark) { content() } }
        compose.waitForIdle()
        captureScreenRoboImage(shotPath(name, dark))
    }

    @Test fun typography() = capture("typography") {
        val c = AppTheme.colors
        Column(Modifier.width(328.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Лента", style = AppType.title, letterSpacing = AppType.titleTracking, color = c.text)
            Text("1 697 км", style = AppType.display(), color = c.text)
            Text("Тбилиси", style = AppType.sheetTitle, color = c.text)
            Text("Краснодарский край", style = AppType.headerTitle, color = c.text)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("4.2", style = AppType.statValue, letterSpacing = AppType.statValueTracking, color = c.text)
                Text(" км", style = AppType.statUnit, color = c.textSecondary)
            }
            Text("ДИСТАНЦИЯ", style = AppType.statCaption, letterSpacing = AppType.statCaptionTracking, color = c.textTertiary)
            Text("История", style = AppType.section, letterSpacing = AppType.sectionTracking, color = c.text)
            Row(Modifier.fillMaxWidth()) {
                Text("Краснодар → Джубга", style = AppType.itemTitle, color = c.text, modifier = Modifier.weight(1f))
                Text("142", style = AppType.itemValue, color = c.text)
            }
            Text("миль новых дорог", style = AppType.unit, color = c.textSecondary)
            Text("Каждая дорога, которую вы проехали, открывает туман на вашей карте.", style = AppType.body, color = c.text)
            Text("Вчера · 2 ч 14 мин", style = AppType.meta, color = c.textSecondary)
            Text("Обновлено сегодня", style = AppType.caption, color = c.textTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Сохранить", style = AppType.button, color = AppTheme.accent)
                Text("Готово", style = AppType.action, color = AppTheme.accent)
                Text("Назад", style = AppType.navTitle, color = c.text)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Частый гость", style = AppType.chip, color = c.textSecondary)
                Text("Сначала новые", style = AppType.chipActive, color = c.text)
            }
        }
    }

    @Test fun palette() = capture("palette") {
        val c = AppTheme.colors
        val a = AtlasTheme.colors
        val swatches = listOf(
            AppTheme.accent, AppTheme.green, AppTheme.blue, AppTheme.red, AppTheme.yellow, AppTheme.purple,
            AppTheme.teal, AppTheme.gold, c.goldBg, AppTheme.spritePlate, c.distance, c.roadHeroAccent,
            c.bg, c.card, c.cardAlt, c.surface, c.surfaceElevated, c.roadHeroCard, c.text, c.textSecondary,
            c.textTertiary, a.accent, a.navSurface, a.navInactive, a.fog, a.chip, a.separator, a.mutedPin,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            swatches.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { colour ->
                        Box(Modifier.size(40.dp).background(colour, RoundedCornerShape(8.dp)).background(Color.Transparent))
                    }
                }
            }
        }
    }

    @Test fun surfaces() = capture("surfaces") {
        val c = AppTheme.colors
        Column(Modifier.width(328.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().surfaceCard().padding(horizontal = 13.dp, vertical = 12.dp)) {
                Text("Краснодар → Джубга", style = AppType.itemTitle, color = c.text)
                Text("Вчера · 142 км", style = AppType.meta, color = c.textSecondary)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Brush.linearGradient(listOf(AppTheme.teal, AppTheme.accent, AppTheme.yellow)), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.glassBackground().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Идёт запись", style = AppType.itemTitle, color = c.text)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPill("Все", isActive = true)
                GlassPill("Неделя")
                GlassPill("Месяц")
            }
        }
    }

    @Test fun cardPress() = capture("card_press", settleMillis = 800) {
        val c = AppTheme.colors
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(null, CardPressResponse.tap, CardPressResponse.hold).forEach { response ->
                val source = remember { MutableInteractionSource() }
                if (response != null) {
                    // After a frame: the feedback starts listening in the first one.
                    LaunchedEffect(Unit) {
                        delay(32)
                        source.emit(PressInteraction.Press(Offset.Zero))
                    }
                }
                Box(
                    Modifier
                        .size(96.dp)
                        .cardPressFeedback(source, response ?: CardPressResponse.tap)
                        .surfaceCard(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(response?.name ?: "rest", style = AppType.meta, color = c.textSecondary)
                }
            }
        }
    }

    @Test fun dialog() = captureScreen("dialog") {
        val strings = appStrings
        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
            Text("Лента", style = AppType.title, color = AppTheme.colors.text, modifier = Modifier.padding(16.dp))
        }
        AppConfirmDialog(
            title = "Удалить поездку?",
            message = "Поездка исчезнет с этого телефона и с сервера. Вернуть её будет нельзя.",
            actions = listOf(
                AppDialogAction(strings.delete(), AppDialogAction.Kind.destructive),
                AppDialogAction("Скрыть из ленты", AppDialogAction.Kind.plain),
            ),
            onDismiss = {},
        )
    }

    @Test fun popoverList() = capture("popover_list") {
        Box(Modifier.surfaceCard()) {
            ActionPopoverList(demoItems(), onItemClick = {})
        }
    }

    @Test fun popover() = captureScreen("popover") {
        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 16.dp)) {
                NavCircleIcon(AppGlyph.ellipsis)
                ActionPopover(expanded = true, onDismissRequest = {}, items = demoItems())
            }
        }
    }

    @Test fun themePicker() = captureScreen("picker_theme") {
        val strings = appStrings
        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg))
        SettingsOptionPicker(
            title = strings.theme(),
            options = listOf(ThemeMode.system, ThemeMode.light, ThemeMode.dark),
            selection = ThemeMode.light,
            footnote = strings.themePickerFootnote(),
            badgeGlyph = {
                when (it) {
                    ThemeMode.system -> AppGlyph.gearshapeFill
                    ThemeMode.light -> AppGlyph.sunMaxFill
                    ThemeMode.dark -> AppGlyph.moonFill
                }
            },
            label = {
                when (it) {
                    ThemeMode.system -> strings.themeSystem()
                    ThemeMode.light -> strings.light()
                    ThemeMode.dark -> strings.dark()
                }
            },
            onSelect = {},
            onDismissRequest = {},
        )
    }

    @Test fun languagePicker() = captureScreen("picker_language") {
        val strings = appStrings
        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg))
        SettingsOptionPicker(
            title = strings.lang(),
            options = AppLanguage.displayOrder,
            selection = AppLanguage.ru,
            footnote = strings.languagePickerFootnote(),
            badge = { it.badge },
            label = { it.endonym },
            onSelect = {},
            onDismissRequest = {},
        )
    }

    @Test fun toast() = capture("toast") {
        val strings = appStrings
        Column(Modifier.width(328.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ToastView(ToastItem(ToastType.success, "Поездка сохранена"), onDismiss = {})
            ToastView(ToastItem(ToastType.error, "Не удалось загрузить фото попутчика, попробуйте позже"), onDismiss = {})
            ToastView(ToastItem(ToastType.info, "Ссылка скопирована"), onDismiss = {})
            ToastView(ToastItem(ToastType.undo, "Поездка удалена", undoLabel = strings.undo(), undoAction = {}), onDismiss = {})
        }
    }

    @Test fun navCircle() = capture("nav_circle") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NavCircleIcon(AppGlyph.chevronLeft)
            NavCircleIcon(AppGlyph.ellipsis)
            NavCircleIcon(AppGlyph.squareAndArrowUp)
        }
    }

    // iOS: CustomNavBar.swift. The bar of a pushed screen, the display title with an owner's name under
    // it, and the bar in a sheet.
    @Test fun customNavBar() = capture("custom_nav_bar") {
        Column(Modifier.width(360.dp)) {
            CustomNavBar("Полторашка", onBack = {}) { NavCircleIcon(AppGlyph.ellipsis) }
            CustomNavBar("Статистика", onBack = {}, subtitle = "Александр", largeTitle = true)
            CustomNavBar("Автозапись", onBack = {}, inSheet = true)
        }
    }

    @Test fun proBadge() = capture("pro_badge") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Путник", style = AppType.sheetTitle, color = AppTheme.colors.text)
            ProBadge()
            ProBadge(height = 14.dp)
        }
    }

    @Test fun edgeScrims() = capture("edge_scrims") {
        Box(
            Modifier
                .size(width = 328.dp, height = 140.dp)
                .background(Brush.verticalGradient(listOf(AppTheme.blue, AppTheme.green, AppTheme.yellow)))
                .edgeScrims(top = true, bottom = true),
        )
    }

    @Test fun glyphs() = capture("glyphs") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppGlyph.entries.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { Glyph(it, contentDescription = null, tint = AppTheme.colors.text, size = 36.dp) }
                }
            }
        }
    }

    private fun demoItems() = listOf(
        ActionPopoverItem("Редактировать", AppGlyph.pencil) {},
        ActionPopoverItem("Поделиться", AppGlyph.squareAndArrowUp) {},
        ActionPopoverItem("Пожаловаться", AppGlyph.flag, isDestructive = true) {},
        ActionPopoverItem("Удалить", AppGlyph.trash, isDestructive = true) {},
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
