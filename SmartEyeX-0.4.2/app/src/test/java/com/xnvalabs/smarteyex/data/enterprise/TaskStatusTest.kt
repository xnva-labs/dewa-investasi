package com.xnvalabs.smarteyex.data.enterprise

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskStatusTest {
    @Test
    fun cyclesThroughStatuses() {
        assertEquals(TaskStatus.DOING, TaskStatus.TODO.next())
        assertEquals(TaskStatus.DONE, TaskStatus.DOING.next())
        assertEquals(TaskStatus.TODO, TaskStatus.DONE.next())
    }
}
