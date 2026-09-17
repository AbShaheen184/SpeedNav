package com.speedcam.nav

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.speedcam.nav.ui.HomeScreen
import com.speedcam.nav.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val incomingSharedLinkState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractSharedLink(intent)

        setContent {
            MyApplicationTheme(dynamicColor = false) {
                HomeScreen(
                    incomingSharedLink = incomingSharedLinkState.value,
                    onSharedLinkConsumed = { incomingSharedLinkState.value = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedLink(intent)
    }

    private fun extractSharedLink(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val dataUri = intent.data?.toString()
                if (!dataUri.isNullOrBlank()) {
                    incomingSharedLinkState.value = dataUri
                }
            }
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                if (!sharedText.isNullOrBlank()) {
                    incomingSharedLinkState.value = sharedText
                }
            }
        }
    }
}
