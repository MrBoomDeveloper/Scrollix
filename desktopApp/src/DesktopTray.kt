package com.mrboomdev.scrollix

import java.awt.Color
import java.awt.Font
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * Desktop tray entry. AWT's tray keeps this dependency-free; environments
 * without a tray (headless CI, some Linux sessions) make every call a no-op.
 */
object DesktopTray {
    private var icon: TrayIcon? = null

    /** Returns true when the tray was shown. Safe to call more than once. */
    fun install(onOpen: () -> Unit, onQuit: () -> Unit): Boolean {
        if (icon != null) return true
        if (!runCatching { SystemTray.isSupported() }.getOrDefault(false)) return false

        val menu = PopupMenu().apply {
            add(MenuItem("Open Scrollix").apply { addActionListener { onOpen() } })
            addSeparator()
            add(MenuItem("Quit").apply { addActionListener { onQuit() } })
        }

        val trayIcon = TrayIcon(render(unread = 0), "Scrollix", menu).apply {
            isImageAutoSize = true
            // Double-click (Windows) opens the window as well.
            addActionListener { onOpen() }
        }

        return runCatching {
            SystemTray.getSystemTray().add(trayIcon)
            icon = trayIcon
            true
        }.getOrDefault(false)
    }

    fun setUnread(count: Int) {
        val trayIcon = icon ?: return
        val unread = count.coerceAtLeast(0)

        trayIcon.toolTip = if (unread > 0) "Scrollix — $unread unread" else "Scrollix"
        trayIcon.image = render(unread)
    }

    fun remove() {
        val trayIcon = icon ?: return
        icon = null
        runCatching { SystemTray.getSystemTray().remove(trayIcon) }
    }

    private fun render(unread: Int): BufferedImage {
        val size = 32
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()

        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            )

            // Chat bubble mark on the app's purple.
            graphics.color = Color(0x3A, 0x1E, 0x5A)
            graphics.fillRoundRect(0, 0, size, size, 8, 8)
            graphics.color = Color.WHITE
            graphics.fillRoundRect(6, 7, 20, 14, 5, 5)
            graphics.fillPolygon(intArrayOf(10, 10, 16), intArrayOf(20, 26, 21), 3)

            if (unread > 0) {
                val badgeSize = 14
                graphics.color = Color(0xE5, 0x39, 0x35)
                graphics.fillOval(size - badgeSize, size - badgeSize, badgeSize, badgeSize)

                val label = if (unread > 9) "9+" else unread.toString()
                graphics.color = Color.WHITE
                graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 9)

                val metrics = graphics.fontMetrics
                graphics.drawString(
                    label,
                    size - badgeSize + (badgeSize - metrics.stringWidth(label)) / 2,
                    size - badgeSize + (badgeSize + metrics.ascent - metrics.descent) / 2 - 1
                )
            }
        } finally {
            graphics.dispose()
        }

        return image
    }
}
