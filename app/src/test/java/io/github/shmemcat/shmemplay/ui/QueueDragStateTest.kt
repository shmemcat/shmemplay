package io.github.shmemcat.shmemplay.ui

import org.junit.Assert.*
import org.junit.Test

class QueueDragStateTest {
    private val order = listOf("A", "B", "C", "D")

    @Test fun draggedRowFollowsFingerAndNeighboursMakeRoom() {
        val drag = QueueDragState()
        drag.begin("B")
        drag.move(80f, order, 56f)
        assertEquals(80f, drag.translation("B", order, 56f), 0f)
        assertEquals(-56f, drag.translation("C", order, 56f), 0f)
        assertEquals(0f, drag.translation("D", order, 56f), 0f)
        assertEquals(listOf("A", "C", "B", "D"), drag.reordered(order, 56f))
        assertEquals(listOf("A", "B", "C", "D"), order)
    }

    @Test fun upwardDragAndBoundsPreserveEveryIdentity() {
        val drag = QueueDragState()
        drag.begin("C")
        drag.move(-10000f, order, 56f)
        assertEquals(-112f, drag.delta, 0f)
        assertEquals(56f, drag.translation("A", order, 56f), 0f)
        assertEquals(listOf("C", "A", "B", "D"), drag.reordered(order, 56f))
        drag.move(10000f, order, 56f)
        assertEquals(listOf("A", "B", "D", "C"), drag.reordered(order, 56f))
    }

    @Test fun cancelRemovesPreviewWithoutReordering() {
        val drag = QueueDragState()
        drag.begin("A")
        drag.move(130f, order, 56f)
        drag.clear()
        assertNull(drag.id)
        assertEquals(order, drag.reordered(order, 56f))
        order.forEach { assertEquals(0f, drag.translation(it, order, 56f), 0f) }
    }

    @Test fun missingDraggedIdentityDoesNotChangeUpdatedQueue() {
        val drag = QueueDragState()
        drag.begin("B")
        drag.move(80f, order, 56f)
        val updated = listOf("A", "C", "D")
        assertEquals(updated, drag.reordered(updated, 56f))
    }
}
