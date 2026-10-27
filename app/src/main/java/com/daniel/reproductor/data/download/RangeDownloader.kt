package com.daniel.reproductor.data.download

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Descarga por bloques con el header Range. YouTube limita la velocidad cuando se pide
 * el archivo completo en una sola petición; así lo hace también NewPipe.
 * Si [target] ya tiene datos (descarga interrumpida), continúa desde donde quedó.
 */
class RangeDownloader(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val chunkSize: Long = 4L * 1024 * 1024,
) {
    fun download(url: String, target: File, onProgress: (downloaded: Long, total: Long) -> Unit) {
        var downloaded = target.length()
        var total = -1L

        FileOutputStream(target, /* append = */ true).use { out ->
            while (total < 0 || downloaded < total) {
                val before = downloaded
                val end = downloaded + chunkSize - 1
                val request = Request.Builder().url(url)
                    .header("User-Agent", userAgent)
                    .header("Range", "bytes=$downloaded-$end")
                    .build()

                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        206 -> total = parseTotal(response.header("Content-Range")) ?: total
                        200 -> { // El servidor ignoró el Range: viene el archivo completo
                            if (downloaded > 0) throw IOException("El servidor no admite continuar la descarga")
                            total = response.body.contentLength()
                        }
                        416 -> if (total < 0) total = downloaded // ya estaba completo
                        else -> throw IOException("HTTP ${response.code} al descargar")
                    }
                    if (response.code == 416) return@use
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            downloaded += n
                        }
                    }
                    if (response.code == 200) total = downloaded
                    if (total < 0) throw IOException("No se pudo saber el tamaño del archivo")
                    if (downloaded < total && downloaded == before) throw IOException("La descarga no avanza")
                }
                onProgress(downloaded, total)
            }
        }
    }

    private fun parseTotal(contentRange: String?): Long? =
        contentRange?.substringAfterLast('/')?.toLongOrNull()
}
