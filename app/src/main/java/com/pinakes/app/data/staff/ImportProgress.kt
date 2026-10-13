package com.pinakes.app.data.staff

import com.pinakes.app.data.model.ImportJob
import com.pinakes.app.data.network.ApiResult

/** What the chunk driver does after an answer. */
sealed interface ChunkStep {
    /** Ask for the next chunk from [start]. */
    data class Next(val start: Int) : ChunkStep
    /** The job is over (complete, failed or cancelled). */
    data object Done : ChunkStep
    /** Another device is running this job: wait, then ask again from [start]. */
    data class Wait(val start: Int) : ChunkStep
    /** Stop the loop and show the failure (offline, expired, refused). */
    data class Stop(val failure: ApiResult.Failure) : ChunkStep
}

/**
 * The pure rules of the resumable import (`POST /staff/imports/{id}/chunk`). The server owns the
 * progress: every chunk starts at the job's `processed`; a 409 `out_of_order` says where the
 * server really is (`meta.processed`), so the app continues from there instead of failing; a
 * 409 `job_busy` means another loop is running and is retried after a pause; 410 `gone`
 * (expired, cancelled, already completed) ends the loop.
 */
object ImportProgress {
    private val FINAL_STATES = setOf("completed", "failed", "cancelled")

    const val BUSY_RETRIES = 5

    fun isFinished(job: ImportJob): Boolean = job.complete || job.status in FINAL_STATES

    /** 0..100, clamped; an empty file is 100 once finished. */
    fun percent(job: ImportJob): Int = when {
        job.totalRows <= 0 -> if (isFinished(job)) 100 else 0
        else -> ((job.processed.coerceIn(0, job.totalRows) * 100L) / job.totalRows).toInt()
    }

    fun next(job: ImportJob): ChunkStep = if (isFinished(job)) ChunkStep.Done else ChunkStep.Next(job.processed)

    /** After a failed chunk request that started at [start]. */
    fun afterFailure(failure: ApiResult.Failure, start: Int, busyAttempts: Int): ChunkStep = when (failure.code) {
        "out_of_order" -> failure.meta?.processed?.let { ChunkStep.Next(it) } ?: ChunkStep.Stop(failure)
        "job_busy" -> if (busyAttempts < BUSY_RETRIES) ChunkStep.Wait(start) else ChunkStep.Stop(failure)
        else -> ChunkStep.Stop(failure)
    }

    /** Accepted file extensions per kind (the server re-checks). */
    fun acceptsFileName(name: String, librarything: Boolean): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return if (librarything) ext in setOf("tsv", "csv", "txt") else ext == "csv"
    }

    /** Upload limit enforced by the server (20 MB). */
    const val MAX_BYTES = 20L * 1024 * 1024
}
