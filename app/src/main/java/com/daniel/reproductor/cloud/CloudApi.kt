package com.daniel.reproductor.cloud

import com.daniel.reproductor.data.UserFacingException
import com.daniel.reproductor.data.db.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class PartnerInfo(val username: String, val displayName: String, val online: Boolean)

/** none = sin pareja, sent = invitación enviada, received = invitación recibida, linked = vinculados. */
data class PartnerStatus(val status: String, val partner: PartnerInfo?) {
    val linked get() = status == "linked"
}

data class Reaction(val emoji: String, val mine: Boolean, val at: Long)

data class Dedication(
    val id: String,
    val incoming: Boolean,
    val fromName: String,
    val song: Song,
    val message: String,
    val autoplay: Boolean,
    val createdAt: Long,
    val seenAt: Long?,
    val reactions: List<Reaction>,
)

/** Cliente HTTP del servidor de Minu. */
class CloudApi(baseClient: OkHttpClient, private val session: Session) {

    // Render (plan gratis) duerme el servidor: la primera petición puede tardar ~1 minuto
    private val client = baseClient.newBuilder()
        .connectTimeout(70, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .build()

    suspend fun register(username: String, password: String, displayName: String, inviteCode: String): Account =
        authResponse(
            call(
                "POST", "/auth/register",
                JSONObject().put("username", username).put("password", password)
                    .put("displayName", displayName).put("inviteCode", inviteCode),
                auth = false,
            )
        )

    suspend fun login(username: String, password: String): Account =
        authResponse(call("POST", "/auth/login", JSONObject().put("username", username).put("password", password), auth = false))

    /** Copia de la biblioteca en la nube: (datos o null, updatedAt). */
    suspend fun getLibrary(): Pair<JSONObject?, Long> {
        val res = call("GET", "/library")
        return res.optJSONObject("data") to res.optLong("updatedAt")
    }

    suspend fun putLibrary(data: JSONObject, device: String): Long =
        call("PUT", "/library", JSONObject().put("data", data).put("device", device)).getLong("updatedAt")

    suspend fun partner(): PartnerStatus = parsePartner(call("GET", "/partner"))
    suspend fun requestPartner(username: String) = parsePartner(call("POST", "/partner/request", JSONObject().put("username", username)))
    suspend fun acceptPartner() = parsePartner(call("POST", "/partner/accept", JSONObject()))
    suspend fun unlinkPartner() = parsePartner(call("DELETE", "/partner"))

    suspend fun dedications(): List<Dedication> {
        val array = call("GET", "/dedications").getJSONArray("dedications")
        return (0 until array.length()).map { parseDedication(array.getJSONObject(it)) }
    }

    /** Devuelve la dedicatoria y si le llegó al instante (su app estaba abierta). */
    suspend fun sendDedication(song: Song, message: String, autoplay: Boolean): Pair<Dedication, Boolean> {
        val res = call(
            "POST", "/dedications",
            JSONObject().put("song", songJson(song)).put("message", message).put("autoplay", autoplay),
        )
        return parseDedication(res.getJSONObject("dedication")) to res.optBoolean("delivered")
    }

    suspend fun markSeen(id: String) {
        call("POST", "/dedications/$id/seen", JSONObject())
    }

    suspend fun react(id: String, emoji: String): Dedication =
        parseDedication(call("POST", "/dedications/$id/react", JSONObject().put("emoji", emoji)).getJSONObject("dedication"))

    private suspend fun call(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            val token = session.account.value?.token
            if (auth && token == null) throw UserFacingException("Inicia sesión primero.")
            val request = Request.Builder()
                .url(session.serverUrl + path)
                .method(method, body?.toString()?.toRequestBody(JSON))
                .apply { if (auth) header("Authorization", "Bearer $token") }
                .build()
            try {
                client.newCall(request).execute().use { res ->
                    val text = res.body.string()
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    if (!res.isSuccessful) {
                        if (res.code == 401 && auth) session.signOut()
                        throw UserFacingException(json?.optString("error")?.ifBlank { null } ?: "Error del servidor (${res.code}).")
                    }
                    json ?: throw UserFacingException("Respuesta inválida del servidor. Revisa la URL del servidor.")
                }
            } catch (e: IOException) {
                throw UserFacingException("No se pudo conectar con el servidor. Si estaba dormido, intenta de nuevo en un minuto.", e)
            } catch (e: IllegalArgumentException) {
                throw UserFacingException("La URL del servidor no es válida: ${session.serverUrl}", e)
            }
        }

    private fun authResponse(json: JSONObject): Account {
        val user = json.getJSONObject("user")
        return Account(user.getString("username"), user.optString("displayName"), json.getString("token"))
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun songJson(song: Song): JSONObject = JSONObject()
            .put("videoId", song.videoId).put("title", song.title).put("uploader", song.uploader)
            .put("durationSec", song.durationSec).put("thumbnailUrl", song.thumbnailUrl)

        fun parseSong(json: JSONObject) = Song(
            videoId = json.getString("videoId"),
            title = json.optString("title"),
            uploader = json.optString("uploader"),
            durationSec = json.optLong("durationSec"),
            thumbnailUrl = json.optString("thumbnailUrl"),
        )

        fun parsePartner(json: JSONObject): PartnerStatus {
            val p = json.optJSONObject("partner")
            return PartnerStatus(
                json.optString("status", "none"),
                p?.let { PartnerInfo(it.optString("username"), it.optString("displayName"), it.optBoolean("online")) },
            )
        }

        fun parseDedication(json: JSONObject): Dedication {
            val reactions = json.optJSONArray("reactions") ?: JSONArray()
            return Dedication(
                id = json.getString("id"),
                incoming = json.optBoolean("incoming"),
                fromName = json.optString("fromName"),
                song = parseSong(json.getJSONObject("song")),
                message = json.optString("message"),
                autoplay = json.optBoolean("autoplay"),
                createdAt = json.optLong("createdAt"),
                seenAt = json.optLong("seenAt").takeIf { it > 0 },
                reactions = (0 until reactions.length()).map {
                    val r = reactions.getJSONObject(it)
                    Reaction(r.optString("emoji"), r.optBoolean("mine"), r.optLong("at"))
                },
            )
        }
    }
}
