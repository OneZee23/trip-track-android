// Android-only. The signed-in redemption flow follows the backend promo contract deployed 6 Nov 2026.
package com.onezee.triptrack.feature.settings

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.account.AccountEntry
import com.onezee.triptrack.feature.me.PlusEntitlement
import com.onezee.triptrack.feature.me.PlusEntitlementStore
import com.onezee.triptrack.feature.settings.MeSheetHeader
import com.onezee.triptrack.network.ApiError
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PromoCodeSheet(graph: AppGraph, signedIn: Boolean, onDismiss: () -> Unit) {
    val strings = appStrings
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Int?>(null) }
    var successUntil by remember { mutableStateOf<Instant?>(null) }
    var sessionExpired by remember { mutableStateOf(false) }
    fun redeem() {
        if (busy) return
        if (code.trim().isEmpty()) { problem = com.onezee.triptrack.designsystem.R.string.promo_validation; return }
        busy = true
        problem = null
        scope.launch {
            try {
                val result = graph.plusApi.redeem(code)
                graph.plusEntitlement.afterRedemption(result.plus)
                successUntil = result.plus.until ?: result.redemption.endsAt
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: ApiError.TooManyRequests) { problem = com.onezee.triptrack.designsystem.R.string.promo_too_many }
            catch (error: ApiError.UnknownServer) {
                if (error.code == "PROMO_CODE_ALREADY_REDEEMED") {
                    val current = refreshAfterAlreadyRedeemed(graph.plusEntitlement)
                    if (current?.active == true) successUntil = current.until
                    else problem = com.onezee.triptrack.designsystem.R.string.promo_already_redeemed
                } else problem = promoError(error.code)
            } catch (error: ApiError.ValidationFailed) { problem = com.onezee.triptrack.designsystem.R.string.promo_validation }
            catch (error: ApiError) {
                if (promoSessionExpired(error)) sessionExpired = true
                else problem = com.onezee.triptrack.designsystem.R.string.promo_offline
            }
            catch (_: Exception) { problem = com.onezee.triptrack.designsystem.R.string.promo_offline }
            finally { busy = false }
        }
    }
    ContentSizedSheet(onDismiss, background = AppTheme.colors.bg) {
        val sheet = this
        PromoCodeSheetContent(signedIn && !sessionExpired, code, busy, problem, successUntil, onCodeChange = { code = it; problem = null },
            onRedeem = ::redeem, onDismiss = { sheet.dismiss() }) { AccountEntry(graph) }
    }
}

@Composable
internal fun PromoCodeSheetContent(
    signedIn: Boolean,
    code: String,
    busy: Boolean,
    problem: Int?,
    successUntil: Instant?,
    onCodeChange: (String) -> Unit,
    onRedeem: () -> Unit,
    onDismiss: () -> Unit,
    signedOutContent: @Composable () -> Unit = {},
) {
    val strings = appStrings
    MeSheetHeader(strings.tr(com.onezee.triptrack.designsystem.R.string.promo_code), onDismiss)
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings.tr(com.onezee.triptrack.designsystem.R.string.promo_body), style = inter(14f), color = AppTheme.colors.textSecondary)
        if (!signedIn) {
            Text(strings.tr(com.onezee.triptrack.designsystem.R.string.promo_signed_out), style = inter(13f), color = AppTheme.colors.textSecondary, modifier = Modifier.testTag("promo_signed_out"))
            signedOutContent()
        } else if (successUntil != null) {
            Text(strings.tr(com.onezee.triptrack.designsystem.R.string.promo_success), style = inter(17f, InterWeight.bold), color = AppTheme.colors.text, modifier = Modifier.testTag("promo_success"))
            Text(strings.tr(com.onezee.triptrack.designsystem.R.string.promo_until, dateOnly(successUntil, strings)), style = inter(14f), color = AppTheme.colors.textSecondary, modifier = Modifier.testTag("promo_until"))
            PrimaryPromoButton(strings.tr(com.onezee.triptrack.designsystem.R.string.promo_done), busy = false, tag = "promo_close", onClick = onDismiss)
        } else {
            PromoCodeField(code, enabled = !busy, hint = strings.tr(com.onezee.triptrack.designsystem.R.string.promo_hint), onChange = onCodeChange, onSubmit = onRedeem)
            Row(Modifier.heightIn(min = 18.dp).testTag("promo_error"), verticalAlignment = Alignment.CenterVertically) {
                problem?.let { Text(strings.tr(it), style = inter(12.5f, InterWeight.medium), color = AppTheme.red) }
            }
            PrimaryPromoButton(strings.tr(com.onezee.triptrack.designsystem.R.string.promo_redeem), busy, "promo_redeem_button", onRedeem)
        }
    }
}

@Composable
private fun PromoCodeField(value: String, enabled: Boolean, hint: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val codeStyle = inter(16f, InterWeight.medium).copy(fontFamily = FontFamily.Monospace, color = AppTheme.colors.text)
    val hintStyle = inter(16f, InterWeight.medium).copy(fontFamily = FontFamily.Monospace, color = AppTheme.colors.textTertiary)
    Row(Modifier.fillMaxWidth().background(AppTheme.colors.card, RoundedCornerShape(14.dp))
        .border(1.5.dp, if (focused) AppTheme.accent else AppTheme.colors.borderBright, RoundedCornerShape(14.dp))
        .padding(horizontal = 15.dp, vertical = 16.dp).testTag("promo_code_field")) {
        BasicTextField(value, onChange, Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }, enabled = enabled,
            singleLine = true, textStyle = codeStyle,
            cursorBrush = SolidColor(AppTheme.accent), decorationBox = { inner ->
                Box { if (value.isEmpty()) Text(hint, style = hintStyle); inner() }
            }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }))
    }
}

@Composable
private fun PrimaryPromoButton(title: String, busy: Boolean, tag: String, onClick: () -> Unit) {
    val rotation = if (busy) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "promo_progress")
        val angle by transition.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "promo_progress_rotation")
        angle
    } else 0f
    Row(Modifier.fillMaxWidth().background(AppTheme.accent, RoundedCornerShape(14.dp))
        .clickable(enabled = !busy, role = Role.Button, onClick = onClick).padding(vertical = 14.dp).testTag(tag),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (busy) Glyph(com.onezee.triptrack.designsystem.components.AppGlyph.arrowClockwise, null,
            Modifier.graphicsLayer { rotationZ = rotation }, tint = androidx.compose.ui.graphics.Color.White, size = 18.dp)
        else Text(title, style = inter(15f, InterWeight.bold), color = androidx.compose.ui.graphics.Color.White)
    }
}

internal fun promoError(code: String): Int = when (code) {
    "PROMO_CODE_NOT_FOUND" -> com.onezee.triptrack.designsystem.R.string.promo_not_found
    "PROMO_CODE_ALREADY_REDEEMED" -> com.onezee.triptrack.designsystem.R.string.promo_already_redeemed
    "PROMO_CODE_INACTIVE" -> com.onezee.triptrack.designsystem.R.string.promo_inactive
    "PROMO_CODE_EXPIRED" -> com.onezee.triptrack.designsystem.R.string.promo_expired
    "PROMO_CODE_USED_UP" -> com.onezee.triptrack.designsystem.R.string.promo_used_up
    "TOO_MANY_REQUESTS" -> com.onezee.triptrack.designsystem.R.string.promo_too_many
    "VALIDATION_FAILED" -> com.onezee.triptrack.designsystem.R.string.promo_validation
    else -> com.onezee.triptrack.designsystem.R.string.promo_offline
}

internal fun promoSessionExpired(error: ApiError): Boolean = error is ApiError.UserNotAuth || error is ApiError.InvalidRefreshToken

internal suspend fun refreshAfterAlreadyRedeemed(store: PlusEntitlementStore): PlusEntitlement? = store.refresh()

internal fun dateOnly(value: Instant, strings: AppStrings): String = LocalizedDateFormatter.templates("dMMMyyyy", strings.zone).getValue(strings.language).format(value)
