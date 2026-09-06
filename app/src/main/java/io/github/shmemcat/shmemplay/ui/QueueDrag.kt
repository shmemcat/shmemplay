package io.github.shmemcat.shmemplay.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.shmemcat.shmemplay.R
import kotlin.math.roundToInt

@Stable internal class QueueDragState {
    var id by mutableStateOf<String?>(null)
        private set
    var delta by mutableFloatStateOf(0f)
        private set

    fun begin(value: String) { id = value; delta = 0f }
    fun move(amount: Float, order: List<String>, rowHeight: Float) {
        val from = order.indexOf(id)
        if (from >= 0) delta = (delta + amount).coerceIn(-from * rowHeight, (order.lastIndex - from) * rowHeight)
    }
    fun clear() { id = null; delta = 0f }
    fun target(order: List<String>, rowHeight: Float): Int =
        (order.indexOf(id) + (delta / rowHeight).roundToInt()).coerceIn(0, order.lastIndex.coerceAtLeast(0))
    fun translation(value: String, order: List<String>, rowHeight: Float): Float {
        if (value == id) return delta
        val from = order.indexOf(id)
        if (from < 0) return 0f
        val to = target(order, rowHeight)
        val index = order.indexOf(value)
        return when {
            to > from && index in (from + 1)..to -> -rowHeight
            to < from && index in to until from -> rowHeight
            else -> 0f
        }
    }
    fun reordered(order: List<String>, rowHeight: Float): List<String> {
        val from = order.indexOf(id)
        if (from < 0) return order
        val to = target(order, rowHeight)
        return order.toMutableList().also { it.add(to, it.removeAt(from)) }
    }
}

@Composable internal fun draggedRow(id: String, order: List<String>, drag: QueueDragState, height: Dp): Modifier {
    val rowHeight = with(LocalDensity.current) { height.toPx() }
    val target = drag.translation(id, order, rowHeight)
    val animated by animateFloatAsState(if (drag.id == id) 0f else target, label = "Queue drop gap")
    return Modifier.zIndex(if (drag.id == id) 1f else 0f).graphicsLayer {
        translationY = if (drag.id == null) 0f else if (drag.id == id) target else animated
        shadowElevation = if (drag.id == id) 8.dp.toPx() else 0f
    }
}

@Composable internal fun QueueDragHandle(id: String, order: List<String>, drag: QueueDragState, height: Dp,
    enabled: Boolean = true, onMove: (List<String>) -> Unit) {
    val rowHeight = with(LocalDensity.current) { height.toPx() }
    val latestOrder by rememberUpdatedState(order)
    val latestMove by rememberUpdatedState(onMove)
    Box(Modifier.size(28.dp, 48.dp).pointerInput(id, enabled, rowHeight) {
        if (enabled) detectDragGestures(
            onDragStart = { drag.begin(id) },
            onDragCancel = drag::clear,
            onDragEnd = {
                val result = drag.reordered(latestOrder, rowHeight)
                if (result != latestOrder) latestMove(result)
                drag.clear()
            },
        ) { change, amount ->
            change.consume()
            drag.move(amount.y, latestOrder, rowHeight)
        }
    }, contentAlignment = Alignment.Center) {
        PlayerIcon(R.drawable.ic_grip_vertical, "Drag to reorder", Modifier.size(20.dp))
    }
}
