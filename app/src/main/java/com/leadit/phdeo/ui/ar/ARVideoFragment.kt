package com.leadit.phdeo.ui.ar

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.core.animation.doOnStart
import androidx.core.graphics.rotationMatrix
import androidx.core.graphics.transform
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.rendering.ExternalTexture
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.leadit.phdeo.R
import com.leadit.phdeo.helpers.VideoAnchorNode
import com.leadit.phdeo.helpers.VideoScaleType
import kotlinx.android.synthetic.main.ar_managment_view.view.*
import kotlinx.coroutines.*
import java.io.IOException

class ARVideoFragment : ArFragment() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var externalTexture: ExternalTexture
    private lateinit var videoRenderable: ModelRenderable
    private lateinit var videoAnchorNode: VideoAnchorNode
    private lateinit var arManagmentView: View

    private var activeAugmentedImage: AugmentedImage? = null
    var mokData = ARData(
        images =
        mapOf(
            "img1.png" to "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "img2.png" to "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            "img3.png" to "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            "img4.png" to "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4"
        )
    );

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaPlayer = MediaPlayer()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        planeDiscoveryController.hide()
        planeDiscoveryController.setInstructionView(null)
        arSceneView.planeRenderer.isEnabled = false
        arSceneView.isLightEstimationEnabled = false

        initializeSession()
        createArScene()

        arManagmentView = layoutInflater.inflate(R.layout.ar_managment_view,container,false)
        (view as ViewGroup).addView(arManagmentView)
        setAdditionalManagmentParam()

        return view
    }

    private fun setAdditionalManagmentParam(){
        arManagmentView.visibility = View.GONE
        arManagmentView.fullScreen.setOnClickListener {
            goToDetailScreen()
        }
        arManagmentView.fullScreenText.setOnClickListener {
            goToDetailScreen()
        }
    }

    private fun goToDetailScreen(){
        println( activeAugmentedImage?.name)
        val i = Intent(requireContext(),ArVideoFullscreenActivity::class.java)
            .putExtra("video_url",activeAugmentedImage?.name)
            .putExtra("seek_to",mediaPlayer.currentPosition.toString())
        startActivity(i)
    }

    override fun getSessionConfiguration(session: Session): Config {

        fun loadAugmentedImageBitmap(imageName: String): Bitmap =
            requireContext().assets.open(imageName).use { return BitmapFactory.decodeStream(it) }

        fun setupAugmentedImageDatabase(config: Config, session: Session): Boolean {
            try {
                config.augmentedImageDatabase = AugmentedImageDatabase(session).also { db ->
                    mokData.images.forEach { image, video ->
                        db.addImage(video, loadAugmentedImageBitmap(image))
                    }
                }
                return true
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Could not add bitmap to augmented image database", e)
            } catch (e: IOException) {
                Log.e(TAG, "IO exception loading augmented image bitmap.", e)
            }
            return false
        }

        return super.getSessionConfiguration(session).also {
            it.lightEstimationMode = Config.LightEstimationMode.DISABLED
            it.focusMode = Config.FocusMode.AUTO

            if (!setupAugmentedImageDatabase(it, session)) {
                Toast.makeText(
                    requireContext(),
                    "Could not setup augmented image database",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun createArScene() {
        // Create an ExternalTexture for displaying the contents of the video.
        externalTexture = ExternalTexture().also {
            mediaPlayer.setSurface(it.surface)
        }

        // Create a renderable with a material that has a parameter of type 'samplerExternal' so that
        // it can display an ExternalTexture.
        ModelRenderable.builder()
            .setSource(requireContext(), R.raw.augmented_video_model)
            .build()
            .thenAccept { renderable ->
                videoRenderable = renderable
                renderable.isShadowCaster = false
                renderable.isShadowReceiver = false
                renderable.material.setExternalTexture("videoTexture", externalTexture)
            }
            .exceptionally { throwable ->
                Log.e(TAG, "Could not create ModelRenderable", throwable)
                return@exceptionally null
            }

        videoAnchorNode = VideoAnchorNode().apply {
            setParent(arSceneView.scene)
        }
    }

    /**
     * In this case, we want to support the playback of one video at a time.
     * Therefore, if ARCore loses current active image FULL_TRACKING we will pause the video.
     * If the same image gets FULL_TRACKING back, the video will resume.
     * If a new image will become active, then the corresponding video will start from scratch.
     */
    override fun onUpdate(frameTime: FrameTime) {
        val frame = arSceneView.arFrame ?: return

        val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

        // If current active augmented image isn't tracked anymore and video playback is started - pause video playback
        val nonFullTrackingImages =
            updatedAugmentedImages.filter { it.trackingMethod != AugmentedImage.TrackingMethod.FULL_TRACKING }
        activeAugmentedImage?.let { activeAugmentedImage ->
            if (isArVideoPlaying() && nonFullTrackingImages.any { it.index == activeAugmentedImage.index }) {
                arManagmentView.visibility = View.GONE
                pauseArVideo()
            }
        }

        val fullTrackingImages =
            updatedAugmentedImages.filter { it.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING }
        if (fullTrackingImages.isEmpty()) return
        println("size of tracked images ${fullTrackingImages.size}")
        // If current active augmented image is tracked but video playback is paused - resume video playback
        activeAugmentedImage?.let { activeAugmentedImage ->
            if (fullTrackingImages.any { it.index == activeAugmentedImage.index }) {
                if (!isArVideoPlaying()) {
                    arManagmentView.visibility = View.VISIBLE
                    resumeArVideo()
                }
                return
            }
        }

        // Otherwise - make the first tracked image active and start video playback
        fullTrackingImages.firstOrNull()?.let { augmentedImage ->
            try {
                playbackArVideo(augmentedImage)
            } catch (e: Exception) {
                Log.e(TAG, "Could not play video [${augmentedImage.name}]", e)
            }
        }
    }

    private fun isArVideoPlaying() = mediaPlayer.isPlaying

    private fun pauseArVideo() {
        videoAnchorNode.renderable = null
        mediaPlayer.pause()
    }

    private fun resumeArVideo() {
        mediaPlayer.start()
        fadeInVideo()
    }

    private fun dismissArVideo() {
        videoAnchorNode.anchor?.detach()
        videoAnchorNode.renderable = null
        activeAugmentedImage = null
        mediaPlayer.reset()
    }

    private fun playbackArVideo(augmentedImage: AugmentedImage) {
        Log.d(TAG, "playbackVideo = ${augmentedImage.name}")
        val context = requireContext()
        activeAugmentedImage = augmentedImage

        runBlocking {
//            withContext(context = Dispatchers.Unconfined) {
//
//                val metadataRetriever = FFmpegMediaMetadataRetriever()
//
//                metadataRetriever.setDataSource(augmentedImage.name)
//
//                val videoWidth =
//                    metadataRetriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
//                        ?.toFloatOrNull() ?: 0f
//                val videoHeight =
//                    metadataRetriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
//                        ?.toFloatOrNull() ?: 0f
//                val videoRotation =
//                    metadataRetriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
//                        ?.toFloatOrNull() ?: 0f
//            }
            // Account for video rotation, so that scale logic math works properly
            val imageSize = RectF(0f, 0f, augmentedImage.extentX, augmentedImage.extentZ)
                .transform(rotationMatrix(0f))

            val videoScaleType = VideoScaleType.CenterCrop

            videoAnchorNode.setVideoProperties(
                videoWidth = 480f,
                videoHeight = 360f,
                videoRotation = 0f,
                imageWidth = imageSize.width(),
                imageHeight = imageSize.height(),
                videoScaleType = videoScaleType
            )

            // Update the material parameters
            videoRenderable.material.setFloat2(
                MATERIAL_IMAGE_SIZE,
                imageSize.width(),
                imageSize.height()
            )
            videoRenderable.material.setFloat2(MATERIAL_VIDEO_SIZE, 480f, 360f)
            videoRenderable.material.setBoolean(MATERIAL_VIDEO_CROP, VIDEO_CROP_ENABLED)

            mediaPlayer.reset()
            mediaPlayer.setDataSource(augmentedImage.name)

            mediaPlayer.isLooping = true
            mediaPlayer.prepareAsync()
            mediaPlayer.start()

            videoAnchorNode.anchor?.detach()
            videoAnchorNode.anchor = augmentedImage.createAnchor(augmentedImage.centerPose)

            externalTexture.surfaceTexture.setOnFrameAvailableListener {
                it.setOnFrameAvailableListener(null)
                fadeInVideo()
            }


        }
    }


    private fun fadeInVideo() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 100L
            interpolator = LinearInterpolator()
            addUpdateListener { v ->
                videoRenderable.material.setFloat(MATERIAL_VIDEO_ALPHA, v.animatedValue as Float)
            }
            doOnStart { videoAnchorNode.renderable = videoRenderable }
            start()
        }
    }

    override fun onPause() {
        super.onPause()
        dismissArVideo()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }

    companion object {
        private const val TAG = "ArVideoFragment"

        private const val TEST_IMAGE_1 = "test_image_1.jpg"
        private const val TEST_IMAGE_2 = "test_image_2.jpg"
        private const val TEST_IMAGE_3 = "test_image_3.jpg"
        private const val TEST_IMAGE_4 = "test_image_4.jpeg"

        private const val TEST_VIDEO_1 = "test_video_1.mp4"
        private const val TEST_VIDEO_2 = "test_video_2.mp4"
        private const val TEST_VIDEO_3 = "test_video_3.mp4"
        private const val TEST_VIDEO_4 = "test_video_4.mp4"

        private const val VIDEO_CROP_ENABLED = true

        private const val MATERIAL_IMAGE_SIZE = "imageSize"
        private const val MATERIAL_VIDEO_SIZE = "videoSize"
        private const val MATERIAL_VIDEO_CROP = "videoCropEnabled"
        private const val MATERIAL_VIDEO_ALPHA = "videoAlpha"
    }
}

class ARData {

    var images: Map<String, String>

    constructor(images: Map<String, String>) {
        this.images = images
    }
}
