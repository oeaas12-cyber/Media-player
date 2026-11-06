package com.prism.player.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prism.player.ui.components.GlassIconButton
import com.prism.player.ui.theme.Palette
import org.json.JSONObject

/** The access token + user the server handed the login session, read from its own web page. */
data class WebSession(val baseUrl: String, val token: String, val userId: String, val serverName: String?)

/**
 * Loads the server's OWN Emby/Jellyfin web login page in a WebView. The user signs in exactly
 * as they would in a browser; afterwards the app reads the access token the server stored for
 * that same session (the user's own login, on the user's own server) and saves it as a source.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(initialUrl: String, onCancel: () -> Unit, onSession: (WebSession) -> Unit) {
    var status by remember { mutableStateOf("Sign in on your server's page below…") }
    BackHandler(onBack = onCancel)

    // Reads Emby/Jellyfin credentials the web client keeps in localStorage after a successful login.
    val probeJs = """
        (function(){
          try {
            for (var i=0;i<localStorage.length;i++){
              var k=localStorage.key(i), v=localStorage.getItem(k);
              if(!v||v.indexOf('AccessToken')<0) continue;
              var o=JSON.parse(v);
              var servers=o.Servers||(o.servers)||[];
              for(var s=0;s<servers.length;s++){
                var sv=servers[s];
                if(sv.AccessToken&&sv.UserId){
                  var addr=sv.ManualAddress||sv.LocalAddress||sv.RemoteAddress||location.origin;
                  return JSON.stringify({token:sv.AccessToken,userId:sv.UserId,addr:addr,name:sv.Name||null});
                }
              }
            }
          } catch(e){}
          return "";
        })();
    """.trimIndent()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Cancel", onClick = onCancel)
            Text(status, color = Palette.TextSecondary, modifier = Modifier.fillMaxWidth())
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true

                        var delivered = false
                        fun tryProbe(view: WebView) {
                            view.evaluateJavascript(probeJs) { raw ->
                                if (delivered) return@evaluateJavascript
                                val json = raw?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\")
                                if (json.isNullOrBlank() || json == "null") return@evaluateJavascript
                                val o = runCatching { JSONObject(json) }.getOrNull() ?: return@evaluateJavascript
                                val token = o.optString("token")
                                val userId = o.optString("userId")
                                if (token.isNotBlank() && userId.isNotBlank()) {
                                    delivered = true
                                    status = "Signed in. Loading your library…"
                                    val addr = o.optString("addr").ifBlank { initialUrl }.trimEnd('/')
                                    onSession(WebSession(addr, token, userId, o.optString("name").takeIf { it.isNotBlank() }))
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                tryProbe(view)
                            }
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                // Emby/Jellyfin are single-page apps: the URL changes without a full reload.
                                tryProbe(view)
                            }
                        }
                        loadUrl(initialUrl)
                    }
                },
            )
        }
    }
}
