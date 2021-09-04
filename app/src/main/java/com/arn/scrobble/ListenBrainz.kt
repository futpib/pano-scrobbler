package com.arn.scrobble

import de.umass.lastfm.scrobble.ScrobbleData
import de.umass.lastfm.scrobble.ScrobbleResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


class ListenBrainz (private val token: String) {
    private var apiRoot = Stuff.LISTENBRAINZ_API_ROOT

    fun setApiRoot(url: String): ListenBrainz {
        apiRoot = url
        return this
    }

    private fun submitListens(scrobbledatas: List<ScrobbleData>, listenType: String): ScrobbleResult {
        val payload = JSONArray()
        scrobbledatas.forEach {
            val payloadTrack = JSONObject()
                    .put("track_metadata", JSONObject()
                            .put("artist_name", it.artist ?: "")
                            .put("release_name", it.album ?: "")
                            .put("track_name", it.track ?: "")
                    )
            if (listenType != "playing_now")
                payloadTrack.put("listened_at", it.timestamp)
            payload.put(payloadTrack)
        }
        val data = JSONObject()
                .put("listen_type", listenType)
                .put("payload", payload)

        val url = URL("${apiRoot}1/submit-listens")

        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = Stuff.CONNECT_TIMEOUT
            conn.readTimeout = Stuff.READ_TIMEOUT
            conn.requestMethod = "POST"
            conn.setRequestProperty ("Authorization", "token $token")
            conn.setRequestProperty ("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true

            val os = conn.outputStream
            val writer = os.bufferedWriter()
            writer.write(data.toString())
            writer.close()
            os.close()
            conn.connect()

            var body: String? = null
            if (conn.responseCode == 200) {
                val ins = conn.inputStream
                val reader = ins.bufferedReader()
                body = reader.readText()
                reader.close()
                ins.close()
            }

            var errMsg = ""
            if (body != null) {
                val respJson = JSONObject(body)
                if (respJson.has("status"))
                    errMsg = respJson.getString("status")
                else if (respJson.has("error")) {
                    errMsg = respJson.getString("error")
                }
            }

            return ScrobbleResult.createHttp200OKResult(conn.responseCode,
                    conn.responseMessage, errMsg)

        } catch (e: Exception) {
            e.printStackTrace()

            return if (Thread.interrupted())
                ScrobbleResult.createHttp200OKResult(200, e.message, "ok") //suppress err notification
            else
                ScrobbleResult.createHttp200OKResult(0, e.message, "")
        }
    }

    fun updateNowPlaying(scrobbledata: ScrobbleData) = submitListens(listOf(scrobbledata), "playing_now")
    fun scrobble(scrobbledata: ScrobbleData) = submitListens(listOf(scrobbledata), "single")
    fun scrobble(scrobbledatas: MutableList<ScrobbleData>) = submitListens(scrobbledatas, "import")

    fun recents(username:String, limit: Int): JSONObject? {
        val url = URL("${apiRoot}1/user/$username/listens?count=$limit")
        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = Stuff.CONNECT_TIMEOUT
            conn.readTimeout = Stuff.READ_TIMEOUT
            conn.setRequestProperty("Authorization", "token $token")
            //this works even without the token or an incorrect token

            val ins = conn.inputStream
            val reader = ins.bufferedReader()
            val respJson = JSONObject(reader.readText())
            reader.close()
            ins.close()
//            conn.connect()
            return respJson
        } catch (e: IOException) {
        }

        return null
    }

    fun getUsername(): String? {
        val url = URL("${apiRoot}1/validate-token")

        var username: String? = null
        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = Stuff.CONNECT_TIMEOUT
            conn.readTimeout = Stuff.READ_TIMEOUT
            conn.setRequestProperty("Authorization", "token $token")
            conn.connect()
            if (conn.responseCode == 200) {
                val respJson = JSONObject(conn.inputStream.bufferedReader().readText())
                if (respJson.getBoolean("valid"))
                    username = respJson.getString("user_name")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return username
    }

}