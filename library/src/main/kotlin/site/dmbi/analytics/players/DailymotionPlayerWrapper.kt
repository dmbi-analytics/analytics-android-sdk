package site.dmbi.analytics.players

import com.dailymotion.android.player.sdk.PlayerWebView
import com.dailymotion.android.player.sdk.events.*
import site.dmbi.analytics.DMBIAnalytics

/**
 * Wrapper for Dailymotion Android Player that automatically tracks video analytics events.
 *
 * Usage:
 * ```kotlin
 * val playerWebView = findViewById<PlayerWebView>(R.id.dailymotionPlayer)
 *
 * val wrapper = DailymotionPlayerWrapper(playerWebView)
 * wrapper.attach(
 *     videoId = "x8abc123",
 *     title = "Video Title",
 *     duration = 180f
 * )
 *
 * // Load video
 * playerWebView.load(videoId = "x8abc123")
 * ```
 */
class DailymotionPlayerWrapper(private val playerWebView: PlayerWebView) {

    private var videoId: String? = null
    private var videoTitle: String? = null
    private var videoDuration: Float? = null
    private var lastReportedQuartile: Int = 0
    private var hasTrackedImpression: Boolean = false
    private var isPlaying: Boolean = false
    private var currentPosition: Double = 0.0
    private var totalDuration: Double = 0.0

    private val eventListener = PlayerWebView.EventListener { event ->
        when (event) {
            is StartEvent -> {
                if (!hasTrackedImpression) {
                    trackImpression()
                    hasTrackedImpression = true
                }
            }
            is PlayEvent -> {
                if (!isPlaying) {
                    trackPlay()
                    isPlaying = true
                }
            }
            is PauseEvent -> {
                if (isPlaying) {
                    trackPause()
                    isPlaying = false
                }
            }
            is EndEvent -> {
                trackComplete()
                isPlaying = false
            }
            is TimeUpdateEvent -> {
                currentPosition = event.time
                checkQuartileProgress()
            }
            is DurationChangeEvent -> {
                totalDuration = event.duration
            }
        }
    }

    /**
     * Attach analytics tracking to the Dailymotion player.
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

        playerWebView.setEventListener(eventListener)
    }

    /**
     * Detach analytics tracking from the player.
     */
    fun detach() {
        playerWebView.setEventListener(null as PlayerWebView.EventListener?)
        videoId = null
        videoTitle = null
    }

    private fun trackImpression() {
        val id = videoId ?: return
        val duration = videoDuration ?: totalDuration.toFloat().takeIf { it > 0 }

        DMBIAnalytics.trackVideoImpression(
            videoId = id,
            title = videoTitle,
            duration = duration
        )
    }

    private fun trackPlay() {
        val id = videoId ?: return
        val duration = videoDuration ?: totalDuration.toFloat().takeIf { it > 0 }

        DMBIAnalytics.trackVideoPlay(
            videoId = id,
            title = videoTitle,
            duration = duration,
            position = currentPosition.toFloat()
        )
    }

    private fun trackPause() {
        val id = videoId ?: return
        val percent = calculatePercent()

        DMBIAnalytics.trackVideoPause(
            videoId = id,
            position = currentPosition.toFloat(),
            percent = percent
        )
    }

    private fun trackComplete() {
        val id = videoId ?: return
        val duration = videoDuration ?: totalDuration.toFloat().takeIf { it > 0 }

        DMBIAnalytics.trackVideoComplete(
            videoId = id,
            duration = duration
        )
    }

    private fun trackQuartile(percent: Int) {
        val id = videoId ?: return
        val duration = videoDuration ?: totalDuration.toFloat().takeIf { it > 0 }

        DMBIAnalytics.trackVideoProgress(
            videoId = id,
            duration = duration,
            position = currentPosition.toFloat(),
            percent = percent
        )
    }

    private fun calculatePercent(): Int {
        val duration = videoDuration ?: totalDuration.toFloat()
        if (duration <= 0) return 0
        return ((currentPosition.toFloat() / duration) * 100).toInt()
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
