package com.po4yka.framelapse.domain.service

import kotlinx.coroutines.Job

/**
 * Background processing boundary for long-running tasks.
 */
expect class ProcessingQueue() {
    fun launch(taskId: String, block: suspend () -> Unit): Job
    fun cancel(taskId: String)
    fun cancelAll()
}
