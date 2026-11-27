package com.hksalameh.shoppinglist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private val photoImport by lazy { ViewModelProvider(this)[PhotoImportViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[ShoppingViewModel::class.java]
        if (savedInstanceState == null) handleSharedImage(intent)

        setContent {
            ShoppingListTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ShoppingApp(viewModel, photoImport)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedImage(intent)
    }

    private fun handleSharedImage(intent: Intent) {
        if (intent.type?.startsWith("image/") != true) return
        val streams = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }
        val clip = intent.clipData
        val uris = (streams + (0 until (clip?.itemCount ?: 0)).mapNotNull { clip?.getItemAt(it)?.uri })
            .filter { it.scheme == "content" }
            .distinct()
        photoImport.receiveShare(uris)
    }
}
