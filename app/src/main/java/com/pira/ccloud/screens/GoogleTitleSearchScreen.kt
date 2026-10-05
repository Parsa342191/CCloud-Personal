package com.pira.ccloud.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import java.net.URLEncoder

/** Key the selected title is returned under via the previous back-stack entry's
 * SavedStateHandle - [SearchScreen] reads this key once it's back on top. */
const val GOOGLE_SELECTED_TITLE_KEY = "google_selected_title"

/**
 * A plain Google search results page for [initialQuery], opened inside the app so
 * the user can find a title's real English name (the app's own search only matches
 * English titles, but a lot of users only know a title's Persian/fansub name).
 *
 * This is a real WebView the user interacts with directly - not automated scraping
 * of Google's results - so it doesn't touch Google's terms around automated
 * querying. "Use this title" simply reads whatever text the user has highlighted
 * on the page (via `window.getSelection()`) and feeds it back into the app's own
 * search as the query.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleTitleSearchScreen(
    initialQuery: String,
    navController: NavController? = null
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController?.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = initialQuery,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                TextButton(onClick = {
                    // Pull whatever the user has highlighted on the page and hand it
                    // back to the search screen as the query to actually search for
                    // in the app's own catalog.
                    webView?.evaluateJavascript(
                        "(function(){ var s = window.getSelection(); return s ? s.toString() : ''; })()"
                    ) { rawResult ->
                        val selected = decodeJsStringResult(rawResult)
                        if (selected.isNotEmpty()) {
                            navController?.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(GOOGLE_SELECTED_TITLE_KEY, selected)
                            navController?.popBackStack()
                        }
                    }
                }) {
                    Text("Use this title")
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }
                        }
                        webView = this
                        val encodedQuery = URLEncoder.encode(initialQuery, "UTF-8")
                        loadUrl("https://www.google.com/search?q=$encodedQuery")
                    }
                }
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                )
            }
        }
    }
}

/**
 * [WebView.evaluateJavascript]'s callback receives the result JSON-encoded (e.g. a
 * selection of `One Piece` comes back as the literal string `"One Piece"`, and an
 * empty selection as `""`). This undoes that encoding to get the plain text back.
 */
private fun decodeJsStringResult(raw: String?): String {
    if (raw == null || raw == "null") return ""
    return raw
        .removeSurrounding("\"")
        .replace("\\n", " ")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .trim()
}
