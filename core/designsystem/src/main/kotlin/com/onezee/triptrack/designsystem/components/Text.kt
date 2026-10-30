package com.onezee.triptrack.designsystem.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

/** The role of text that does not name one, like SwiftUI's environment font. */
val LocalTextStyle = compositionLocalOf { TextStyle.Default }

/** The ink of text and glyphs that do not name one, like SwiftUI's `foregroundStyle`. */
val LocalContentColor = compositionLocalOf { Color.Black }

/**
 * The house Text: `BasicText` in the given [AppType] role and the theme's ink. Like SwiftUI's Text
 * it ends with an ellipsis when it runs out of lines. `letterSpacing` is iOS `.tracking`, set at
 * the call site where iOS sets it.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
    autoSize: TextAutoSize? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = resolve(style, color, letterSpacing, lineHeight, textAlign),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        autoSize = autoSize,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
    autoSize: TextAutoSize? = null,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = resolve(style, color, letterSpacing, lineHeight, textAlign),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        autoSize = autoSize,
    )
}

@Composable
private fun resolve(
    style: TextStyle,
    color: Color,
    letterSpacing: TextUnit,
    lineHeight: TextUnit,
    textAlign: TextAlign,
): TextStyle {
    val ink = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    return style.copy(
        color = ink,
        letterSpacing = if (letterSpacing.isSpecified) letterSpacing else style.letterSpacing,
        lineHeight = if (lineHeight.isSpecified) lineHeight else style.lineHeight,
        textAlign = if (textAlign != TextAlign.Unspecified) textAlign else style.textAlign,
    )
}
