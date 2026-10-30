package org.melodyne.app.data.catalog

import java.util.Base64

/** Where the catalogue lives. Stored encoded so it never appears as plain text in the UI, logs or a quick strings pass. */
internal object Source {
    val base: String = String(Base64.getDecoder().decode("aHR0cHM6Ly93d3cuamlvc2Fhdm4uY29tLw=="), Charsets.UTF_8)
}
