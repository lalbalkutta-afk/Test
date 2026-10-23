package com.lalitbadoga.mediaplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.InputStream
import java.util.Locale
import kotlin.math.roundToLong

/**
 * First prototype of Lalit Media Player.
 * External SRT subtitles can be corrected with an offset and a linear drift-rate adjustment.
 * Embedded subtitles are passed through unchanged; Media3 does not expose a generic timestamp
 * rewrite control for embedded subtitle tracks.
 */
class MainActivity : Activity() {
    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private lateinit var offsetLabel: TextView
    private lateinit var driftLabel: TextView
    private lateinit var offsetBar: SeekBar
    private lateinit var driftBar: SeekBar
    private var player: ExoPlayer? = null
    private var videoUri: Uri? = null
    private var subtitleUri: Uri? = null
    private var offsetMs = 0
    // SeekBar position 0..2000 maps to -1000..+1000 parts per million (0.1% max).
    private var driftPpm = 0
    private var isRebuilding = false

    companion object {
        private const val PICK_VIDEO = 1001
        private const val PICK_SUBTITLE = 1002
        private const val PREFS = "player_settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 20, 24)
        window.navigationBarColor = Color.rgb(16, 20, 24)
        buildUi()
        offsetMs = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("offset_ms", 0)
        driftPpm = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("drift_ppm", 0)
        offsetBar.progress = (offsetMs + 10_000).coerceIn(0, 20_000)
        driftBar.progress = (driftPpm + 1000).coerceIn(0, 2000)
        updateCorrectionLabels()
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    status.text = "Playback error: ${error.errorCodeName}. Try another file or audio track."
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) status.text = "Ready · ${videoUri?.lastPathSegment ?: "video"}"
                }
            })
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 20, 24))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        val title = TextView(this).apply {
            text = "LALIT MEDIA PLAYER"
            setTextColor(Color.WHITE); textSize = 22f
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        root.addView(title)
        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
            contentDescription = "Video playback. Use the remote for playback controls and track settings."
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(playerView)
        status = TextView(this).apply {
            text = "Choose a video from USB or internal storage."
            setTextColor(Color.LTGRAY); textSize = 13f
            setPadding(dp(4), dp(6), dp(4), dp(4))
        }
        root.addView(status)
        val fileRow = row(root)
        addButton(fileRow, "Open video / USB") { pickVideo() }
        addButton(fileRow, "External subtitles (.srt)") { pickSubtitle() }

        val offsetRow = row(root)
        offsetLabel = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; minWidth = dp(150) }
        offsetRow.addView(offsetLabel)
        addButton(offsetRow, "−250 ms") { changeOffset(-250) }
        addButton(offsetRow, "+250 ms") { changeOffset(250) }
        offsetBar = SeekBar(this).apply {
            max = 20_000
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            contentDescription = "External subtitle time offset"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) { offsetMs = progress - 10_000; updateCorrectionLabels() }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) { saveCorrectionAndReload() }
            })
        }
        offsetRow.addView(offsetBar)

        val driftRow = row(root)
        driftLabel = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; minWidth = dp(150) }
        driftRow.addView(driftLabel)
        addButton(driftRow, "−25 ppm") { changeDrift(-25) }
        addButton(driftRow, "+25 ppm") { changeDrift(25) }
        driftBar = SeekBar(this).apply {
            max = 2000
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            contentDescription = "External subtitle drift correction"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) { driftPpm = progress - 1000; updateCorrectionLabels() }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) { saveCorrectionAndReload() }
            })
        }
        driftRow.addView(driftBar)
        val hint = TextView(this).apply {
            text = "Correction applies to external SRT only. Positive offset delays subtitles. Drift adjusts timing progressively. Embedded MKV subtitle tracks remain unchanged."
            setTextColor(Color.rgb(170, 185, 192)); textSize = 12f
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        root.addView(hint)
        setContentView(root)
    }

    private fun row(root: LinearLayout): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(this)
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = label
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 12f
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
        }
        parent.addView(b)
    }

    private fun pickVideo() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i, PICK_VIDEO)
    }

    private fun pickSubtitle() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i, PICK_SUBTITLE)
    }

    @Deprecated("Legacy result API kept for broad Android TV compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        when (requestCode) {
            PICK_VIDEO -> {
                videoUri = uri
                if (subtitleUri == null) playVideo()
                else playVideo()
            }
            PICK_SUBTITLE -> {
                subtitleUri = uri
                if (videoUri != null) playVideo() else status.text = "Subtitle selected. Now choose a video."
            }
        }
    }

    private fun playVideo() {
        val uri = videoUri ?: return
        val exo = player ?: return
        val position = exo.currentPosition.coerceAtLeast(0L)
        val wasPlaying = exo.isPlaying
        try {
            val builder = MediaItem.Builder().setUri(uri)
            val sub = subtitleUri
            if (sub != null) {
                val corrected = makeCorrectedSrt(sub)
                if (corrected != null) {
                    builder.setSubtitleConfigurations(listOf(
                        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(corrected))
                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage("und")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    ))
                } else {
                    status.text = "Could not read subtitle as SRT. Try an .srt file."
                }
            }
            exo.setMediaItem(builder.build())
            exo.prepare()
            if (position > 0) exo.seekTo(position)
            exo.playWhenReady = wasPlaying
            status.text = "Loaded. Use the player's settings menu to choose embedded audio/subtitle tracks."
        } catch (e: Exception) {
            status.text = "Could not open file: ${e.message ?: "unsupported format"}"
        }
    }

    private fun makeCorrectedSrt(uri: Uri): File? {
        return try {
            val text = contentResolver.openInputStream(uri)?.use { input -> input.readBytes().toString(Charsets.UTF_8) } ?: return null
            // Strict SRT timestamp transformation: corrected = source * (1 + ppm/1e6) + offset.
            val regex = Regex("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})")
            val transformed = regex.replace(text) { match ->
                val g = match.groupValues
                val original = (((g[1].toLong() * 60 + g[2].toLong()) * 60 + g[3].toLong()) * 1000 + g[4].toLong())
                val adjusted = (original * (1.0 + driftPpm / 1_000_000.0)).roundToLong() + offsetMs
                val t = adjusted.coerceAtLeast(0L)
                val hours = t / 3_600_000
                val minutes = (t % 3_600_000) / 60_000
                val seconds = (t % 60_000) / 1000
                val millis = t % 1000
                String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
            }
            val out = File(cacheDir, "external_subtitle_corrected.srt")
            out.writeText(transformed, Charsets.UTF_8)
            out
        } catch (_: Exception) { null }
    }

    private fun changeOffset(delta: Int) {
        offsetMs = (offsetMs + delta).coerceIn(-10_000, 10_000)
        offsetBar.progress = offsetMs + 10_000
        updateCorrectionLabels(); saveCorrectionAndReload()
    }

    private fun changeDrift(delta: Int) {
        driftPpm = (driftPpm + delta).coerceIn(-1000, 1000)
        driftBar.progress = driftPpm + 1000
        updateCorrectionLabels(); saveCorrectionAndReload()
    }

    private fun updateCorrectionLabels() {
        if (::offsetLabel.isInitialized) offsetLabel.text = "Offset: ${if (offsetMs >= 0) "+" else ""}${offsetMs} ms"
        if (::driftLabel.isInitialized) driftLabel.text = "Drift: ${if (driftPpm >= 0) "+" else ""}${driftPpm} ppm"
    }

    private fun saveCorrectionAndReload() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("offset_ms", offsetMs).putInt("drift_ppm", driftPpm).apply()
        if (subtitleUri != null && videoUri != null && !isRebuilding) {
            isRebuilding = true
            val position = player?.currentPosition ?: 0L
            playVideo()
            player?.seekTo(position)
            isRebuilding = false
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToLong().toInt()

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
