package com.example.addsplayer

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.FrameLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import android.content.Intent
import android.view.KeyEvent
import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ADSPlayer"
        private val PLAYLIST_DISPLAY_NAMES = mapOf(
            "CoffeePoint"  to "Кавопоінт",
            "CoffeeShop"   to "Кав'ярня",
            "Gastronomy"   to "Гастроном",
            "Pizzeria"     to "Піцерія",
            "Additional1"  to "Додатковий 1",
            "Additional2"  to "Додатковий 2",
            "Additional3"  to "Додатковий 3"
        )
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val videoGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_VIDEO] ?: false
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }

        if (videoGranted) {
            Log.d(TAG, "Разрешение на видео получено")
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            lifecycleScope.launch {
                try {
                    updateMediaContent()
                    loadPlaylist()
                } catch (e: Exception) {
                    //showError("settings", "Помилка після збереження налаштувань", e)
                }
            }
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var errorTextView: TextView

    private val client = OkHttpClient()
    private val prefs by lazy { SettingsActivity.getPrefs(this) }

    private val server get() = prefs.getString(SettingsActivity.KEY_SERVER, BuildConfig.DEFAULT_SERVER) ?: BuildConfig.DEFAULT_SERVER
    private val login  get() = prefs.getString(SettingsActivity.KEY_LOGIN, BuildConfig.DEFAULT_LOGIN) ?: BuildConfig.DEFAULT_LOGIN
    private val password get() = prefs.getString(SettingsActivity.KEY_PASSWORD, BuildConfig.DEFAULT_PASSWORD) ?: BuildConfig.DEFAULT_PASSWORD
    private val posId  get() = prefs.getString(SettingsActivity.KEY_POS_ID, BuildConfig.DEFAULT_POS_ID) ?: BuildConfig.DEFAULT_POS_ID
    private val playlistType get() = prefs.getString(SettingsActivity.KEY_PLAYLIST_TYPE, "Кавопоінт") ?: "Кавопоінт"

    private val mediaDirectory: File by lazy {
        File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "media").apply { mkdirs() }
    }

    private var mediaList = mutableListOf<MediaContent>()

    // ─── helpers ────────────────────────────────────────────────────────────

    private fun showError(source: String, message: String, e: Throwable? = null) {
        val text = "[$source] $message${e?.let { "\n${it::class.simpleName}: ${it.message}" } ?: ""}"
        Log.e(TAG, text, e)
        runOnUiThread {
            errorTextView.text = text
            errorTextView.visibility = android.view.View.VISIBLE
        }
    }

    private fun clearError() {
        runOnUiThread { errorTextView.visibility = android.view.View.GONE }
    }

    // ─── lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        player = ExoPlayer.Builder(this).build()

        val playerView = PlayerView(this).apply {
            useController = false
            player = this@MainActivity.player
        }

        errorTextView = TextView(this).apply {
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            setTextColor(Color.RED)
            textSize = 14f
            setPadding(24, 24, 24, 24)
            visibility = android.view.View.GONE
        }

        val rootLayout = FrameLayout(this).apply {
            addView(playerView)
            addView(errorTextView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM })
        }

        setContentView(rootLayout)

        playerView.setOnLongClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
            true
        }

        lifecycleScope.launch {
            loadPlaylist()

            while (true) {
                try {
                    updateMediaContent()
                    loadPlaylist()
                } catch (e: Exception) {
                    //showError("main loop", "Необроблена помилка циклу", e)
                }
                delay(3 * 3600_000L) // 3 години
            }
        }
    }

    // ─── permissions ─────────────────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.READ_MEDIA_VIDEO
            permissions += Manifest.permission.POST_NOTIFICATIONS
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_DPAD_UP -> {
                settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // ─── media update ────────────────────────────────────────────────────────

    private suspend fun updateMediaContent() {
        Log.d(TAG, "updateMediaContent: старт (PlaylistType: $playlistType)")
        try {
            updateMediaCatalog()
            updateMediaFiles()
            cleanupObsoleteFiles()
            clearError()
            Log.d(TAG, "updateMediaContent: завершено успішно")
        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun updateMediaCatalog() {
        Log.d(TAG, "updateMediaCatalog: запит до $server")
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(server)
                    .addHeader("Authorization", SettingsActivity.createBasicAuth(login, password))
                    .addHeader("POSID", posId)
                    .addHeader("PlaylistType", playlistType)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext
                }

                val body = response.body?.string() ?: return@withContext

                val jsonArray = JSONArray(body)

                mediaList.clear()

                for (i in 0 until jsonArray.length()) {
                    try {
                        val obj = jsonArray.getJSONObject(i)
                        mediaList.add(
                            MediaContent(
                                code = obj.getString("Код"),
                                deleted = obj.optBoolean("ПометкаУдаления"),
                                playing = obj.optBoolean("ВідтворюватиАндроидТВ")
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "updateMediaCatalog: помилка парсингу $i", e)
                    }
                }

                if (mediaList.isEmpty()) {
                    val displayName = PLAYLIST_DISPLAY_NAMES[playlistType] ?: playlistType
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "⚠️ Плейлист \"$displayName\" - відсутні відео, відображаються попередні відео",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {
                throw e
            }
        }
    }

    private suspend fun updateMediaFiles() {
        Log.d(TAG, "updateMediaFiles: обробка ${mediaList.size} файлів")
        withContext(Dispatchers.IO) {
            for (media in mediaList) {
                val file = File(mediaDirectory, "${media.code}.mp4")

                if (file.exists() && (media.deleted || !media.playing)) {
                    file.delete()
                    continue
                }

                if (!file.exists() && !media.deleted && media.playing) {
                    val fileUrl = server + media.code

                    try {
                        val request = Request.Builder()
                            .url(fileUrl)
                            .addHeader("Authorization", SettingsActivity.createBasicAuth(login, password))
                            .addHeader("POSID", posId)
                            .addHeader("playlistType", playlistType)       // ← НОВИЙ ХЕДЕР
                            .build()

                        val response = client.newCall(request).execute()
                        if (!response.isSuccessful) continue

                        val body = response.body ?: continue

                        val tempFile = File(mediaDirectory, "${media.code}.tmp")
                        FileOutputStream(tempFile).use { output ->
                            body.byteStream().use { input -> input.copyTo(output) }
                        }

                        tempFile.renameTo(file)
                    } catch (e: Exception) {
                        Log.e(TAG, "Помилка завантаження ${media.code}", e)
                    }
                }
            }
        }
    }

    private fun isValidMp4(file: File): Boolean {
        return try {
            val header = ByteArray(12)
            file.inputStream().use { it.read(header, 0, 12) }
            header.size >= 8 &&
                    header[4] == 0x66.toByte() &&
                    header[5] == 0x74.toByte() &&
                    header[6] == 0x79.toByte() &&
                    header[7] == 0x70.toByte()
        } catch (e: Exception) {
            false
        }
    }

    private fun cleanupObsoleteFiles() {
        val currentCodes = mediaList
            .filter { !it.deleted && it.playing }
            .map { it.code }
            .toSet()

        val filesToDelete = mediaDirectory.listFiles()?.filter { file ->
            file.extension == "mp4" && file.nameWithoutExtension !in currentCodes
        } ?: emptyList()

        if (filesToDelete.isNotEmpty()) {
            Log.d(TAG, "Видаляємо ${filesToDelete.size} застарілих файлів")
            filesToDelete.forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "Видалено: ${file.name}")
                } else {
                    Log.w(TAG, "Не вдалося видалити: ${file.name}")
                }
            }
        }
    }

    private fun loadPlaylist() {
        val allFiles = mediaDirectory.listFiles()?.filter { it.extension == "mp4" } ?: emptyList()
        val mediaItems = allFiles.sortedBy { it.name }.map { MediaItem.fromUri(Uri.fromFile(it)) }

        if (mediaItems.isEmpty()) return

        player.setMediaItems(mediaItems)
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()
        if (!player.isPlaying) player.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}

data class MediaContent(
    val code: String,
    val deleted: Boolean,
    val playing: Boolean
)