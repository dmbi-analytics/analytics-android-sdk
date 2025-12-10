package site.dmbi.analytics

import android.content.Context
import android.net.Uri
import android.util.Log
import site.dmbi.analytics.models.ScreenMetadata
import site.dmbi.analytics.models.UTMParameters

/**
 * DMBI Analytics SDK for Android
 * Tracks user activity, screen views, video engagement, and push notifications
 */
object DMBIAnalytics {
    private var config: DMBIConfiguration? = null
    private var sessionManager: SessionManager? = null
    private var networkQueue: NetworkQueue? = null
    private var offlineStore: OfflineStore? = null
    private var eventTracker: EventTracker? = null
    private var heartbeatManager: HeartbeatManager? = null
    private var lifecycleTracker: LifecycleTracker? = null

    private var isConfigured = false

    private const val TAG = "DMBIAnalytics"

    // MARK: - Configuration

    /**
     * Configure the SDK with site ID and endpoint
     * @param context Application context
     * @param siteId Your site identifier (e.g., "hurriyet-android")
     * @param endpoint Analytics endpoint URL (e.g., "https://realtime.dmbi.site/e")
     */
    @JvmStatic
    fun configure(context: Context, siteId: String, endpoint: String) {
        val config = DMBIConfiguration(siteId = siteId, endpoint = endpoint)
        configure(context, config)
    }

    /**
     * Configure the SDK with a custom configuration
     * @param context Application context
     * @param config DMBIConfiguration instance
     */
    @JvmStatic
    fun configure(context: Context, config: DMBIConfiguration) {
        if (isConfigured) {
            if (config.debugLogging) {
                Log.w(TAG, "Already configured. Ignoring duplicate configuration.")
            }
            return
        }

        this.config = config
        val appContext = context.applicationContext

        // Initialize components
        sessionManager = SessionManager(appContext, config.sessionTimeout)

        offlineStore = OfflineStore(
            context = appContext,
            maxEvents = config.maxOfflineEvents,
            retentionDays = config.offlineRetentionDays,
            debugLogging = config.debugLogging
        )

        networkQueue = NetworkQueue(
            endpoint = config.endpoint,
            batchSize = config.batchSize,
            flushInterval = config.flushInterval,
            maxRetryCount = config.maxRetryCount,
            debugLogging = config.debugLogging
        ).also {
            it.setOfflineStore(offlineStore!!)
        }

        eventTracker = EventTracker(
            context = appContext,
            config = config,
            sessionManager = sessionManager!!,
            networkQueue = networkQueue!!
        )

        heartbeatManager = HeartbeatManager(config.heartbeatInterval).also {
            it.setTracker(eventTracker!!)
        }

        lifecycleTracker = LifecycleTracker(config.sessionTimeout).also {
            it.configure(
                tracker = eventTracker!!,
                sessionManager = sessionManager!!,
                heartbeatManager = heartbeatManager!!
            )
        }

        // Start heartbeat
        heartbeatManager?.start()

        isConfigured = true

        if (config.debugLogging) {
            Log.d(TAG, "Configured with siteId: ${config.siteId}")
        }
    }

    // MARK: - Screen Tracking

    /**
     * Track a screen view (simple version)
     * @param name Screen name (e.g., "ArticleDetail", "Home")
     * @param url Screen URL (e.g., "app://article/123")
     * @param title Optional screen title
     */
    @JvmStatic
    @JvmOverloads
    fun trackScreen(name: String, url: String, title: String? = null) {
        eventTracker?.trackScreen(name, url, title, null)
    }

    /**
     * Track a screen view with article metadata
     * @param name Screen name (e.g., "ArticleDetail", "Home")
     * @param url Screen URL (e.g., "app://article/123")
     * @param title Optional screen title
     * @param metadata Article metadata (authors, section, keywords, etc.)
     */
    @JvmStatic
    fun trackScreen(name: String, url: String, title: String?, metadata: ScreenMetadata) {
        eventTracker?.trackScreen(name, url, title, metadata)
    }

    // MARK: - Deep Link & UTM Tracking

    /**
     * Handle a deep link URI and extract UTM parameters
     * Call this when your app opens from a deep link
     * @param uri The deep link URI
     */
    @JvmStatic
    fun handleDeepLink(uri: Uri) {
        eventTracker?.handleDeepLink(uri)
    }

    /**
     * Handle a deep link URL string and extract UTM parameters
     * @param url The deep link URL string
     */
    @JvmStatic
    fun handleDeepLink(url: String) {
        try {
            eventTracker?.handleDeepLink(Uri.parse(url))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse deep link URL: $url")
        }
    }

    /**
     * Set UTM parameters manually
     * @param utm UTM parameters
     */
    @JvmStatic
    fun setUTMParameters(utm: UTMParameters) {
        eventTracker?.setUTMParameters(utm)
    }

    /**
     * Set referrer source manually
     * @param referrer Referrer identifier (e.g., "facebook", "twitter", "push_notification")
     */
    @JvmStatic
    fun setReferrer(referrer: String) {
        eventTracker?.setReferrer(referrer)
    }

    // MARK: - Video Tracking

    /**
     * Track video impression (video appeared on screen)
     */
    @JvmStatic
    @JvmOverloads
    fun trackVideoImpression(videoId: String, title: String? = null, duration: Float? = null) {
        eventTracker?.trackVideoImpression(videoId, title, duration)
    }

    /**
     * Track video play start
     */
    @JvmStatic
    @JvmOverloads
    fun trackVideoPlay(videoId: String, title: String? = null, duration: Float? = null, position: Float? = null) {
        eventTracker?.trackVideoPlay(videoId, title, duration, position)
    }

    /**
     * Track video progress (25%, 50%, 75%, etc.)
     */
    @JvmStatic
    @JvmOverloads
    fun trackVideoProgress(videoId: String, duration: Float? = null, position: Float? = null, percent: Int) {
        eventTracker?.trackVideoProgress(videoId, duration, position, percent)
    }

    /**
     * Track video pause
     */
    @JvmStatic
    @JvmOverloads
    fun trackVideoPause(videoId: String, position: Float? = null, percent: Int? = null) {
        eventTracker?.trackVideoPause(videoId, position, percent)
    }

    /**
     * Track video completion
     */
    @JvmStatic
    @JvmOverloads
    fun trackVideoComplete(videoId: String, duration: Float? = null) {
        eventTracker?.trackVideoComplete(videoId, duration)
    }

    // MARK: - Push Notification Tracking

    /**
     * Track push notification received
     */
    @JvmStatic
    @JvmOverloads
    fun trackPushReceived(notificationId: String? = null, title: String? = null, campaign: String? = null) {
        eventTracker?.trackPushReceived(notificationId, title, campaign)
    }

    /**
     * Track push notification opened
     */
    @JvmStatic
    @JvmOverloads
    fun trackPushOpened(notificationId: String? = null, title: String? = null, campaign: String? = null) {
        eventTracker?.trackPushOpened(notificationId, title, campaign)
    }

    // MARK: - User State

    /**
     * Set user login state
     * @param loggedIn Whether the user is logged in
     */
    @JvmStatic
    fun setLoggedIn(loggedIn: Boolean) {
        eventTracker?.setLoggedIn(loggedIn)
    }

    // MARK: - Custom Events

    /**
     * Track a custom event
     * @param name Event name
     * @param properties Optional event properties
     */
    @JvmStatic
    @JvmOverloads
    fun trackEvent(name: String, properties: Map<String, Any>? = null) {
        eventTracker?.trackCustomEvent(name, properties)
    }

    // MARK: - Control

    /**
     * Flush all pending events immediately
     */
    @JvmStatic
    fun flush() {
        eventTracker?.flush()
    }
}
