package com.mrboomdev.scrollix.platform.clipboard

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

private val HtmlFlavors = listOf(
    "text/html;class=java.lang.String",
    // Browsers on Linux often publish the selection shape only.
    "text/html;document=selection;class=java.lang.String"
)

actual fun readClipboardRichText(): ClipboardRichText? {
    val contents = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
    }.getOrNull() ?: return null

    val plain = runCatching {
        if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            contents.getTransferData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    }.getOrNull()

    val html = HtmlFlavors.firstNotNullOfOrNull { name ->
        runCatching {
            val flavor = DataFlavor(name)
            if (!contents.isDataFlavorSupported(flavor)) return@runCatching null
            contents.getTransferData(flavor) as? String
        }.getOrNull()
    }

    if (plain.isNullOrEmpty() && html == null) return null

    return ClipboardRichText(plain = plain.orEmpty(), html = html)
}
