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

/** El día en que empezó la relación, la canción de origen y cómo empezó (de los dos). */
data class DuoOrigin(
    val startedOn: String, // AAAA-MM-DD
    val song: Song?,
    val story: String,
    val setByName: String,
    val setByMe: Boolean,
)

/** El reto del día para salvar la racha. type null = todavía no lo eligen. */
data class StreakRescue(
    val missedDay: String,
    val day: String,
    val type: String?,
    val chosenByName: String?,
    val listenedSec: Int,
    val goalSec: Int,
)

/** Cuándo y cuánto se perdió (para mostrarlo una vez). reason: timeout, missed, no_rescues, gave_up. */
data class StreakBroken(val on: String, val lost: Int, val reason: String)

data class DuoStreak(
    val today: String,
    val count: Int,
    val best: Int,
    val todayMine: Boolean,
    val todayPartner: Boolean,
    val state: String, // active, at_risk, rescuing
    val rescue: StreakRescue?,
    val rescuesLeft: Int,
    val broken: StreakBroken?,
    val savedDay: String?,
) {
    val inDanger get() = state != "active"
}

/** Misión del mes: cada uno marca "Lo hicimos". */
data class MissionState(val mine: Boolean, val partner: Boolean) {
    val done get() = mine && partner
}

data class DuoInfo(
    val origin: DuoOrigin?,
    val dedicationCount: Int,
    val listenSec: Long = 0,
    val streak: DuoStreak? = null,
    val missions: Map<Int, MissionState> = emptyMap(),
)

data class CapsuleAnswer(val song: Song, val note: String)

/** La cápsula de tres canciones. [partner] llega solo cuando se abrió (los dos respondieron). */
data class Capsule(
    val day: String,
    val prompts: List<String>,
    val mine: List<CapsuleAnswer>?,
    val partnerDone: Boolean,
    val partner: List<CapsuleAnswer>?,
    val openedAt: Long?,
)

data class MixtapeTrack(val song: Song, val message: String, val day: String)

/** La Cinta del mes: lado A tus dedicatorias, lado B las de tu pareja. */
data class Mixtape(val from: String, val to: String, val sideA: List<MixtapeTrack>, val sideB: List<MixtapeTrack>) {
    /** Las dos caras mezcladas en el orden en que se dedicaron. */
    val inOrder get() = (sideA + sideB).sortedBy { it.day }
}

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

    /** "Continuar con Google": el servidor verifica el token y crea la cuenta si el correo está permitido. */
    suspend fun googleLogin(idToken: String): Account =
        authResponse(call("POST", "/auth/google", JSONObject().put("idToken", idToken), auth = false))

    /** Registra este celular para recibir notificaciones. */
    suspend fun registerDevice(token: String) {
        call("POST", "/devices", JSONObject().put("token", token))
    }

    suspend fun unregisterDevice(token: String) {
        call("DELETE", "/devices", JSONObject().put("token", token))
    }

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

    /** Lo compartido en Dúo; null si no hay pareja vinculada. */
    suspend fun duo(): DuoInfo? = parseDuo(call("GET", "/duo"))

    suspend fun saveOrigin(startedOn: String, song: Song?, story: String): DuoInfo? = parseDuo(
        call(
            "PUT", "/duo/origin",
            JSONObject().put("startedOn", startedOn).put("song", song?.let(::songJson) ?: JSONObject.NULL).put("story", story),
        )
    )

    suspend fun chooseRescue(type: String): DuoInfo? = parseDuo(call("POST", "/duo/rescue", JSONObject().put("type", type)))
    suspend fun giveUpRescue(): DuoInfo? = parseDuo(call("POST", "/duo/rescue/giveup", JSONObject()))

    /** La cápsula del día del reto, o null si hoy no toca. */
    suspend fun capsule(): Capsule? = call("GET", "/duo/capsule").optJSONObject("capsule")?.let(::parseCapsule)

    suspend fun answerCapsule(answers: List<CapsuleAnswer>): Capsule {
        val list = JSONArray()
        answers.forEach { list.put(JSONObject().put("song", songJson(it.song)).put("note", it.note)) }
        return parseCapsule(call("POST", "/duo/capsule", JSONObject().put("answers", list)).getJSONObject("capsule"))
    }

    /** Cápsulas ya abiertas, de la más nueva a la más vieja. */
    suspend fun memories(): List<Capsule> {
        val array = call("GET", "/duo/memories").getJSONArray("capsules")
        return (0 until array.length()).map { parseCapsule(array.getJSONObject(it)) }
    }

    /** [from] incluido, [to] sin incluir (AAAA-MM-DD). */
    suspend fun mixtape(from: String, to: String): Mixtape {
        val json = call("GET", "/duo/mixtape?from=$from&to=$to")
        fun side(name: String): List<MixtapeTrack> {
            val array = json.optJSONArray(name) ?: JSONArray()
            return (0 until array.length()).map {
                val t = array.getJSONObject(it)
                MixtapeTrack(parseSong(t.getJSONObject("song")), t.optString("message"), t.optString("day"))
            }
        }
        return Mixtape(from, to, side("sideA"), side("sideB"))
    }

    suspend fun markMission(month: Int, done: Boolean): DuoInfo? =
        parseDuo(call("POST", "/duo/missions/$month", JSONObject().put("done", done)))

    private suspend fun call(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            val token = session.account.value?.token
            if (auth && token == null) throw UserFacingException("Inicia sesión primero.")
            val request = Request.Builder()
                .url(session.serverUrl + path)
                .method(method, body?.toString()?.toRequestBody(JSON))
                .apply { if (auth) header("Authorization", "Bearer $token") }
                // El día de la racha va de medianoche a medianoche en la zona horaria del celular
                .header("X-Timezone", java.time.ZoneId.systemDefault().id)
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

        fun parseDuo(json: JSONObject): DuoInfo? {
            if (!json.optBoolean("linked")) return null
            val origin = json.optJSONObject("origin")?.let { o ->
                DuoOrigin(
                    startedOn = o.getString("startedOn"),
                    song = o.optJSONObject("song")?.let(::parseSong),
                    story = o.optString("story"),
                    setByName = o.optString("setByName"),
                    setByMe = o.optBoolean("setByMe"),
                )
            }
            val stats = json.optJSONObject("stats")
            val missions = json.optJSONObject("missions")?.let { m ->
                m.keys().asSequence().mapNotNull { k ->
                    val v = m.optJSONObject(k) ?: return@mapNotNull null
                    k.toIntOrNull()?.let { it to MissionState(v.optBoolean("mine"), v.optBoolean("partner")) }
                }.toMap()
            } ?: emptyMap()
            return DuoInfo(
                origin, stats?.optInt("dedications") ?: 0, stats?.optLong("listenSec") ?: 0,
                json.optJSONObject("streak")?.let(::parseStreak), missions,
            )
        }

        private fun parseAnswers(array: JSONArray?): List<CapsuleAnswer>? = array?.let {
            (0 until it.length()).map { i ->
                val a = it.getJSONObject(i)
                CapsuleAnswer(parseSong(a.getJSONObject("song")), a.optString("note"))
            }
        }

        fun parseCapsule(c: JSONObject): Capsule {
            val prompts = c.optJSONArray("prompts") ?: JSONArray()
            return Capsule(
                day = c.optString("day"),
                prompts = (0 until prompts.length()).map { prompts.getString(it) },
                mine = parseAnswers(c.optJSONArray("mine")),
                partnerDone = c.optBoolean("partnerDone"),
                partner = parseAnswers(c.optJSONArray("partner")),
                openedAt = c.optLong("openedAt").takeIf { it > 0 },
            )
        }

        private fun parseStreak(s: JSONObject): DuoStreak {
            val rescue = s.optJSONObject("rescue")?.let { r ->
                StreakRescue(
                    missedDay = r.optString("missedDay"),
                    day = r.optString("day"),
                    type = r.optString("type").takeIf { !r.isNull("type") && it.isNotBlank() },
                    chosenByName = r.optString("chosenByName").takeIf { !r.isNull("chosenByName") && it.isNotBlank() },
                    listenedSec = r.optInt("listenedSec"),
                    goalSec = r.optInt("goalSec", 1800),
                )
            }
            val broken = s.optJSONObject("broken")?.let { StreakBroken(it.optString("on"), it.optInt("lost"), it.optString("reason")) }
            return DuoStreak(
                today = s.optString("today"),
                count = s.optInt("count"),
                best = s.optInt("best"),
                todayMine = s.optBoolean("todayMine"),
                todayPartner = s.optBoolean("todayPartner"),
                state = s.optString("state", "active"),
                rescue = rescue,
                rescuesLeft = s.optInt("rescuesLeft", 3),
                broken = broken,
                savedDay = s.optJSONObject("saved")?.optString("day"),
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
