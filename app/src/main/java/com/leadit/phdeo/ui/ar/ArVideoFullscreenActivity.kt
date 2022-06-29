package com.leadit.phdeo.ui.ar

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.leadit.phdeo.R
import kotlinx.android.synthetic.main.activity_ar_video_fullscreen.*

class ArVideoFullscreenActivity : AppCompatActivity() {

    @SuppressLint("InlinedApi")
    private val hidePart2Runnable = Runnable {
        fullscreen_content.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    lateinit var player:SimpleExoPlayer

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_video_fullscreen)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        hidePart2Runnable.run()

        val url = intent.extras?.getString("video_url")
        val seekTo = intent.extras?.getString("seek_to")
        url?.let {
            setUpPlayer(videoUri = it,time = seekTo!!)
        }


    }

    private fun setUpPlayer(videoUri: String,time: String) {
        player = SimpleExoPlayer.Builder(this).build()
        playerView.setPlayer(player)
        //MARK: set up url
        val mediaItem: MediaItem = MediaItem.fromUri(videoUri)
        player.setMediaItem(mediaItem)
        player.seekTo(time.toLong())
        player.prepare()
        player.play()
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }



}