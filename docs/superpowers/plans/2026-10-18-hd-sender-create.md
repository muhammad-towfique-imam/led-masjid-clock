# HD Sender Create Flow and .hdpkg Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a home screen (Send, Create), a Create flow that makes an `.hdpkg` package from an HD2020 U-disk export on a USB drive and saves or shares it, and make the Send flow accept only `.hdpkg` files.

**Architecture:** New pure-Kotlin units in the `android` module (`pkg/PackageName`, `pkg/PackageWriter`, `pkg/Downloads`, `usb/ExportFinder`, `ui/createSteps`) carry the logic and have JVM tests. Thin Android units (`usb/RemovableVolumes`, `usb/StorageAccess`, `CreateViewModel`, screens) call them. `AppViewModel` gets a page state (HOME, SEND, CREATE).

**Tech Stack:** Kotlin 2.1.20, Jetpack Compose (BOM 2025.02.00), AndroidX core `FileProvider`, lifecycle-runtime-compose `LifecycleResumeEffect`, JUnit 4.13.2. Gradle 8.11.1 with Java 21.

**Spec:** `docs/superpowers/specs/2026-10-18-hd-sender-create-design.md` (base spec: `docs/superpowers/specs/2026-10-18-android-wf-sender-design.md`)

## Global Constraints

- Run every Gradle command from `/home/towfique/workspace/led-masjid-clock/app` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.
- Package names stay `com.masjidclock.wfsender.*`. App ID stays `com.hdsender.app`.
- Version after this plan: `versionCode = 4`, `versionName = "0.3.0"`.
- Package extension: `.hdpkg`, matched without regard to case. The Send flow accepts only `.hdpkg`.
- `.hdpkg` content: a ZIP with exactly 2 entries at the root, `screen.hds` and `progam.hds`, with the bytes of the export without change.
- Name rule: after `trim()` and removal of a typed `.hdpkg`, the name must match `^[A-Za-z0-9 _-]{1,50}$`.
- Save never overwrites: `name.hdpkg`, then `name (1).hdpkg`, `name (2).hdpkg`, and so on.
- USB check interval: 2 seconds while the Create page is visible.
- Permissions: `MANAGE_EXTERNAL_STORAGE` (Android 11+), `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="29"`, and `android:requestLegacyExternalStorage="true"`.
- User texts must be exactly the texts in `Messages.kt` (Task 5) and in spec sections 4 and 5.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

## Review Focus

1. A name with only spaces, or a name that is only `.hdpkg`, must be rejected, not saved as `.hdpkg` with an empty name. Test: Task 1 `rejectsEmptyAfterCleaning`.
2. A file name from the provider with upper-case `.HDPKG` must be accepted, and the display name must not contain the extension. Test: Task 2 `acceptsUpperCaseExtension`.
3. A partial file after a failed save must be deleted, so that Downloads does not keep a broken package. Test: Task 4 `deletesThePartialFileWhenTheWriteFails`.
4. A USB root that cannot be listed (no permission, or the drive was removed) must count as "no export", not crash. Test: Task 5 `rootThatCannotBeListedIsSkipped`.
5. The package that Create writes must be readable by the Send flow with the same bytes. Test: Task 3 `writtenPackageImportsWithTheSameBytes`.

## File Structure

```
app/android/src/main/
├── AndroidManifest.xml                      permissions, legacy storage flag, FileProvider (Task 6)
├── res/xml/file_paths.xml                   FileProvider path for shared packages (Task 6)
└── kotlin/com/masjidclock/wfsender/
    ├── AppViewModel.kt                      + Page state, openSend, openCreate, goHome, importPackage (Task 6)
    ├── CreateViewModel.kt                   Create steps, Save, Share (Task 6)
    ├── MainActivity.kt                      2 view models, importPackage (Task 6)
    ├── pkg/PackageName.kt                   name rule (Task 1)
    ├── pkg/PackageWriter.kt                 export → .hdpkg ZIP (Task 3)
    ├── pkg/Downloads.kt                     free name + safe write (Task 4)
    ├── site/ZipImporter.kt                  .hdpkg rule (Task 2)
    ├── send/Messages.kt                     new texts (Task 5)
    ├── usb/ExportFinder.kt                  first root with a valid export, UsbStatus (Task 5)
    ├── usb/RemovableVolumes.kt              StorageManager volumes (Task 6)
    ├── usb/StorageAccess.kt                 permission check and settings intent (Task 6)
    └── ui/
        ├── Steps.kt                         + CreateSteps, createSteps (Task 5)
        ├── Components.kt                    Step, PageTitle, colors, KeepScreenOn (Task 6)
        ├── Screens.kt                       WfSenderApp router + SendScreen (Task 6)
        ├── HomeScreen.kt                    2 buttons (Task 6)
        └── CreateScreen.kt                  Create page, name dialog (Task 6)
app/android/src/test/kotlin/com/masjidclock/wfsender/
    ├── pkg/PackageNameTest.kt, pkg/PackageWriterTest.kt, pkg/DownloadsTest.kt
    ├── usb/ExportFinderTest.kt
    ├── ui/StepsTest.kt (+ create steps), send/MessagesTest.kt (+ new texts)
    └── site/ZipImporterTest.kt (.hdpkg)
```

---

### Task 1: PackageName

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/pkg/PackageName.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/pkg/PackageNameTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `object PackageName { const val EXTENSION = ".hdpkg"; fun clean(input: String): String?; fun fileName(cleanName: String): String; fun hasExtension(fileName: String): Boolean; fun withoutExtension(fileName: String): String }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.masjidclock.wfsender.pkg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageNameTest {

    @Test
    fun acceptsAValidName() {
        assertEquals("masjid-north_english 2", PackageName.clean("masjid-north_english 2"))
    }

    @Test
    fun removesSpacesAtStartAndEnd() {
        assertEquals("english", PackageName.clean("  english  "))
    }

    @Test
    fun removesATypedExtension() {
        assertEquals("english", PackageName.clean("english.hdpkg"))
        assertEquals("english", PackageName.clean("english.HDPKG "))
    }

    @Test
    fun rejectsEmptyAfterCleaning() {
        assertNull(PackageName.clean(""))
        assertNull(PackageName.clean("   "))
        assertNull(PackageName.clean(".hdpkg"))
    }

    @Test
    fun rejectsMoreThan50Characters() {
        assertEquals("a".repeat(50), PackageName.clean("a".repeat(50)))
        assertNull(PackageName.clean("a".repeat(51)))
    }

    @Test
    fun rejectsCharactersThatAreNotAllowed() {
        assertNull(PackageName.clean("a/b"))
        assertNull(PackageName.clean("a.b"))
        assertNull(PackageName.clean("mosquée"))
        assertNull(PackageName.clean("মসজিদ"))
    }

    @Test
    fun makesAndReadsFileNames() {
        assertEquals("english.hdpkg", PackageName.fileName("english"))
        assertTrue(PackageName.hasExtension("english.HdPkg"))
        assertFalse(PackageName.hasExtension("english.zip"))
        assertEquals("english", PackageName.withoutExtension("english.HDPKG"))
        assertEquals("english.zip", PackageName.withoutExtension("english.zip"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :android:testDebugUnitTest`
Expected: FAIL, `Unresolved reference 'PackageName'`.

- [ ] **Step 3: Implement**

```kotlin
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
```

- [ ] **Step 4: Run to verify it passes**

Run: the command of Step 2. Expected: `BUILD SUCCESSFUL`; `PackageNameTest` 7 tests pass; all other tests pass.

- [ ] **Step 5: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock && git add app/android && git commit -m "Add the package name rule" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: ZipImporter accepts only .hdpkg

**Files:**
- Modify: `app/android/src/main/kotlin/com/masjidclock/wfsender/site/ZipImporter.kt` (`import`, `siteName`)
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/site/ZipImporterTest.kt`

**Interfaces:**
- Consumes: `PackageName.hasExtension`, `PackageName.withoutExtension` (Task 1).
- Produces: `ZipImporter.NOT_PACKAGE` (companion `const val`): `"This file is not an HD Sender package. Use a file that ends with .hdpkg."`

- [ ] **Step 1: Change the test names and add tests**

In `ZipImporterTest.kt`, replace each test file name: `"site-a.zip"` → `"site-a.hdpkg"`, `"masjid-x.zip"` → `"masjid-x.hdpkg"`, `"one.zip"` → `"one.hdpkg"`, `"two.zip"` → `"two.hdpkg"`. The expected labels and site names stay the same. Then add:

```kotlin
    @Test
    fun rejectsZipExtension() {
        val zip = zipOf("screen.hds" to screen(128, 64), "progam.hds" to program())
        val error = assertThrows(ImportException::class.java) { import(zip, name = "english.zip") }
        assertEquals(ZipImporter.NOT_PACKAGE, error.message)
        assertEquals("This file is not an HD Sender package. Use a file that ends with .hdpkg.", error.message)
    }

    @Test
    fun acceptsUpperCaseExtension() {
        val site = import(zipOf("screen.hds" to screen(128, 64), "progam.hds" to program()), name = "North-English.HDPKG")
        assertEquals("North-English", site.name)
        assertEquals(listOf("North-English"), site.cards.map { it.label })
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :android:testDebugUnitTest`
Expected: FAIL. `Unresolved reference 'NOT_PACKAGE'`.

- [ ] **Step 3: Implement**

In `ZipImporter.kt`:
- add `import com.masjidclock.wfsender.pkg.PackageName`;
- at the start of `import(...)`, before `val name = siteName(zipName)`, add:

```kotlin
        if (!PackageName.hasExtension(zipName)) throw ImportException(NOT_PACKAGE)
```

- replace the `siteName` function with:

```kotlin
        private fun siteName(zipName: String): String =
            PackageName.withoutExtension(zipName.substringAfterLast('/'))
```

- in the `companion object`, add:

```kotlin
        const val NOT_PACKAGE = "This file is not an HD Sender package. Use a file that ends with .hdpkg."
```

- [ ] **Step 4: Run to verify it passes**

Run: the command of Step 2. Expected: `BUILD SUCCESSFUL`, all tests pass (`ZipImporterTest` 19 tests).

- [ ] **Step 5: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock && git add app/android && git commit -m "Accept only .hdpkg packages in the Send flow" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: PackageWriter

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/pkg/PackageWriter.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/pkg/PackageWriterTest.kt`

**Interfaces:**
- Consumes: `HdsExport` (protocol); `ZipImporter` (Task 2) in the test.
- Produces: `object PackageWriter { fun write(export: HdsExport, out: java.io.OutputStream) }` — it closes `out`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.masjidclock.wfsender.pkg

import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.site.ZipImporter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PackageWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val screen = ByteArray(66).also {
        it[0] = 'H'.code.toByte(); it[1] = 'S'.code.toByte(); it[5] = 0x80.toByte(); it[7] = 0x40
    }
    private val program = ByteArray(2500) { (it % 251).toByte() }.also {
        it[0] = 'H'.code.toByte(); it[1] = 'C'.code.toByte()
    }
    private val export = HdsExport(screen, program)

    private fun written(): ByteArray = ByteArrayOutputStream().also { PackageWriter.write(export, it) }.toByteArray()

    @Test
    fun writesExactlyTheTwoFilesAtTheRoot() {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(written())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        assertEquals(setOf("screen.hds", "progam.hds"), entries.keys)
        assertArrayEquals(screen, entries["screen.hds"])
        assertArrayEquals(program, entries["progam.hds"])
    }

    @Test
    fun writtenPackageImportsWithTheSameBytes() {
        val siteDir = File(tmp.newFolder("files"), "site")
        val site = ZipImporter().import(ByteArrayInputStream(written()), "masjid-north.hdpkg", siteDir)
        val card = site.cards.single()
        assertEquals("masjid-north", card.label)
        assertEquals(128, card.width)
        assertEquals(64, card.height)
        assertArrayEquals(program, File(card.dir, "progam.hds").readBytes())
        assertArrayEquals(screen, File(card.dir, "screen.hds").readBytes())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :android:testDebugUnitTest`
Expected: FAIL, `Unresolved reference 'PackageWriter'`.

- [ ] **Step 3: Implement**

```kotlin
package com.masjidclock.wfsender.pkg

import com.masjidclock.wfsender.protocol.HdsExport
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes an HD2020 export as an .hdpkg package (spec section 5.2). Closes the stream. */
object PackageWriter {
    fun write(export: HdsExport, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(HdsExport.SCREEN_FILE))
            zip.write(export.screen)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(HdsExport.PROGRAM_FILE))
            zip.write(export.program)
            zip.closeEntry()
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes** — the command of Step 2; `PackageWriterTest` 2 tests pass, all others pass.

- [ ] **Step 5: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock && git add app/android && git commit -m "Add the .hdpkg package writer" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Downloads

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/pkg/Downloads.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/pkg/DownloadsTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `object Downloads { fun freeFile(dir: File, fileName: String): File; fun save(dir: File, fileName: String, write: (java.io.OutputStream) -> Unit): File }` — `save` creates `dir` if needed, never overwrites, deletes a partial file and rethrows on failure.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.masjidclock.wfsender.pkg

import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun usesTheNameWhenItIsFree() {
        val dir = tmp.newFolder()
        assertEquals(File(dir, "english.hdpkg"), Downloads.freeFile(dir, "english.hdpkg"))
    }

    @Test
    fun addsANumberWhenTheNameExists() {
        val dir = tmp.newFolder()
        File(dir, "english.hdpkg").writeText("x")
        assertEquals(File(dir, "english (1).hdpkg"), Downloads.freeFile(dir, "english.hdpkg"))
        File(dir, "english (1).hdpkg").writeText("x")
        assertEquals(File(dir, "english (2).hdpkg"), Downloads.freeFile(dir, "english.hdpkg"))
    }

    @Test
    fun savesTheBytesAndNeverOverwrites() {
        val dir = File(tmp.root, "Download")
        val first = Downloads.save(dir, "english.hdpkg") { it.write(byteArrayOf(1, 2, 3)) }
        val second = Downloads.save(dir, "english.hdpkg") { it.write(byteArrayOf(4)) }
        assertEquals("english.hdpkg", first.name)
        assertEquals("english (1).hdpkg", second.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), first.readBytes())
        assertArrayEquals(byteArrayOf(4), second.readBytes())
    }

    @Test
    fun deletesThePartialFileWhenTheWriteFails() {
        val dir = tmp.newFolder()
        assertThrows(IOException::class.java) {
            Downloads.save(dir, "english.hdpkg") { it.write(1); throw IOException("disk full") }
        }
        assertFalse(File(dir, "english.hdpkg").exists())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :android:testDebugUnitTest`
Expected: FAIL, `Unresolved reference 'Downloads'`.

- [ ] **Step 3: Implement**

```kotlin
package com.masjidclock.wfsender.pkg

import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Saves a file without overwriting an existing file (spec section 5.3). */
object Downloads {

    /** [fileName] if it is free in [dir], otherwise "name (1).ext", "name (2).ext", and so on. */
    fun freeFile(dir: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.')
        val extension = if ('.' in fileName) "." + fileName.substringAfterLast('.') else ""
        var file = File(dir, fileName)
        var number = 1
        while (file.exists()) {
            file = File(dir, "$base ($number)$extension")
            number++
        }
        return file
    }

    /** Writes a new file in [dir]. On failure, deletes the partial file and throws. */
    fun save(dir: File, fileName: String, write: (OutputStream) -> Unit): File {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val file = freeFile(dir, fileName)
        try {
            file.outputStream().use { write(it) }
        } catch (e: IOException) {
            file.delete()
            throw e
        }
        return file
    }
}
```

- [ ] **Step 4: Run to verify it passes** — the command of Step 2; `DownloadsTest` 4 tests pass, all others pass.

- [ ] **Step 5: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock && git add app/android && git commit -m "Add safe save without overwrite for packages" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: ExportFinder, Create steps, and new messages

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/usb/ExportFinder.kt`
- Modify: `app/android/src/main/kotlin/com/masjidclock/wfsender/ui/Steps.kt` (add `CreateSteps`, `createSteps`)
- Modify: `app/android/src/main/kotlin/com/masjidclock/wfsender/send/Messages.kt` (new texts)
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/usb/ExportFinderTest.kt`; add to `ui/StepsTest.kt` and `send/MessagesTest.kt`

**Interfaces:**
- Consumes: `HdsExport`, `InvalidExportException` (protocol); `StepState` (existing `ui/Steps.kt`).
- Produces:
  - `enum class UsbStatus { NO_PERMISSION, NO_DRIVE, NO_EXPORT, FOUND }`
  - `data class FoundExport(val root: File, val export: HdsExport)`; `object ExportFinder { fun find(roots: List<File>): FoundExport? }`
  - `data class CreateSteps(val access: StepState, val drive: StepState, val files: StepState, val output: StepState)`; `fun createSteps(status: UsbStatus): CreateSteps`
  - `Messages`: `NO_SITE` (changed), `WAIT_FOR_SEND` (changed), `ALLOW_ACCESS`, `CONNECT_USB`, `NO_EXPORT`, `SAVE_FAILED`, `NAME_RULE`, `fun found(volume: String, width: Int, height: Int): String`, `fun saved(fileName: String): String`

- [ ] **Step 1: Write the failing tests**

`usb/ExportFinderTest.kt`:

```kotlin
package com.masjidclock.wfsender.usb

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportFinderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val screen = ByteArray(66).also { it[0] = 'H'.code.toByte(); it[1] = 'S'.code.toByte(); it[5] = 0x80.toByte(); it[7] = 0x40 }
    private val program = ByteArray(100).also { it[0] = 'H'.code.toByte(); it[1] = 'C'.code.toByte() }

    private fun root(screenName: String? = "screen.hds", programName: String? = "progam.hds", screenBytes: ByteArray = screen): File {
        val dir = tmp.newFolder()
        screenName?.let { File(dir, it).writeBytes(screenBytes) }
        programName?.let { File(dir, it).writeBytes(program) }
        return dir
    }

    @Test
    fun noRoots() {
        assertNull(ExportFinder.find(emptyList()))
    }

    @Test
    fun rootWithoutExport() {
        assertNull(ExportFinder.find(listOf(root(screenName = null, programName = null))))
        assertNull(ExportFinder.find(listOf(root(programName = null))))
    }

    @Test
    fun rootWithInvalidExport() {
        assertNull(ExportFinder.find(listOf(root(screenBytes = ByteArray(66)))))
    }

    @Test
    fun upperCaseFileNames() {
        val found = ExportFinder.find(listOf(root("SCREEN.HDS", "PROGAM.HDS")))!!
        assertEquals(128, found.export.width)
        assertEquals(64, found.export.height)
    }

    @Test
    fun usesTheFirstValidRoot() {
        val empty = root(screenName = null, programName = null)
        val first = root()
        val second = root()
        assertEquals(first, ExportFinder.find(listOf(empty, first, second))!!.root)
    }

    @Test
    fun rootThatCannotBeListedIsSkipped() {
        val missing = File(tmp.root, "removed-drive")
        val valid = root()
        assertEquals(valid, ExportFinder.find(listOf(missing, valid))!!.root)
    }
}
```

Add to `ui/StepsTest.kt` (inside the class; add `import com.masjidclock.wfsender.usb.UsbStatus`):

```kotlin
    @Test
    fun createStepsFollowTheUsbStatus() {
        assertEquals(CreateSteps(CURRENT, WAITING, WAITING, WAITING), createSteps(UsbStatus.NO_PERMISSION))
        assertEquals(CreateSteps(DONE, CURRENT, WAITING, WAITING), createSteps(UsbStatus.NO_DRIVE))
        assertEquals(CreateSteps(DONE, DONE, CURRENT, WAITING), createSteps(UsbStatus.NO_EXPORT))
        assertEquals(CreateSteps(DONE, DONE, DONE, CURRENT), createSteps(UsbStatus.FOUND))
    }
```

Add to `send/MessagesTest.kt` (inside the class):

```kotlin
    @Test
    fun createMessages() {
        assertEquals("Found on: SanDisk USB drive — 128 × 64 display", Messages.found("SanDisk USB drive", 128, 64))
        assertEquals("Saved: Downloads/english (1).hdpkg", Messages.saved("english (1).hdpkg"))
        assertEquals("The USB drive has no HD2020 export. Use the U-disk export of HD2020.", Messages.NO_EXPORT)
        assertEquals(
            "Connect the USB drive with the HD2020 export to the phone. Use a USB OTG adapter.",
            Messages.CONNECT_USB,
        )
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :android:testDebugUnitTest`
Expected: FAIL, `Unresolved reference 'ExportFinder'`, `'UsbStatus'`, `'createSteps'`, `'found'`.

- [ ] **Step 3: Implement**

`usb/ExportFinder.kt`:

```kotlin
package com.masjidclock.wfsender.usb

import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.protocol.InvalidExportException
import java.io.File
import java.io.IOException

enum class UsbStatus { NO_PERMISSION, NO_DRIVE, NO_EXPORT, FOUND }

data class FoundExport(val root: File, val export: HdsExport)

/** Finds the HD2020 U-disk export at the root of a removable volume (spec section 4.2, step 3). */
object ExportFinder {

    fun find(roots: List<File>): FoundExport? =
        roots.firstNotNullOfOrNull { root -> load(root)?.let { FoundExport(root, it) } }

    private fun load(root: File): HdsExport? {
        val files = root.listFiles() ?: return null
        val screen = files.firstOrNull { it.isFile && it.name.equals(HdsExport.SCREEN_FILE, ignoreCase = true) }
        val program = files.firstOrNull { it.isFile && it.name.equals(HdsExport.PROGRAM_FILE, ignoreCase = true) }
        if (screen == null || program == null) return null
        return try {
            HdsExport(screen.readBytes(), program.readBytes())
        } catch (e: InvalidExportException) {
            null
        } catch (e: IOException) {
            null
        }
    }
}
```

Append to `ui/Steps.kt` (add `import com.masjidclock.wfsender.usb.UsbStatus`):

```kotlin
/** The state of the 4 steps of the Create page: file access, USB drive, files, save or share. */
data class CreateSteps(val access: StepState, val drive: StepState, val files: StepState, val output: StepState)

fun createSteps(status: UsbStatus): CreateSteps = when (status) {
    UsbStatus.NO_PERMISSION -> CreateSteps(StepState.CURRENT, StepState.WAITING, StepState.WAITING, StepState.WAITING)
    UsbStatus.NO_DRIVE -> CreateSteps(StepState.DONE, StepState.CURRENT, StepState.WAITING, StepState.WAITING)
    UsbStatus.NO_EXPORT -> CreateSteps(StepState.DONE, StepState.DONE, StepState.CURRENT, StepState.WAITING)
    UsbStatus.FOUND -> CreateSteps(StepState.DONE, StepState.DONE, StepState.DONE, StepState.CURRENT)
}
```

In `send/Messages.kt`, replace the `NO_SITE` and `WAIT_FOR_SEND` lines with these, and add the other lines in the object:

```kotlin
    const val NO_SITE = "Load the HD Sender package (.hdpkg) that you received."
    const val WAIT_FOR_SEND = "Wait until the send is complete. Then open the file again."
    const val ALLOW_ACCESS = "HD Sender needs access to files to read the USB drive and to save packages."
    const val CONNECT_USB = "Connect the USB drive with the HD2020 export to the phone. Use a USB OTG adapter."
    const val NO_EXPORT = "The USB drive has no HD2020 export. Use the U-disk export of HD2020."
    const val SAVE_FAILED = "Could not save the file. Check that the phone has free space."
    const val NAME_RULE = "Use only English letters, numbers, spaces, - and _ (maximum 50 characters)."

    fun found(volume: String, width: Int, height: Int): String = "Found on: $volume — $width × $height display"

    fun saved(fileName: String): String = "Saved: Downloads/$fileName"
```

- [ ] **Step 4: Run to verify it passes** — the command of Step 2; all tests pass (`ExportFinderTest` 6, `StepsTest` 7, `MessagesTest` 4).

- [ ] **Step 5: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock && git add app/android && git commit -m "Add USB export finder, Create steps, and Create messages" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Android wiring: permissions, volumes, view models, and screens

No unit tests in this task: it is Android API and UI code. The logic it calls is tested in Tasks 1–5. Verification: build without warnings, all unit tests, and the manual tests in Task 7.

**Files:**
- Create: `usb/StorageAccess.kt`, `usb/RemovableVolumes.kt`, `CreateViewModel.kt`, `ui/Components.kt`, `ui/HomeScreen.kt`, `ui/CreateScreen.kt`, `app/android/src/main/res/xml/file_paths.xml`
- Replace: `AppViewModel.kt`, `ui/Screens.kt`, `MainActivity.kt` (full contents below)
- Modify: `AndroidManifest.xml`

(All Kotlin paths are under `app/android/src/main/kotlin/com/masjidclock/wfsender/`.)

**Interfaces:**
- Consumes: Tasks 1–5; existing `CardNetwork`, `ConnectionCheck`, `ConnectionStatus`, `CardSender`, `SendGate`, `SendLog`, `SendState`, `sendCard`, `Messages`, `ZipImporter`, `stepStates`, `StepState`.
- Produces: `enum class Page { HOME, SEND, CREATE }`; `AppViewModel.openSend()`, `openCreate()`, `goHome()`, `importPackage(uri)`; `CreateViewModel`.

- [ ] **Step 1: Manifest and FileProvider paths**

`app/android/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="share" path="share/" />
</paths>
```

In `AndroidManifest.xml`:
- change the `<manifest ...>` start tag to `<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">`;
- after the `ACCESS_NETWORK_STATE` line, add:

```xml
    <uses-permission
        android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="29" />
    <uses-permission
        android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="29" />
```

- in `<application ...>`, add the attribute `android:requestLegacyExternalStorage="true"`;
- before `</application>`, add:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.files"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 2: `usb/StorageAccess.kt` and `usb/RemovableVolumes.kt`**

```kotlin
package com.masjidclock.wfsender.usb

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/** File access for the Create flow (spec section 5.5). */
object StorageAccess {
    val LEGACY_PERMISSIONS = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun granted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            LEGACY_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        }

    /** Opens the "All files access" settings page of this app. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun openSettings(context: Context) {
        val forApp = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        try {
            context.startActivity(forApp)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}
```

```kotlin
package com.masjidclock.wfsender.usb

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

data class Volume(val name: String, val root: File)

/** The mounted removable volumes: USB drives and SD cards (spec section 5.1). */
class RemovableVolumes(private val context: Context) {

    fun list(): List<Volume> {
        val storage = context.getSystemService(StorageManager::class.java)
        return storage.storageVolumes
            .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { volume -> rootOf(volume)?.let { Volume(volume.getDescription(context), it) } }
    }

    private fun rootOf(volume: StorageVolume): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return volume.directory
        // Android 7-10: a removable volume is mounted at /storage/<UUID>.
        val uuid = volume.uuid ?: return null
        return context.getExternalFilesDirs(null).filterNotNull()
            .map { File(it.path.substringBefore("/Android/data/")) }
            .firstOrNull { it.name.equals(uuid, ignoreCase = true) }
    }
}
```

- [ ] **Step 3: `CreateViewModel.kt`**

```kotlin
package com.masjidclock.wfsender

import android.app.Application
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masjidclock.wfsender.pkg.Downloads
import com.masjidclock.wfsender.pkg.PackageName
import com.masjidclock.wfsender.pkg.PackageWriter
import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.usb.ExportFinder
import com.masjidclock.wfsender.usb.RemovableVolumes
import com.masjidclock.wfsender.usb.StorageAccess
import com.masjidclock.wfsender.usb.UsbStatus
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UsbState(val status: UsbStatus, val volumeName: String = "", val export: HdsExport? = null)

/** The Create page (spec section 4.2). */
class CreateViewModel(app: Application) : AndroidViewModel(app) {

    private val volumes = RemovableVolumes(app)
    private var checkJob: Job? = null

    private val _usb = MutableStateFlow(UsbState(UsbStatus.NO_PERMISSION))
    val usb: StateFlow<UsbState> = _usb

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result

    /** Checks the permission and the USB drive every 2 seconds while the page is visible. */
    fun startChecks() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            while (isActive) {
                _usb.value = withContext(Dispatchers.IO) { check() }
                delay(2_000)
            }
        }
    }

    fun stopChecks() {
        checkJob?.cancel()
        checkJob = null
    }

    private fun check(): UsbState {
        if (!StorageAccess.granted(getApplication())) return UsbState(UsbStatus.NO_PERMISSION)
        val list = volumes.list()
        if (list.isEmpty()) return UsbState(UsbStatus.NO_DRIVE)
        val found = ExportFinder.find(list.map { it.root }) ?: return UsbState(UsbStatus.NO_EXPORT)
        val name = list.first { it.root == found.root }.name
        return UsbState(UsbStatus.FOUND, name, found.export)
    }

    /** [name] is a clean name (PackageName.clean). */
    fun save(name: String) {
        val export = _usb.value.export ?: return
        viewModelScope.launch {
            _result.value = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = Downloads.save(dir, PackageName.fileName(name)) { PackageWriter.write(export, it) }
                    MediaScannerConnection.scanFile(getApplication(), arrayOf(file.absolutePath), null, null)
                    Messages.saved(file.name)
                } catch (e: IOException) {
                    Messages.SAVE_FAILED
                } catch (e: SecurityException) {
                    Messages.SAVE_FAILED
                }
            }
        }
    }

    /** Writes the package to the cache folder and returns a shareable Uri, or null on failure. */
    suspend fun shareFile(name: String): Uri? {
        val export = _usb.value.export ?: return null
        val app = getApplication<Application>()
        return withContext(Dispatchers.IO) {
            try {
                val dir = File(app.cacheDir, "share")
                dir.deleteRecursively()
                dir.mkdirs()
                val file = File(dir, PackageName.fileName(name))
                PackageWriter.write(export, file.outputStream())
                FileProvider.getUriForFile(app, "${app.packageName}.files", file)
            } catch (e: IOException) {
                _result.value = Messages.SAVE_FAILED
                null
            }
        }
    }

    override fun onCleared() {
        stopChecks()
    }
}
```

- [ ] **Step 4: Replace `AppViewModel.kt`**

```kotlin
package com.masjidclock.wfsender

import android.app.Application
import android.content.ContentResolver
import android.net.Network
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masjidclock.wfsender.net.CardNetwork
import com.masjidclock.wfsender.net.ConnectionCheck
import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.protocol.CardSender
import com.masjidclock.wfsender.protocol.SendListener
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.send.SendGate
import com.masjidclock.wfsender.send.SendLog
import com.masjidclock.wfsender.send.SendState
import com.masjidclock.wfsender.send.sendCard
import com.masjidclock.wfsender.site.Card
import com.masjidclock.wfsender.site.ImportException
import com.masjidclock.wfsender.site.Site
import com.masjidclock.wfsender.site.ZipImporter
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Page { HOME, SEND, CREATE }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val siteDir = File(app.filesDir, "site")
    private val importer = ZipImporter()
    private val network = CardNetwork(app).also { it.start() }
    private var checkJob: Job? = null
    private var check: ConnectionCheck<Network>? = null
    private val gate = SendGate()

    private val _page = MutableStateFlow(Page.HOME)
    val page: StateFlow<Page> = _page

    private val _site = MutableStateFlow(ZipImporter.load(siteDir))
    val site: StateFlow<Site?> = _site

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError

    private val _selectedCard = MutableStateFlow<Card?>(null)
    val selectedCard: StateFlow<Card?> = _selectedCard

    private val _connection = MutableStateFlow(ConnectionStatus.NOT_CONNECTED)
    val connection: StateFlow<ConnectionStatus> = _connection

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState

    /** Opens the Send page. A package holds 1 display, so it is selected without an input. */
    fun openSend() {
        _page.value = Page.SEND
        // selectCard does nothing during a send, so an "Open with" during a send cannot change the card.
        _site.value?.cards?.firstOrNull()?.let(::selectCard)
    }

    fun openCreate() {
        if (gate.busy) return
        stopConnectionChecks()
        _page.value = Page.CREATE
    }

    /** Back to the home screen. Not possible during a send. */
    fun goHome() {
        if (gate.busy) return
        stopConnectionChecks()
        _sendState.value = SendState.Idle
        _page.value = Page.HOME
    }

    fun importPackage(uri: Uri) {
        if (gate.busy) {
            _importError.value = Messages.WAIT_FOR_SEND
            return
        }
        openSend()
        viewModelScope.launch {
            _importError.value = null
            val resolver = getApplication<Application>().contentResolver
            try {
                val imported = withContext(Dispatchers.IO) {
                    val name = displayName(resolver, uri) ?: ""
                    val input = resolver.openInputStream(uri) ?: throw ImportException("Could not open the file.")
                    input.use { importer.import(it, name, siteDir) }
                }
                _site.value = imported
                selectCard(imported.cards.first())
            } catch (e: ImportException) {
                _importError.value = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // For example FileNotFoundException when the sharing app no longer gives access to the file.
                _importError.value = "Could not open the file."
            }
        }
    }

    private fun selectCard(card: Card) {
        if (gate.busy) return
        _selectedCard.value = card
        _sendState.value = SendState.Idle
        stopConnectionChecks()
        startConnectionChecks()
    }

    /** After a result: back to step 2 for the same display. The connection check starts again. */
    fun sendAgain() {
        _selectedCard.value?.let(::selectCard)
    }

    private fun startConnectionChecks() {
        _connection.value = ConnectionStatus.NOT_CONNECTED
        val newCheck = ConnectionCheck({ network.current }) { wifi -> CardSender(network.connectorFor(wifi)).probe() }
        check = newCheck
        checkJob = viewModelScope.launch {
            while (isActive) {
                val status = withContext(Dispatchers.IO) { newCheck.next() } ?: break
                _connection.value = status
                delay(2_000)
            }
        }
    }

    /** No more probes: after a send, each probe makes the display flicker. */
    private fun stopConnectionChecks() {
        check?.stop()
        check = null
        checkJob?.cancel()
        checkJob = null
    }

    fun send() {
        val card = _selectedCard.value ?: return
        val wifi = network.current ?: return
        if (!gate.tryStart()) return
        stopConnectionChecks()
        _sendState.value = SendState.Sending(0, 0)
        viewModelScope.launch {
            val log = SendLog(
                siteName = _site.value?.name ?: "",
                card = card,
                appVersion = BuildConfig.VERSION_NAME,
                androidVersion = Build.VERSION.RELEASE,
            )
            val progress = object : SendListener by log {
                override fun onFrameSent(index: Int, total: Int, frame: ByteArray) {
                    log.onFrameSent(index, total, frame)
                    _sendState.value = SendState.Sending(index, total)
                }
            }
            try {
                val result = withContext(Dispatchers.IO) {
                    val sender = CardSender(network.connectorFor(wifi), isNetworkLost = { network.current != wifi })
                    sendCard(card, sender, progress)
                }
                log.finish(result)
                _sendState.value =
                    SendState.Finished(result == SendResult.Success, Messages.forResult(result), log.text())
            } finally {
                gate.finish()
            }
        }
    }

    fun clearImportError() {
        _importError.value = null
    }

    override fun onCleared() {
        stopConnectionChecks()
        network.stop()
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment
}
```


- [ ] **Step 5: `ui/Components.kt`**

```kotlin
package com.masjidclock.wfsender.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

internal val GREEN = Color(0xFF1B5E20)
internal val RED = Color(0xFFB00020)
internal val GREY = Color(0xFF757575)

/** A page title with a Back button. */
@Composable
internal fun PageTitle(title: String, onBack: () -> Unit, backEnabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, enabled = backEnabled) { Text("‹ Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

/** 1 numbered step with its mark: ✔ done, ● current, ○ waiting. */
@Composable
internal fun Step(number: Int, title: String, state: StepState, content: @Composable ColumnScope.() -> Unit) {
    val (mark, color) = when (state) {
        StepState.DONE -> "✔" to GREEN
        StepState.CURRENT -> "●" to MaterialTheme.colorScheme.primary
        StepState.WAITING -> "○" to GREY
    }
    Row {
        Text(mark, color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$number  $title",
                style = MaterialTheme.typography.titleMedium,
                color = if (state == StepState.WAITING) GREY else Color.Unspecified,
            )
            content()
        }
    }
}

@Composable
internal fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
```

- [ ] **Step 6: `ui/HomeScreen.kt`**

```kotlin
package com.masjidclock.wfsender.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onSend: () -> Unit, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("HD Sender", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onSend, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("Send", style = MaterialTheme.typography.titleLarge)
        }
        Text("Send a package to a display.")
        OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("Create", style = MaterialTheme.typography.titleLarge)
        }
        Text("Make a package from an HD2020 export on a USB drive.")
    }
}
```

- [ ] **Step 7: `ui/CreateScreen.kt`**

```kotlin
package com.masjidclock.wfsender.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.CreateViewModel
import com.masjidclock.wfsender.pkg.PackageName
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.usb.StorageAccess
import com.masjidclock.wfsender.usb.UsbStatus
import kotlinx.coroutines.launch

private enum class NameAction { SAVE, SHARE }

@Composable
fun CreateScreen(model: CreateViewModel, onBack: () -> Unit) {
    val usb by model.usb.collectAsStateWithLifecycle()
    val result by model.result.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf<NameAction?>(null) }
    val askLegacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val steps = createSteps(usb.status)

    // The checks run only while the page is visible; they start again when the person returns from Settings.
    LifecycleResumeEffect(Unit) {
        model.startChecks()
        onPauseOrDispose { model.stopChecks() }
    }
    BackHandler { onBack() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageTitle("Create a package", onBack)

        Step(1, "Allow file access", steps.access) {
            if (usb.status == UsbStatus.NO_PERMISSION) {
                Text(Messages.ALLOW_ACCESS)
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        StorageAccess.openSettings(context)
                    } else {
                        askLegacy.launch(StorageAccess.LEGACY_PERMISSIONS)
                    }
                }) { Text("Allow file access") }
            }
        }
        HorizontalDivider()

        Step(2, "Connect the USB drive", steps.drive) {
            if (usb.status == UsbStatus.NO_DRIVE) Text(Messages.CONNECT_USB)
        }
        HorizontalDivider()

        Step(3, "Check the files", steps.files) {
            when (usb.status) {
                UsbStatus.NO_EXPORT -> Text(Messages.NO_EXPORT, color = RED)
                UsbStatus.FOUND -> {
                    val export = usb.export!!
                    Text(Messages.found(usb.volumeName, export.width, export.height), color = GREEN, fontWeight = FontWeight.Bold)
                }
                else -> Unit
            }
        }
        HorizontalDivider()

        Step(4, "Save or share", steps.output) {
            if (usb.status == UsbStatus.FOUND) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { naming = NameAction.SAVE }) { Text("Save") }
                    OutlinedButton(onClick = { naming = NameAction.SHARE }) { Text("Share") }
                }
            }
            result?.let { Text(it, fontWeight = FontWeight.Bold) }
        }
    }

    naming?.let { action ->
        NameDialog(
            onDismiss = { naming = null },
            onConfirm = { name ->
                naming = null
                when (action) {
                    NameAction.SAVE -> model.save(name)
                    NameAction.SHARE -> scope.launch {
                        model.shareFile(name)?.let { uri -> context.startActivity(shareIntent(uri)) }
                    }
                }
            },
        )
    }
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val clean = PackageName.clean(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("for example masjid-north-english") },
                )
                if (text.isNotBlank() && clean == null) Text(Messages.NAME_RULE, color = RED)
            }
        },
        confirmButton = { TextButton(onClick = { clean?.let(onConfirm) }, enabled = clean != null) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun shareIntent(uri: Uri): Intent {
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/octet-stream")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri("", uri)
    return Intent.createChooser(send, "Share package")
}
```

- [ ] **Step 8: Replace `ui/Screens.kt`**

```kotlin
package com.masjidclock.wfsender.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.AppViewModel
import com.masjidclock.wfsender.CreateViewModel
import com.masjidclock.wfsender.Page
import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.send.SendState

/** The file picker shows all files: Android has no type for .hdpkg. The app checks the name. */
private val PACKAGE_TYPES = arrayOf("*/*")

@Composable
fun WfSenderApp(model: AppViewModel, create: CreateViewModel) {
    val page by model.page.collectAsStateWithLifecycle()
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                when (page) {
                    Page.HOME -> HomeScreen(onSend = model::openSend, onCreate = model::openCreate)
                    Page.SEND -> SendScreen(model)
                    Page.CREATE -> CreateScreen(create, onBack = model::goHome)
                }
            }
        }
    }
}

@Composable
private fun SendScreen(model: AppViewModel) {
    val site by model.site.collectAsStateWithLifecycle()
    val importError by model.importError.collectAsStateWithLifecycle()
    val card by model.selectedCard.collectAsStateWithLifecycle()
    val connection by model.connection.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openPackage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importPackage(uri)
    }
    var confirming by remember { mutableStateOf(false) }
    val sending = sendState is SendState.Sending
    val loaded = site != null && card != null
    val steps = stepStates(loaded, connection, sendState)

    // During a send, the back button does nothing and the screen stays on.
    BackHandler { if (!sending) model.goHome() }
    if (sending) KeepScreenOn()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageTitle("Send", onBack = model::goHome, backEnabled = !sending)

        Step(1, "Load file", steps.load) {
            val shown = card
            if (site == null || shown == null) {
                Text(Messages.NO_SITE)
            } else {
                Text("${site!!.name}.hdpkg", fontWeight = FontWeight.Bold)
                Text("Display: ${Messages.cardRow(shown)}")
            }
            OutlinedButton(onClick = { openPackage.launch(PACKAGE_TYPES) }, enabled = !sending) {
                Text(if (site == null) "Load file" else "Load another file")
            }
        }
        HorizontalDivider()

        Step(2, "Connect to the display WiFi", steps.connect) {
            if (steps.connect != StepState.WAITING) {
                Text(Messages.WIFI_INSTRUCTIONS)
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                    enabled = !sending,
                ) { Text("Open WiFi settings") }
                if (sendState == SendState.Idle) {
                    val (status, color) = when (connection) {
                        ConnectionStatus.NOT_CONNECTED -> Messages.NOT_CONNECTED to RED
                        ConnectionStatus.NO_DISPLAY -> Messages.NO_DISPLAY to RED
                        ConnectionStatus.DISPLAY_FOUND -> Messages.DISPLAY_FOUND to GREEN
                    }
                    Text("Status: $status", color = color, fontWeight = FontWeight.Bold)
                }
            }
        }
        HorizontalDivider()

        Step(3, "Send", steps.send) {
            when (val state = sendState) {
                SendState.Idle -> Button(
                    onClick = { confirming = true },
                    enabled = loaded && connection == ConnectionStatus.DISPLAY_FOUND,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Send") }
                is SendState.Sending -> {
                    if (state.total == 0) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Connecting to the display…")
                    } else {
                        LinearProgressIndicator(
                            progress = { state.sent.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Sending… ${state.sent} of ${state.total}")
                    }
                    Text("Keep the phone near the display.")
                }
                is SendState.Finished -> {
                    Text(state.message, color = if (state.success) GREEN else RED, fontWeight = FontWeight.Bold)
                    Button(onClick = model::sendAgain, modifier = Modifier.fillMaxWidth()) { Text("Send again") }
                    OutlinedButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, state.log)
                        context.startActivity(Intent.createChooser(share, "Share log"))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Share log") }
                }
            }
        }
    }

    val selected = card
    if (confirming && selected != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Send the program?") },
            text = { Text(Messages.confirmation(selected)) },
            confirmButton = { TextButton(onClick = { confirming = false; model.send() }) { Text("Yes, send") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
    importError?.let { message ->
        AlertDialog(
            onDismissRequest = model::clearImportError,
            confirmButton = { TextButton(onClick = model::clearImportError) { Text("OK") } },
            title = { Text("Cannot open the file") },
            text = { Text(message) },
        )
    }
}
```

- [ ] **Step 9: Replace `MainActivity.kt`**

```kotlin
package com.masjidclock.wfsender

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.masjidclock.wfsender.ui.WfSenderApp

class MainActivity : ComponentActivity() {

    private val model: AppViewModel by viewModels()
    private val create: CreateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 draws apps edge to edge. Dark icons on a transparent bar keep the status bar visible
        // on the light page; the page itself is padded below the status bar (safeDrawingPadding).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { WfSenderApp(model, create) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A package from "Open with" (ACTION_VIEW) or from the share sheet (ACTION_SEND) opens the Send page. */
    private fun handleIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        model.importPackage(uri)
    }
}
```

- [ ] **Step 10: Build and run all tests**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew clean :protocol:test :android:testDebugUnitTest :android:assembleRelease`
Expected: `BUILD SUCCESSFUL`, no `e:` or `w:` lines, all tests pass. If lint (`lintVitalRelease`) fails, read its report, fix only the reported issue, and record a ruling.

- [ ] **Step 11: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock && git add app/android && git commit -m "Add home screen and Create flow with USB access, Save, and Share" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Version, README, and release build

**Files:**
- Modify: `app/android/build.gradle.kts` (`versionCode = 4`, `versionName = "0.3.0"`)
- Modify: `app/README.md`

- [ ] **Step 1: Version**

In `app/android/build.gradle.kts`: `versionCode = 3` → `versionCode = 4`, `versionName = "0.2.0"` → `versionName = "0.3.0"`.

- [ ] **Step 2: README**

Replace the section `## Make the ZIP file of a display` with:

```markdown
## Make a package (.hdpkg)

A package holds the program of exactly 1 display. It is a ZIP file with the extension `.hdpkg`.

**On the phone (recommended):**

1. In HD2020, export the program of the display with the U-disk option to a USB drive.
2. Connect the USB drive to the phone with a USB OTG adapter.
3. In HD Sender, tap **Create**. The first time, allow file access (on Android 11 and later,
   turn on "Allow access to manage all files" for HD Sender, then go back).
4. When the app shows "Found on: … display", tap **Save** (to Downloads) or **Share** (for example
   to WhatsApp), and type a file name, for example `masjid-north-english`.

**On a PC:** put `screen.hds` and `progam.hds` from the U-disk export into a ZIP file, at its root,
and rename the ZIP to `<name>.hdpkg`. Use only English letters, numbers, spaces, `-` and `_` in the name.

The app ignores `set.hds`, `smart.ss`, and `font.hds`. It never sends them.
The Send flow accepts only `.hdpkg` files.
```

In section `## Use`, add before the 3 steps: `On the home screen, tap **Send**.` In step 1, change `open the ZIP with the app` to `open the .hdpkg file with the app`.

Add to the manual test list:

```markdown
7. Create: the first Create asks for file access; the second does not.
8. Create without a USB drive shows the message; when the drive is connected, the app finds it.
9. Create with a USB drive without an export shows "The USB drive has no HD2020 export".
10. Save twice with the same name: Downloads has `name.hdpkg` and `name (1).hdpkg`.
11. Share to WhatsApp, open the file with "Open with" → HD Sender, and send it to the display.
12. Load a `.zip` file in Send: the app shows "This file is not an HD Sender package".
```

- [ ] **Step 3: Release build and signature check**

Run:

```bash
cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test :android:testDebugUnitTest :android:assembleRelease
~/Android/Sdk/build-tools/36.0.0/aapt2 dump badging android/build/outputs/apk/release/android-release.apk | grep -E "^package|application-label"
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ~/Android/Sdk/build-tools/36.0.0/apksigner verify --print-certs android/build/outputs/apk/release/android-release.apk | grep "SHA-256"
```

Expected: `BUILD SUCCESSFUL`; `package: name='com.hdsender.app' versionCode='4' versionName='0.3.0'`; `application-label:'HD Sender'`; certificate SHA-256 `f1ef44b2f8a84ed57c3e376ea8aebe28f0a748f5f5ec07d66300bf37eb9f648a`.

- [ ] **Step 4: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock && git add app/android/build.gradle.kts app/README.md && git commit -m "HD Sender 0.3.0: README for Create and .hdpkg" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```
