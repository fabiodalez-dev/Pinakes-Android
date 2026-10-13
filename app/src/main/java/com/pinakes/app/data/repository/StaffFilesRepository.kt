package com.pinakes.app.data.repository

import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.parseErrorBody
import com.pinakes.app.data.store.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.net.ssl.SSLException

/**
 * Staff exports (loan receipts, label PDFs, import error CSVs) downloaded with the bearer token
 * into `cache/exports/`, which the FileProvider shares. Files older than a day are pruned on
 * every download, so the directory never grows. A 403 `forbidden` drops the role like every
 * other staff call.
 */
class StaffFilesRepository(
    private val network: NetworkModule,
    private val session: SessionStore,
    cacheDir: File,
) {
    val dir: File = File(cacheDir, EXPORTS_DIR)

    /** Download [path] (relative to `/api/v1/`) to a file named after the server's filename or [fallbackName]. */
    suspend fun download(path: String, fallbackName: String): ApiResult<File> = withContext(Dispatchers.IO) {
        prune(System.currentTimeMillis())
        try {
            val response = network.staffContentApi().download(path)
            if (!response.isSuccessful) {
                val failure = parseErrorBody(response.errorBody()?.string(), response.code())
                if (failure.isForbidden()) session.saveRole(null)
                return@withContext failure
            }
            val body = response.body() ?: return@withContext ApiResult.Failure(ErrorCodes.UNKNOWN, "Empty body", response.code())
            dir.mkdirs()
            val name = safeName(filenameFrom(response.headers()["Content-Disposition"]) ?: fallbackName)
            val target = File(dir, name)
            val partial = File(dir, "$name.part")
            body.use { b -> partial.outputStream().use { out -> b.byteStream().copyTo(out) } }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            ApiResult.Success(target)
        } catch (e: SSLException) {
            ApiResult.Failure(ErrorCodes.TLS, e.message ?: "TLS error")
        } catch (e: IOException) {
            ApiResult.Failure(ErrorCodes.NETWORK, e.message ?: "Network error")
        } catch (e: Throwable) {
            ApiResult.Failure(ErrorCodes.UNKNOWN, e.message ?: "Unexpected error")
        }
    }

    /** Remove exports older than [MAX_AGE_MS]. */
    fun prune(nowMs: Long) {
        dir.listFiles()?.forEach { if (nowMs - it.lastModified() > MAX_AGE_MS) it.delete() }
    }

    companion object {
        const val EXPORTS_DIR = "exports"
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000

        /** `attachment; filename="prestito_7_20261014.pdf"` → `prestito_7_20261014.pdf`. */
        fun filenameFrom(disposition: String?): String? {
            if (disposition.isNullOrBlank()) return null
            Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE).find(disposition)?.let {
                return java.net.URLDecoder.decode(it.groupValues[1].trim('"'), "UTF-8")
            }
            return Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.get(1)?.trim()
        }

        /** Only the last path segment, with characters a file system or a share target dislikes replaced. */
        fun safeName(raw: String): String {
            val base = raw.substringAfterLast('/').substringAfterLast('\\')
            val cleaned = base.replace(Regex("""[^A-Za-z0-9._-]"""), "_").trim('.', '_')
            return cleaned.ifBlank { "export" }.take(120)
        }
    }
}
