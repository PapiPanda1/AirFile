package com.airfile.tv.storage

object FileNames {
    private val illegal = Regex("[\\u0000-\\u001F\\u007F<>:\"|?*]")

    /** Strips any path, control and reserved characters; never returns an empty or dot-only name. */
    fun sanitize(raw: String): String {
        var name = raw.substringAfterLast('/').substringAfterLast('\\')
        name = illegal.replace(name, "_").trim { it == '.' || it.isWhitespace() }
        if (name.isEmpty()) return "file"
        if (name.length > 200) {
            val dot = name.lastIndexOf('.')
            val ext = if (dot > 0 && name.length - dot <= 20) name.substring(dot) else ""
            name = name.take(200 - ext.length) + ext
        }
        return name
    }

    /** Returns [name], or "name (1).ext", "name (2).ext", ... if it already exists. */
    fun uniqueName(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        for (i in 1..9999) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
        }
        return "$base (${System.currentTimeMillis()})$ext"
    }
}
