package site.dmbi.analytics.players

import com.dailymotion.player.android.sdk.PlayerView
import com.dailymotion.player.android.sdk.listeners.VideoListener
import com.dailymotion.player.android.sdk.listeners.PlayerListener
import site.dmbi.analytics.DMBIAnalytics

/**
 * Wrapper for Dailymotion Android Player that automatically tracks video analytics events.
 *
 * Usage:
 * ```kotlin
 * // Create player with wrapper listeners
 * Dailymotion.createPlayer(
 *     context = context,
 *     playerId = "YOUR_PLAYER_ID",
 *     videoListener = wrapper.videoListener,
 *     playerListener = wrapper.playerListener
 * ) { player ->
 *     playerView.setPlayer(player)
 *     wrapper.attach(videoId = "x8abc123", title = "Video Title")
 *     player.loadContent(videoId = "x8abc123")
 * }
 * ```
 */
class DailymotionPlayerWrapper {

    private var videoId: String? = null
    private var videoTitle: String? = null
    private var videoDuration: Float? = null
    private var lastReportedQuartile: Int = 0
    private var hasTrackedImpression: Boolean = false
    private var isPlaying: Boolean = false
    private var currentPosition: Float = 0f
    private var totalDuration: Float = 0f

    /**
     * VideoListener to be passed to Dailymotion.createPlayer()
     */
    val videoListener = object : VideoListener {
        override fun onVideoStart(playerView: PlayerView) {
            if (!hasTrackedImpression) {
                trackImpression()
                hasTrackedImpression = true
            }
        }

        override fun onVideoEnd(playerView: PlayerView) {
            trackComplete()
            isPlaying = false
        }

        override fun onVideoPlay(playerView: PlayerView) {
            if (!isPlaying) {
                trackPlay()
                isPlaying = true
            }
        }

        override fun onVideoPause(playerView: PlayerView) {
            if (isPlaying) {
                trackPause()
                isPlaying = false
            }
        }

        override fun onVideoTimeChange(playerView: PlayerView, time: Float) {
            currentPosition = time
            checkQuartileProgress()
        }

        override fun onVideoDurationChange(playerView: PlayerView, duration: Float) {
            totalDuration = duration
        }
    }

    /**
     * PlayerListener to be passed to Dailymotion.createPlayer()
     */
    val playerListener = object : PlayerListener {
        override fun onPlayerEnd(playerView: PlayerView) {
            // Player ended
        }
    }

    /**
     * Attach analytics tracking with video metadata.
     *
     * @param videoId Dailymotion video ID
     * @param title Optional video title
     * @param duration Optional video duration in seconds
     */
    fun attach(videoId: String, title: String? = null, duration: Float? = null) {
        this.videoId = videoId
        this.videoTitle = title
        this.videoDuration = duration
        this.lastReportedQuartile = 0
        this.hasTrackedImpression = false
        this.isPlaying = false
    }

    /**
     * Detach analytics tracking.
     */
    fun detach() {
        videoId = null
        videoTitle = null
    }

    private fun trackImpression() {
        val id = videoId ?: return
        val duration = videoDuration ?: totalDuration.takeIf { it > 0 }

        DMBIAnalytics.trackVideoImpression(
            videoId = id,
            title = videoTitle,
            duration = duration
        )
    }

    private fun trackPlay() {
        val id = videoId ?: return
        val duration = videoDuration ?: totalDuration.takeIf { it > 0 }

        DMBIAnalytics.trackVideoPlay(
            videoId = id,
            title = videoTitle,
            duration = duration,
            position = currentPosition
        )
    }

    private fun trackPause() {
        val id = videoId ?: return
        val percent = calculatePercent()

        DMBIAnalytics.trackVideoPause(
            videoId = id,
            position = currentPosition,
            percent = percent
        )
    }

    private fun trackComplete() {
        val id = videoId ?: return
        val duration = videoDuration ?: totalDuration.takeIf { it > 0 }

        DMBIAnalytics.trackVideoComplete(
            videoId = id,
            duration = duration
        )
    }

    private fun trackQuartile(percent: Int) {
        val id = videoId ?: return
        val duration = videoDuration ?: totalDuration.takeIf { it > 0 }

        DMBIAnalytics.trackVideoProgress(
            videoId = id,
            duration = duration,
            position = currentPosition,
            percent = percent
        )
    }

    private fun calculatePercent(): Int {
        val duration = videoDuration ?: totalDuration
        if (duration <= 0) return 0
        return ((currentPosition / duration) * 100).toInt()
    }

    private fun checkQuartileProgress() {
        val percent = calculatePercent()
        val quartiles = listOf(25, 50, 75, 100)

        for (quartile in quartiles) {
            if (percent >= quartile && lastReportedQuartile < quartile) {
                trackQuartile(quartile)
                lastReportedQuartile = quartile
            }
        }
    }
}
