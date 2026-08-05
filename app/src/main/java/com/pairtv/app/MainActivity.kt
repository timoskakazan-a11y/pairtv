package com.pairtv.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var listView: ListView
    private lateinit var rescanButton: Button

    private var pcs: List<DiscoveredPc> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        listView = findViewById(R.id.pcListView)
        rescanButton = findViewById(R.id.rescanButton)

        rescanButton.setOnClickListener { startScan() }
        listView.setOnItemClickListener { _, _, position, _ ->
            connectTo(pcs[position])
        }

        startScan()
    }

    private fun startScan() {
        progressBar.visibility = View.VISIBLE
        statusText.text = getString(R.string.title_searching)
        listView.adapter = null

        lifecycleScope.launch {
            val found = Discovery.scan()
            pcs = found
            progressBar.visibility = View.GONE
            if (found.isEmpty()) {
                statusText.text = getString(R.string.title_none)
            } else {
                statusText.text = getString(R.string.title_found)
                val labels = found.map { "${it.name}  (${it.ip})  •  ${it.videoCount} видео" }
                listView.adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_list_item_1,
                    android.R.id.text1,
                    labels
                )
            }
        }
    }

    private fun connectTo(pc: DiscoveredPc) {
        statusText.text = "Подключение к ${pc.name}…"
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val urls = withContext(Dispatchers.IO) { fetchPlaylist(pc) }
            progressBar.visibility = View.GONE
            if (urls.isNullOrEmpty()) {
                Toast.makeText(this@MainActivity, "На ПК нет добавленных видео", Toast.LENGTH_LONG).show()
                return@launch
            }
            val intent = Intent(this@MainActivity, PlayerActivity::class.java)
            intent.putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(urls))
            startActivity(intent)
        }
    }

    private fun fetchPlaylist(pc: DiscoveredPc): List<String>? {
        return try {
            val url = URL("http://${pc.ip}:${pc.httpPort}/playlist.json")
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
            list
        } catch (e: Exception) {
            null
        }
    }
}
