package com.pira.ccloud.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
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
 * querying.
 *
 * Picking a title has two ways to work, because relying on just one turned out to
 * be unreliable:
 *  - **Tap a result's title** (primary, recommended way): each result heading on a
 *    Google results page is itself a link, and on Android, long-pressing text that
 *    is also a link normally brings up the link's own "open/copy link" menu instead
 *    of starting text selection - so trying to *select* a title by long-pressing it
 *    often doesn't work at all. To sidestep that, JS injected after the page loads
 *    intercepts a plain tap (not a long-press) on each result heading, stops it
 *    from navigating, and sends its text straight back to Kotlin via
 *    [TitlePickerBridge].
 *  - **Select any other text + "Use this title"** (fallback): for text that isn't
 *    a result heading (e.g. part of a snippet), where long-press selection does
 *    work normally, the toolbar button reads whatever is currently highlighted via
 *    `window.getSelection()`.
 *
 * Either way the chosen text is handed back to [SearchScreen] via the previous
 * back-stack entry's SavedStateHandle under [GOOGLE_SELECTED_TITLE_KEY].
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun GoogleTitleSearchScreen(
    initialQuery: String,
    navController: NavController? = null
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun useTitle(title: String) {
        val cleaned = title.trim()
        if (cleaned.isEmpty()) return
        navController?.previousBackStackEntry
            ?.savedStateHandle
            ?.set(GOOGLE_SELECTED_TITLE_KEY, cleaned)
        navController?.popBackStack()
    }

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
                    text = "روی عنوان یه نتیجه ضربه بزنید",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                TextButton(onClick = {
                    // Fallback for text that isn't a result heading: pull whatever
                    // the user has manually highlighted on the page instead.
                    webView?.evaluateJavascript(
                        "(function(){ var s = window.getSelection(); return s ? s.toString() : ''; })()"
                    ) { rawResult ->
                        useTitle(decodeJsStringResult(rawResult))
                    }
                }) {
                    Text("Use selected text")
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

                        // Runs tap-triggered callbacks from the injected JS below.
                        // @JavascriptInterface methods are invoked on a WebView
                        // background thread, not the main thread, so hop back to
                        // the main thread before touching Compose state/navigation.
                        addJavascriptInterface(
                            TitlePickerBridge { title ->
                                Handler(Looper.getMainLooper()).post { useTitle(title) }
                            },
                            "AndroidTitlePicker"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                view?.evaluateJavascript(TAP_TO_PICK_TITLE_JS, null)
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

/** Bridges a tapped result title from page JS (see [TAP_TO_PICK_TITLE_JS]) back to Kotlin. */
private class TitlePickerBridge(private val onPicked: (String) -> Unit) {
    @JavascriptInterface
    fun onTitlePicked(title: String) {
        onPicked(title)
    }
}

/**
 * Finds each organic result's heading (Google wraps these in an "h3") and:
 *  - gives it a visible underline so it's clear it's tappable for this purpose;
 *  - intercepts a plain tap on it in the capture phase (so this runs before the
 *    link's own click/navigation handling), stops that default navigation, and
 *    reports its text to [TitlePickerBridge] instead.
 * Re-run after every page load (including paging to more results), since it only
 * affects elements present at the time it runs.
 */
private const val TAP_TO_PICK_TITLE_JS = """
(function() {
    var headings = document.querySelectorAll('h3');
    for (var i = 0; i < headings.length; i++) {
        var h = headings[i];
        if (h.dataset.titlePickerBound) continue;
        h.dataset.titlePickerBound = '1';
        h.style.textDecoration = 'underline';
        h.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            if (window.AndroidTitlePicker) {
                window.AndroidTitlePicker.onTitlePicked(this.innerText);
            }
        }, true);
    }
})();
"""

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
