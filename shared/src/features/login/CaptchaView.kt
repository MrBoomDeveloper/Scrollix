package com.mrboomdev.scrollix.features.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders a provider captcha and reports the token it produces.
 *
 * Android shows the widget in an embedded browser; platforms without a web
 * view fall back to an explanatory message instead of pretending to support
 * the check.
 */
@Composable
expect fun CaptchaView(
    url: String,
    enabled: Boolean,
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
)

/**
 * Wrapper page for provider captcha widgets.
 *
 * It embeds the check in an iframe and forwards the widget result to both the
 * Android JavaScript bridge and a global that the desktop build polls. The
 * widget reports `{type: "vk-sak-sdk", handler: "VKCaptchaGetResult", params:
 * {token}}` to its embedder.
 *
 * The widget only posts that frame when its URL carries an `origin` query
 * parameter; without it the check is solved on screen but the token is never
 * delivered. The official VK captcha SDK appends it, so the wrapper does too,
 * using the origin of the document that embeds the iframe (this page).
 */
internal fun captchaPage(link: String): String {
    val escaped = link
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
          <style>
            html, body { margin: 0; padding: 0; height: 100%; background: transparent; }
            iframe { display: block; width: 100%; height: 100%; min-height: 100vh; border: 0; }
          </style>
        </head>
        <body>
          <iframe id="scrollix-captcha-frame" data-src="$escaped" referrerpolicy="no-referrer-when-downgrade"></iframe>
          <script>
            (function () {
              function deliver(token) {
                window.__scrollixCaptchaToken = token;
                try {
                  if (window.ScrollixCaptcha && ScrollixCaptcha.onToken) {
                    ScrollixCaptcha.onToken(token);
                  }
                } catch (ignored) {}
              }

              function fail(message) {
                window.__scrollixCaptchaError = message;
                try {
                  if (window.ScrollixCaptcha && ScrollixCaptcha.onError) {
                    ScrollixCaptcha.onError(message);
                  }
                } catch (ignored) {}
              }

              // Kept for the desktop build, which cannot receive callbacks from
              // the page and polls these globals instead.
              window.__scrollixCaptchaMessages = [];

              function embedderOrigin() {
                try {
                  var base = new URL(document.baseURI);
                  if (base.protocol === 'https:' || base.protocol === 'http:') {
                    return base.protocol + '//' + base.hostname;
                  }
                } catch (ignored) {}

                return location.protocol + '//' + location.hostname;
              }

              var frame = document.getElementById('scrollix-captcha-frame');

              try {
                var captchaUrl = new URL(frame.dataset.src);

                if (!captchaUrl.searchParams.get('origin')) {
                  captchaUrl.searchParams.set('origin', embedderOrigin());
                }

                frame.src = captchaUrl.href;
              } catch (ignored) {
                frame.src = frame.dataset.src;
              }

              window.addEventListener('message', function (event) {
                var data = event.data;

                try {
                  var text = typeof data === 'string' ? data : JSON.stringify(data);
                  window.__scrollixCaptchaMessages.push(String(text).slice(0, 400));
                  if (window.__scrollixCaptchaMessages.length > 20) {
                    window.__scrollixCaptchaMessages.shift();
                  }
                } catch (ignored) {}

                // The widget may deliver its payload as a plain object or as a
                // JSON string, and some embedders only forward the result.
                if (typeof data === 'string') {
                  try { data = JSON.parse(data); } catch (ignored) { return; }
                }

                if (!data || typeof data !== 'object') return;

                var params = data.params || {};

                if (params.token) {
                  deliver(String(params.token));
                } else if (data.handler === 'common: error' || data.handler === 'VKSDKError') {
                  fail('The captcha reported an error');
                }
              });
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}
