package com.pairtv.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Playlist(
    val urls: List<String>,
    val scale: String, // "fill" или "fit"
)

object PlaylistClient {

    /** Скачивает playlist.json с ПК. Должно вызываться из фонового потока. */
    fun fetch(pcIp: String, pcPort: Int): Playlist? {
        return try {
            val url = URL("http://$pcIp:$pcPort/playlist.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(text)
            val items = json.getJSONArray("items")
            val list = mutableListOf<String>()
            for (i in 0 until items.length()) {
                list.add(items.getJSONObject(i).getString("url"))
            }
            val scale = json.optString("scale", "fill")
            Playlist(list, scale)
        } catch (e: Exception) {
            null
        }
    }

    /** Отправляет ПК отметку "я жив, показываю такое-то видео". Не критично при ошибке. */
    fun sendHeartbeat(pcIp: String, pcPort: Int, tvName: String, currentVideo: String) {
        try {
            val name = URLEncoder.encode(tvName, "UTF-8")
            val video = URLEncoder.encode(currentVideo, "UTF-8")
            val url = URL("http://$pcIp:$pcPort/heartbeat?name=$name&video=$video")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.inputStream.close()
            conn.disconnect()
        } catch (_: Exception) {
            // сеть могла моргнуть — не страшно, попробуем в следующий раз
        }
    }
}
