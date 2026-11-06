package com.blueorbit.ruler.feature.ruler

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Touch on the plate moves the span end to the finger: a tap jumps it there, a horizontal drag follows
 * the finger. The plate draws a knob at the span end, and a knob that ignores a finger is a lie the
 * first user finds in a second — this is what makes it real.
 *
 * The callbacks receive the finger's x in **canvas pixels**, the same frame `StartEdge.measureAt`
 * works in, so the conversion to a length happens once, in the caller, with the same maths the slider
 * uses. Only horizontal drags are claimed, so a vertical scroll that starts on the plate still scrolls.
 *
 * `onDragging(true)` lets the caller draw the span without its easing while the finger is down: a
 * 100 ms tween restarted on every move makes the line trail the number it belongs to.
 */
@Composable
fun Modifier.spanDrag(
    onSpanEnd: (xPixels: Float) -> Unit,
    onDragging: (Boolean) -> Unit = {}
): Modifier {
    val onEnd by rememberUpdatedState(onSpanEnd)
    val dragging by rememberUpdatedState(onDragging)
    return this
        .pointerInput(Unit) {
            detectTapGestures(onTap = { onEnd(it.x) })
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    dragging(true)
                    onEnd(offset.x)
                },
                onDragEnd = { dragging(false) },
                onDragCancel = { dragging(false) },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    onEnd(change.position.x)
                }
            )
        }
}
