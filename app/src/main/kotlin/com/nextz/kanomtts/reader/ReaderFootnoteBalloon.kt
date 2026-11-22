package com.nextz.kanomtts.reader

import android.text.Html
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

internal data class ReaderFootnoteBubble(val html: String, val anchorXpx: Float, val anchorYpx: Float)

/** A non-modal, non-dimming footnote balloon positioned near the tapped anchor. */
@Composable
internal fun ReaderFootnoteBalloon(bubble: ReaderFootnoteBubble, onDismiss: () -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    BoxWithConstraints(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss,
        ),
    ) {
        val density = LocalDensity.current
        val balloonWidth = maxWidth * 0.76f
        val balloonHeight = maxHeight * 0.38f
        val anchorX = with(density) { bubble.anchorXpx.toDp() }
        val anchorY = with(density) { bubble.anchorYpx.toDp() }
        val left = (anchorX - balloonWidth / 2).coerceIn(8.dp, (maxWidth - balloonWidth - 8.dp).coerceAtLeast(8.dp))
        val below = anchorY + 12.dp
        val top = if (below + balloonHeight <= maxHeight - 8.dp) below else (anchorY - balloonHeight - 12.dp).coerceAtLeast(8.dp)
        Surface(
            modifier = Modifier
                .offset { IntOffset(with(density) { left.roundToPx() }, with(density) { top.roundToPx() }) }
                .width(balloonWidth)
                .heightIn(max = balloonHeight)
                .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = {}),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 10.dp,
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("เชิงอรรถ", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "ปิดเชิงอรรถ") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AndroidView(
                    modifier = Modifier.fillMaxWidth().heightIn(max = balloonHeight - 56.dp),
                    factory = { context ->
                        TextView(context).apply {
                            textSize = 16f
                            setTextColor(textColor)
                            setPadding(4, 8, 4, 8)
                            movementMethod = ScrollingMovementMethod()
                            setBackgroundColor(AndroidColor.TRANSPARENT)
                        }
                    },
                    update = { view ->
                        view.setTextColor(textColor)
                        view.text = Html.fromHtml(bubble.html, Html.FROM_HTML_MODE_COMPACT)
                    },
                )
            }
        }
    }
}