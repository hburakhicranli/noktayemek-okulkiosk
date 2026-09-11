package com.bizim.kiosk

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-bleed web "app": no address bar, no chrome, no system bars -- just the page.
 *
 * Also owns the idle screensaver: after `config/screensaver.idleTimeoutSec` (default 10 min)
 * with no touch/key input, a fleet-wide slideshow (`config/screensaver.imageUrls`) covers the
 * page. A USB NFC reader behaves like a keyboard wedge -- it "types" the card id followed by
 * Enter -- so any completed scan while the screensaver is up dismisses it. Plain touches
 * don't: waking it is meant to require a badge, not just a passerby's tap.
 */
class WebKioskActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var screensaverContainer: FrameLayout
    private lateinit var screensaverImage: ImageView

    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { showScreensaver() }
    private var screensaverVisible = false
    private var slideshowJob: Job? = null
    private var imageUrls: List<String> = emptyList()
    private var idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS
    private var scanBuffer = StringBuilder()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = WebViewClient()
        }
        screensaverImage = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        screensaverContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = android.view.View.GONE
            addView(screensaverImage, MATCH_PARENT, MATCH_PARENT)
        }
        val root = FrameLayout(this).apply {
            addView(webView, MATCH_PARENT, MATCH_PARENT)
            addView(screensaverContainer, MATCH_PARENT, MATCH_PARENT)
        }
        setContentView(root)

        intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() }?.let { webView.loadUrl(it) }

        listenScreensaverConfig()
        resetIdleTimer()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (!screensaverVisible) resetIdleTimer()
    }

    override fun onDestroy() {
        idleHandler.removeCallbacks(idleRunnable)
        slideshowJob?.cancel()
        super.onDestroy()
    }

    /**
     * A USB "keyboard wedge" NFC reader delivers each character of the card id as a key
     * event, then Enter -- capturing at dispatch level catches it regardless of which view
     * (if any) has focus. Only intercepted while the screensaver is up; otherwise events
     * pass through untouched so normal typing on the page keeps working.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screensaverVisible && event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                val cardId = scanBuffer.toString()
                scanBuffer.clear()
                if (cardId.isNotBlank()) hideScreensaver()
                return true
            }
            val ch = event.unicodeChar
            if (ch != 0) scanBuffer.append(ch.toChar())
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun resetIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, idleTimeoutMs)
    }

    private fun showScreensaver() {
        Log.d(TAG, "idle timeout fired, ${imageUrls.size} images available")
        if (imageUrls.isEmpty()) return
        screensaverVisible = true
        scanBuffer.clear()
        screensaverContainer.visibility = android.view.View.VISIBLE
        startSlideshow()
    }

    private fun hideScreensaver() {
        screensaverVisible = false
        slideshowJob?.cancel()
        screensaverContainer.visibility = android.view.View.GONE
        resetIdleTimer()
    }

    private fun startSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = lifecycleScope.launch {
            var index = 0
            while (true) {
                Glide.with(this@WebKioskActivity).load(imageUrls[index]).into(screensaverImage)
                delay(SLIDESHOW_INTERVAL_MS)
                index = (index + 1) % imageUrls.size
            }
        }
    }

    private fun listenScreensaverConfig() {
        Firebase.firestore.collection("config").document("screensaver")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.w(TAG, "listenScreensaverConfig error", error)
                    return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                imageUrls = (snap?.get("imageUrls") as? List<String>).orEmpty()
                val configuredSec = snap?.getLong("idleTimeoutSec")
                idleTimeoutMs = (configuredSec?.takeIf { it > 0 } ?: DEFAULT_IDLE_TIMEOUT_MS / 1000) * 1000
                Log.d(TAG, "screensaver config: ${imageUrls.size} images, idleTimeoutMs=$idleTimeoutMs")
                if (imageUrls.isEmpty() && screensaverVisible) {
                    hideScreensaver()
                } else if (!screensaverVisible) {
                    // Apply a newly-changed timeout immediately instead of waiting for the
                    // next touch -- otherwise a still-running timer scheduled with the old
                    // duration keeps counting down on its own stale value.
                    resetIdleTimer()
                }
            }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    companion object {
        const val EXTRA_URL = "url"
        private const val DEFAULT_IDLE_TIMEOUT_MS = 10 * 60 * 1000L
        private const val SLIDESHOW_INTERVAL_MS = 8_000L
        private const val TAG = "WebKioskActivity"
    }
}
