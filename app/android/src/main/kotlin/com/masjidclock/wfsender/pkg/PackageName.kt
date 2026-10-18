package com.masjidclock.wfsender.pkg

/** The file name rule of HD Sender packages (spec section 4.3). */
object PackageName {
    const val EXTENSION = ".hdpkg"
    private val ALLOWED = Regex("^[A-Za-z0-9 _-]{1,50}$")

    /** The cleaned name without the extension, or null if the name is not valid. */
    fun clean(input: String): String? {
        val name = withoutExtension(input.trim()).trim()
        return if (ALLOWED.matches(name)) name else null
    }

    fun fileName(cleanName: String): String = cleanName + EXTENSION

    fun hasExtension(fileName: String): Boolean = fileName.endsWith(EXTENSION, ignoreCase = true)

    fun withoutExtension(fileName: String): String =
        if (hasExtension(fileName)) fileName.dropLast(EXTENSION.length) else fileName
}
