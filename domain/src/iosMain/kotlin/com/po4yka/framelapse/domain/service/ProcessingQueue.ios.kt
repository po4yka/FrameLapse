package com.po4yka.framelapse.domain.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

actual class ProcessingQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = mutableMapOf<String, Job>()

    actual fun launch(taskId: String, block: suspend () -> Unit): Job {
        jobs[taskId]?.cancel()
        val job = scope.launch { block() }
        jobs[taskId] = job
        job.invokeOnCompletion { jobs.remove(taskId) }
        return job
    }

    actual fun cancel(taskId: String) {
        jobs.remove(taskId)?.cancel()
    }

    actual fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}
