package com.mrboomdev.scrollix.ui.components

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
          <iframe src="$escaped" referrerpolicy="no-referrer-when-downgrade"></iframe>
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

              window.addEventListener('message', function (event) {
                var data = event.data;
                if (!data || typeof data !== 'object' || data.type !== 'vk-sak-sdk') return;

                if (data.handler === 'VKCaptchaGetResult' && data.params && data.params.token) {
                  deliver(String(data.params.token));
                } else if (data.handler === 'common: error') {
                  fail('The captcha reported an error');
                }
              });
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}
