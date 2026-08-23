package com.filo.app.spotify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.filo.app.MainActivity
import kotlinx.coroutines.launch

/**
 * Catches the redirect back from Spotify's consent page.
 *
 * It is its own activity rather than an intent-filter on MainActivity, which has no
 * launchMode set and would otherwise be started a second time in a second task.
 */
class SpotifyAuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            SpotifyAuth.completeAuthorisation(applicationContext, uri)
            startActivity(
                Intent(this@SpotifyAuthActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
        }
    }
}
