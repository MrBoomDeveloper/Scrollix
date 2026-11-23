package com.mrboomdev.scrollix

import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.io.File
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

private const val RuntimeRelease = "jbr-release-17.0.14b1367.22"

/**
 * Standalone KCEF smoke test. It uses the same pinned runtime, settings and
 * HTML loading path as the desktop captcha browser, prints the init and page
 * results and exits, because the embedded browser would otherwise keep the
 * JVM alive.
 *
 * Run it by pointing `mainClass` in `desktopApp/module.yaml` at this file and
 * invoking `./kotlin run -m desktopApp`.
 */
fun main(args: Array<String>) {
    var exitCode = 1

    runBlocking {
        val dir = File(
            File(System.getProperty("user.home"), ".scrollix"),
            "kcef-probe-$RuntimeRelease"
        )
        val initialized = CompletableDeferred<Unit>()

        println("PROBE_JAVA_HOME=${System.getProperty("java.home")}")

        KCEF.init(
            builder = {
                installDir(dir)
                download {
                    github {
                        release(RuntimeRelease)
                    }
                }
                args(
                    "--no-sandbox",
                    "--disable-dev-shm-usage"
                )
                settings {
                    browserSubProcessPath = File(dir, "jcef_helper").absolutePath
                    resourcesDirPath = dir.absolutePath
                    localesDirPath = File(dir, "locales").absolutePath
                    cachePath = File(dir, "cache").absolutePath
                    noSandbox = true
                }
                progress {
                    onInitialized { initialized.complete(Unit) }
                }
            },
            onError = { error ->
                println("PROBE_ERROR=${error?.message}")
                initialized.completeExceptionally(
                    error ?: IllegalStateException("init failed")
                )
            },
            onRestartRequired = {
                initialized.completeExceptionally(IllegalStateException("restart required"))
            }
        )

        val init = runCatching { withTimeout(180_000) { initialized.await() } }

        if (init.isFailure) {
            println("PROBE_FAILED=${init.exceptionOrNull()}")
            return@runBlocking
        }

        println("PROBE_OK")

        val loaded = CompletableDeferred<Unit>()
        var client: KCEFClient? = null
        var browser: KCEFBrowser? = null
        var frame: javax.swing.JFrame? = null

        SwingUtilities.invokeAndWait {
            runCatching {
                val created = KCEF.newClientBlocking()
                client = created

                created.addLoadHandler(
                    object : CefLoadHandlerAdapter() {
                        override fun onLoadingStateChange(
                            browser: CefBrowser?,
                            isLoading: Boolean,
                            canGoBack: Boolean,
                            canGoForward: Boolean
                        ) {
                            if (!isLoading) loaded.complete(Unit)
                        }

                        override fun onLoadError(
                            browser: CefBrowser?,
                            frame: CefFrame?,
                            errorCode: CefLoadHandler.ErrorCode?,
                            errorText: String?,
                            failedUrl: String?
                        ) {
                            loaded.completeExceptionally(
                                IllegalStateException("load failed: $errorCode $errorText")
                            )
                        }
                    }
                )

                val createdBrowser = created.createBrowserWithHtml(
                    html = "<html><head><title>probe-ok</title></head><body>hi</body></html>",
                    url = "https://web.max.ru/"
                )
                browser = createdBrowser

                // The app embeds this component in Compose; a plain frame does
                // the same here so the browser actually starts loading.
                frame = javax.swing.JFrame("KCEF probe").apply {
                    add(createdBrowser.uiComponent)
                    setSize(800, 600)
                    isVisible = true
                }
            }.onFailure { loaded.completeExceptionally(it) }
        }

        // A rendered document is what used to fail with a mismatched JCEF
        // native runtime: the load only completes when resources were served.
        val page = runCatching { withTimeout(60_000) { loaded.await() } }

        page
            .onSuccess {
                println("PROBE_PAGE_LOADED")
                exitCode = 0
            }
            .onFailure { println("PROBE_PAGE_FAILED=$it") }

        SwingUtilities.invokeAndWait {
            runCatching { browser?.dispose() }
            runCatching { client?.dispose() }
            runCatching { frame?.dispose() }
        }
        runCatching { KCEF.disposeBlocking() }
    }

    exitProcess(exitCode)
}
