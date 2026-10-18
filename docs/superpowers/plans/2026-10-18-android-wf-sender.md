# Android WF Sender Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android app that sends HD2020 U-disk exports (`screen.hds` + `progam.hds`) from a site ZIP to a Huidu WF card over the card WiFi.

**Architecture:** One Gradle project in `app/` with 2 modules. `protocol` is a pure Kotlin JVM library (frames, transfer, sender with retries) that is tested against the real HD2020 capture and a local fake card. `android` is a Jetpack Compose app (ZIP import, WiFi-bound sockets, 2 screens) that calls `protocol` on a background thread.

**Tech Stack:** Kotlin 2.1.20, Gradle 8.11.1, Android Gradle Plugin 8.9.2, Jetpack Compose (BOM 2025.02.00, Material 3), kotlinx-coroutines 1.10.1, JUnit 4.13.2, Java 21 to run Gradle, Android SDK at `/home/towfique/Android/Sdk`.

**Spec:** `docs/superpowers/specs/2026-10-18-android-wf-sender-design.md`

## Global Constraints

- All Android code is in `app/`. Branch: `android-wf-sender`.
- Run every Gradle command from `/home/towfique/workspace/led-masjid-clock/app` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`. Java 27 is also installed and must not be used.
- Package names: `com.masjidclock.wfsender.protocol` (module `protocol`), `com.masjidclock.wfsender` (module `android`). Application ID: `com.masjidclock.wfsender`.
- Minimum Android version: 7.0 (`minSdk = 24`). `compileSdk = 35`, `targetSdk = 35`.
- Card address: `192.168.4.1`, TCP port `6101`.
- The program file name is `progam.hds` (HD2020 spelling). Never use `program.hds`.
- Never send `set.hds`, `smart.ss`, or `font.hds`. The ZIP importer must not unpack them.
- Timeouts: probe connect and reply 3 s; send connect 5 s; ACK 10 s. Maximum 3 send attempts. Connection check every 2 s.
- Chunk size: 1,000 bytes. ZIP unpacked size limit: 20 MB.
- Permissions: only `INTERNET` and `ACCESS_NETWORK_STATE`. No location permission.
- All screens are in English. The user messages must be exactly the texts in spec section 6.2 and in `Messages.kt` (Task 7).
- The signing key and its passwords must never be committed.
- Every commit message ends with the line `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

## Review Focus

1. A card ACK that arrives in 2 or more TCP reads (weak WiFi) must be read correctly, not treated as an error. Test: Task 4 `succeedsWhenAckArrivesInPieces`.
2. An ACK with status 0 for a different command, before the expected ACK, must be ignored, not treated as the answer. Test: Task 4 `ignoresAckForOtherCommand`.
3. A ZIP made on macOS or with Windows tools (`__MACOSX/` entries, `._` files, `\` separators, upper-case `SCREEN.HDS`) must import normally. Test: Task 6 `acceptsZipFromMacAndWindowsTools`.
4. Two card folders with the same name in different parents (`m2/english`, `m3/english`) must stay 2 separate cards with different labels. Test: Task 6 `labelsAreRelativePaths`.
5. A program with more than 253 chunks must wrap the 8-bit sequence number and keep the full 16-bit chunk index. Test: Task 3 `largeProgramWrapsSequenceAndKeepsChunkIndex`.

## File Structure

```
app/
├── .gitignore                         build output, local.properties, keystore; re-includes gradle-wrapper.jar
├── README.md                          build, install, ZIP format, signing key, manual tests (Task 9)
├── settings.gradle.kts                modules
├── build.gradle.kts                   plugin versions (apply false)
├── gradle.properties
├── gradle/libs.versions.toml          all versions
├── gradlew, gradlew.bat, gradle/wrapper/*
├── protocol/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/masjidclock/wfsender/protocol/
│       │   ├── HtFrame.kt             HT frame builder, Ack parser, UnexpectedReplyException
│       │   ├── HdsExport.kt           1 card export, checks, width/height
│       │   ├── ProgramTransfer.kt     ordered frame list for 1 transfer
│       │   └── CardSender.kt          send with retries, probe, CardConnector, SendResult, SendListener
│       └── test/
│           ├── kotlin/com/masjidclock/wfsender/protocol/
│           │   ├── HtFrameTest.kt
│           │   ├── HdsExportTest.kt
│           │   ├── ProgramTransferTest.kt
│           │   ├── FakeCard.kt        local TCP fake card for tests
│           │   ├── CardSenderTest.kt
│           │   └── CardProbeTest.kt
│           └── resources/
│               ├── export-128x64/screen.hds
│               ├── export-128x64/progam.hds
│               └── captured-transfer.bin   16 frames of 1 HD2020 transfer, transfer ID 0x030c
└── android/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   └── kotlin/com/masjidclock/wfsender/
        │       ├── site/ZipImporter.kt    ZIP import, Site, Card, ImportException
        │       ├── send/Messages.kt       all user texts
        │       ├── send/SendLog.kt        log of 1 send (SendListener)
        │       ├── net/CardNetwork.kt     WiFi network tracking, bound sockets
        │       ├── AppViewModel.kt        state, import, connection check, send
        │       ├── MainActivity.kt        intents ("Open with", share), setContent
        │       └── ui/Screens.kt          SiteScreen, SendScreen
        └── test/kotlin/com/masjidclock/wfsender/
            ├── site/ZipImporterTest.kt
            ├── send/MessagesTest.kt
            └── send/SendLogTest.kt
```

---

### Task 1: Gradle project and HT frames

**Files:**
- Create: `app/.gitignore`, `app/settings.gradle.kts`, `app/build.gradle.kts`, `app/gradle.properties`, `app/gradle/libs.versions.toml`, `app/protocol/build.gradle.kts`
- Create (generated): `app/gradlew`, `app/gradlew.bat`, `app/gradle/wrapper/gradle-wrapper.jar`, `app/gradle/wrapper/gradle-wrapper.properties`
- Create: `app/protocol/src/main/kotlin/com/masjidclock/wfsender/protocol/HtFrame.kt`
- Test: `app/protocol/src/test/kotlin/com/masjidclock/wfsender/protocol/HtFrameTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `object HtFrame { const val HEADER_LENGTH = 27; const val ACK_LENGTH = 31; fun build(command: Int, sequence: Int, payload: ByteArray): ByteArray }`
  - `class UnexpectedReplyException(message: String) : Exception(message)`
  - `data class Ack(val command: Int, val status: Int)` with `companion object { fun parse(bytes: ByteArray): Ack; fun isHr(bytes: ByteArray): Boolean }`
  - `internal fun ByteArray.toHex(): String`

- [ ] **Step 1: Create the minimal settings file and the wrapper**

Create `app/settings.gradle.kts`:

```kotlin
rootProject.name = "wf-sender"
```

Run:

```bash
cd /home/towfique/workspace/led-masjid-clock/app
GRADLE_BIN=$(ls -d ~/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/bin/gradle | head -1)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk "$GRADLE_BIN" wrapper --gradle-version 8.11.1 --distribution-type bin
ls gradlew gradlew.bat gradle/wrapper/
```

Expected: `gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, `gradle-wrapper.properties` exist.
If `GRADLE_BIN` is empty, download `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip` into the scratchpad, unzip it, and use its `bin/gradle`.

- [ ] **Step 2: Write the build files**

`app/.gitignore` (the root `.gitignore` excludes `*.jar`, so the wrapper jar must be included again):

```gitignore
.gradle/
build/
local.properties
.kotlin/
*.iml
keystore/
keystore.properties
!gradle/wrapper/gradle-wrapper.jar
```

`app/settings.gradle.kts` (replace the minimal file):

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "wf-sender"
include(":protocol")
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
}
```

`app/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`app/gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.9.2"
kotlin = "2.1.20"
coroutines = "1.10.1"
junit = "4.13.2"
composeBom = "2025.02.00"
activityCompose = "1.10.1"
coreKtx = "1.15.0"
lifecycle = "2.8.7"

[libraries]
junit = { module = "junit:junit", version.ref = "junit" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
```

`app/protocol/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
```

- [ ] **Step 3: Write the failing test**

`app/protocol/src/test/kotlin/com/masjidclock/wfsender/protocol/HtFrameTest.kt`:

```kotlin
package com.masjidclock.wfsender.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtFrameTest {

    private fun hex(s: String): ByteArray =
        s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun buildsHeaderPayloadChecksumAndTerminator() {
        val frame = HtFrame.build(0x12, 1, byteArrayOf(0x02, 0x00))
        val expected = hex(
            "48 54 00 1b 00 20 12" +
                " 00".repeat(19) +
                " 01 02 00 00 ec aa"
        )
        assertArrayEquals(expected, frame)
    }

    @Test
    fun buildsTheCapturedStartFrame() {
        // Start frame of the HD2020 transfer in the spike capture (transfer ID 0x030c, 13 chunks).
        val frame = HtFrame.build(0x02, 2, hex("05 0c 03 00 0d"))
        val expected = hex(
            "48 54 00 1b 00 23 02" +
                " 00".repeat(19) +
                " 02 05 0c 03 00 0d 00 ff aa"
        )
        assertArrayEquals(expected, frame)
    }

    @Test
    fun parsesTheCapturedAck() {
        val ack = Ack.parse(hex("485200ff0071faca000d2000018eec00001a001c130000000000000105c0aa"))
        assertEquals(Ack(command = 0x13, status = 0), ack)
    }

    @Test(expected = UnexpectedReplyException::class)
    fun rejectsShortReply() {
        Ack.parse(hex("4852001b"))
    }

    @Test(expected = UnexpectedReplyException::class)
    fun rejectsReplyWithoutHrPrefix() {
        Ack.parse(ByteArray(HtFrame.ACK_LENGTH) { 0x58 })
    }

    @Test
    fun detectsHrPrefix() {
        assertTrue(Ack.isHr(hex("4852")))
        assertFalse(Ack.isHr(hex("4854")))
        assertFalse(Ack.isHr(hex("48")))
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test`
Expected: FAIL. Compilation errors: `Unresolved reference 'HtFrame'`, `Unresolved reference 'Ack'`.

- [ ] **Step 5: Write the implementation**

`app/protocol/src/main/kotlin/com/masjidclock/wfsender/protocol/HtFrame.kt`:

```kotlin
package com.masjidclock.wfsender.protocol

/** Builds HT request frames (spec section 3.2). */
object HtFrame {
    const val HEADER_LENGTH = 27
    const val ACK_LENGTH = 31

    fun build(command: Int, sequence: Int, payload: ByteArray): ByteArray {
        val total = HEADER_LENGTH + payload.size + 3
        require(total <= 0xFFFF) { "frame too long: $total bytes" }
        val frame = ByteArray(total)
        frame[0] = 'H'.code.toByte()
        frame[1] = 'T'.code.toByte()
        frame[3] = 0x1B
        frame[4] = (total shr 8).toByte()
        frame[5] = total.toByte()
        frame[6] = command.toByte()
        frame[26] = sequence.toByte()
        payload.copyInto(frame, HEADER_LENGTH)
        var sum = 0
        for (i in 0 until total - 3) sum += frame[i].toInt() and 0xFF
        frame[total - 3] = (sum shr 8).toByte()
        frame[total - 2] = sum.toByte()
        frame[total - 1] = 0xAA.toByte()
        return frame
    }
}

/** The card sent a reply that is not a valid HR frame. A retry does not help. */
class UnexpectedReplyException(message: String) : Exception(message)

/** A 31-byte HR frame from the card: the command it answers and its status (0 = success). */
data class Ack(val command: Int, val status: Int) {
    companion object {
        fun parse(bytes: ByteArray): Ack {
            if (bytes.size < HtFrame.ACK_LENGTH) {
                throw UnexpectedReplyException("reply too short (${bytes.size} bytes): ${bytes.toHex()}")
            }
            if (!isHr(bytes)) {
                throw UnexpectedReplyException("reply does not start with HR: ${bytes.toHex()}")
            }
            return Ack(command = bytes[20].toInt() and 0xFF, status = bytes[21].toInt() and 0xFF)
        }

        fun isHr(bytes: ByteArray): Boolean =
            bytes.size >= 2 && bytes[0] == 'H'.code.toByte() && bytes[1] == 'R'.code.toByte()
    }
}

internal fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test`
Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 7: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock
git add app/.gitignore app/settings.gradle.kts app/build.gradle.kts app/gradle.properties app/gradle app/gradlew app/gradlew.bat app/protocol
git status --short   # gradle/wrapper/gradle-wrapper.jar must be in the list
git commit -m "Add Gradle project and HT frame builder for WF sender" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: HdsExport

**Files:**
- Create: `app/protocol/src/main/kotlin/com/masjidclock/wfsender/protocol/HdsExport.kt`
- Create: `app/protocol/src/test/resources/export-128x64/screen.hds`, `app/protocol/src/test/resources/export-128x64/progam.hds`, `app/protocol/src/test/resources/captured-transfer.bin`
- Test: `app/protocol/src/test/kotlin/com/masjidclock/wfsender/protocol/HdsExportTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:
  - `class InvalidExportException(message: String) : Exception(message)`
  - `class HdsExport(val screen: ByteArray, val program: ByteArray)` with `val width: Int`, `val height: Int`, and `companion object { const val SCREEN_FILE = "screen.hds"; const val PROGRAM_FILE = "progam.hds"; const val SCREEN_SIZE = 66; fun fromDirectory(dir: java.io.File): HdsExport }`. The constructor throws `InvalidExportException`.

- [ ] **Step 1: Copy the test data**

These files come from the spike (the 128 × 64 WF1 export and the 16 frames of 1 HD2020 transfer with transfer ID `0x030c`).

```bash
SRC=/tmp/claude-1000/-home-towfique-workspace-led-masjid-clock/c56693b1-09ae-4be1-8905-d1a2718e9c28/scratchpad/fixtures
DST=/home/towfique/workspace/led-masjid-clock/app/protocol/src/test/resources
mkdir -p "$DST/export-128x64"
cp "$SRC/screen.hds" "$SRC/progam.hds" "$DST/export-128x64/"
cp "$SRC/captured-transfer.bin" "$DST/"
sha256sum "$DST/captured-transfer.bin"
wc -c "$DST/export-128x64/"*
```

Expected: sha256 `acb5a8974dbee3e21625174a61a88d7357e1c23b9ed996eddd3679415fbb966b`; `screen.hds` 66 bytes, `progam.hds` 12586 bytes, `captured-transfer.bin` 13205 bytes.
If the scratchpad folder is gone, stop and ask the user for the 128 × 64 export and the capture.

- [ ] **Step 2: Write the failing test**

`app/protocol/src/test/kotlin/com/masjidclock/wfsender/protocol/HdsExportTest.kt`:

```kotlin
package com.masjidclock.wfsender.protocol

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HdsExportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val fixture = File(javaClass.getResource("/export-128x64")!!.toURI())

    private fun dirWith(screen: ByteArray?, program: ByteArray?): File {
        val dir = tmp.newFolder()
        screen?.let { File(dir, HdsExport.SCREEN_FILE).writeBytes(it) }
        program?.let { File(dir, HdsExport.PROGRAM_FILE).writeBytes(it) }
        return dir
    }

    private val goodScreen get() = File(fixture, HdsExport.SCREEN_FILE).readBytes()
    private val goodProgram get() = File(fixture, HdsExport.PROGRAM_FILE).readBytes()

    @Test
    fun readsTheSpikeExport() {
        val export = HdsExport.fromDirectory(fixture)
        assertEquals(128, export.width)
        assertEquals(64, export.height)
        assertEquals(66, export.screen.size)
        assertEquals(12586, export.program.size)
    }

    @Test
    fun missingScreenFile() {
        val e = assertThrows(InvalidExportException::class.java) {
            HdsExport.fromDirectory(dirWith(null, goodProgram))
        }
        assertTrue(e.message!!.contains("screen.hds is missing"))
    }

    @Test
    fun missingProgramFile() {
        val e = assertThrows(InvalidExportException::class.java) {
            HdsExport.fromDirectory(dirWith(goodScreen, null))
        }
        assertTrue(e.message!!.contains("progam.hds is missing"))
    }

    @Test
    fun screenWithWrongSize() {
        val e = assertThrows(InvalidExportException::class.java) {
            HdsExport(goodScreen.copyOf(65), goodProgram)
        }
        assertTrue(e.message!!.contains("66 bytes"))
    }

    @Test
    fun screenWithWrongPrefix() {
        val screen = goodScreen.also { it[1] = 'X'.code.toByte() }
        val e = assertThrows(InvalidExportException::class.java) { HdsExport(screen, goodProgram) }
        assertTrue(e.message!!.contains("does not start with HS"))
    }

    @Test
    fun programWithWrongPrefix() {
        val program = goodProgram.also { it[0] = 'X'.code.toByte() }
        val e = assertThrows(InvalidExportException::class.java) { HdsExport(goodScreen, program) }
        assertTrue(e.message!!.contains("does not start with HC"))
    }

    @Test
    fun programTooShort() {
        val e = assertThrows(InvalidExportException::class.java) {
            HdsExport(goodScreen, byteArrayOf('H'.code.toByte(), 'C'.code.toByte(), 0, 0))
        }
        assertTrue(e.message!!.contains("too short"))
    }

    @Test
    fun readsWidthAndHeightAsBigEndian() {
        val screen = goodScreen.also { it[4] = 0x01; it[5] = 0x80.toByte(); it[6] = 0x00; it[7] = 0x40 }
        val export = HdsExport(screen, goodProgram)
        assertEquals(384, export.width)
        assertEquals(64, export.height)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test`
Expected: FAIL. `Unresolved reference 'HdsExport'`.

- [ ] **Step 4: Write the implementation**

`app/protocol/src/main/kotlin/com/masjidclock/wfsender/protocol/HdsExport.kt`:

```kotlin
package com.masjidclock.wfsender.protocol

import java.io.File

/** The export is not a valid HD2020 U-disk export. */
class InvalidExportException(message: String) : Exception(message)

/** One card of an HD2020 U-disk export: screen.hds + progam.hds (spec section 3.1). */
class HdsExport(val screen: ByteArray, val program: ByteArray) {

    init {
        if (screen.size != SCREEN_SIZE) {
            throw InvalidExportException("$SCREEN_FILE must be $SCREEN_SIZE bytes, but it is ${screen.size} bytes")
        }
        if (!screen.startsWith("HS")) throw InvalidExportException("$SCREEN_FILE does not start with HS")
        if (program.size < MIN_PROGRAM_SIZE) throw InvalidExportException("$PROGRAM_FILE is too short")
        if (!program.startsWith("HC")) throw InvalidExportException("$PROGRAM_FILE does not start with HC")
    }

    val width: Int get() = screen.u16(4)
    val height: Int get() = screen.u16(6)

    companion object {
        const val SCREEN_FILE = "screen.hds"
        const val PROGRAM_FILE = "progam.hds"
        const val SCREEN_SIZE = 66

        // The chunk count is written at bytes 9-10, so the program needs at least 11 bytes.
        private const val MIN_PROGRAM_SIZE = 11

        fun fromDirectory(dir: File): HdsExport {
            val screenFile = File(dir, SCREEN_FILE)
            val programFile = File(dir, PROGRAM_FILE)
            if (!screenFile.isFile) throw InvalidExportException("$SCREEN_FILE is missing")
            if (!programFile.isFile) throw InvalidExportException("$PROGRAM_FILE is missing")
            return HdsExport(screenFile.readBytes(), programFile.readBytes())
        }
    }
}

private fun ByteArray.startsWith(prefix: String): Boolean =
    size >= 2 && this[0] == prefix[0].code.toByte() && this[1] == prefix[1].code.toByte()

private fun ByteArray.u16(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test`
Expected: `BUILD SUCCESSFUL`, 14 tests passed.

- [ ] **Step 6: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock
git add app/protocol
git commit -m "Add HdsExport and spike test data" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: ProgramTransfer

**Files:**
- Create: `app/protocol/src/main/kotlin/com/masjidclock/wfsender/protocol/ProgramTransfer.kt`
- Test: `app/protocol/src/test/kotlin/com/masjidclock/wfsender/protocol/ProgramTransferTest.kt`

**Interfaces:**
- Consumes: `HtFrame.build(command, sequence, payload)` (Task 1); `HdsExport` (Task 2).
- Produces:
  - `object Command { const val SCREEN = 0x13; const val START = 0x02; const val DATA = 0x03; const val END = 0x04 }`
  - `object ProgramTransfer { const val CHUNK_SIZE = 1000; fun frames(export: HdsExport, transferId: Int): List<ByteArray> }`

- [ ] **Step 1: Write the failing test**

`app/protocol/src/test/kotlin/com/masjidclock/wfsender/protocol/ProgramTransferTest.kt`:

```kotlin
package com.masjidclock.wfsender.protocol

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramTransferTest {

    private val export = HdsExport.fromDirectory(File(javaClass.getResource("/export-128x64")!!.toURI()))
    private val captured = javaClass.getResourceAsStream("/captured-transfer.bin")!!.readBytes()

    private fun ByteArray.u16(offset: Int) = ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    @Test
    fun matchesTheHd2020CaptureByteForByte() {
        val frames = ProgramTransfer.frames(export, transferId = 0x030c)
        assertEquals(16, frames.size)
        val all = frames.fold(ByteArray(0)) { acc, f -> acc + f }
        assertArrayEquals(captured, all)
    }

    @Test
    fun framesAreInTheSpecOrder() {
        val frames = ProgramTransfer.frames(export, transferId = 0x1234)
        val commands = frames.map { it[6].toInt() and 0xFF }
        assertEquals(listOf(0x13, 0x02) + List(13) { 0x03 } + listOf(0x04), commands)
        val sequences = frames.map { it[26].toInt() and 0xFF }
        assertEquals((1..16).toList(), sequences)
        // Transfer ID is little-endian in START, DATA and END.
        assertEquals(0x34, frames[1][28].toInt() and 0xFF)
        assertEquals(0x12, frames[1][29].toInt() and 0xFF)
    }

    @Test
    fun doesNotChangeTheExport() {
        val before = export.program.copyOf()
        ProgramTransfer.frames(export, transferId = 1)
        assertArrayEquals(before, export.program)
    }

    @Test
    fun largeProgramWrapsSequenceAndKeepsChunkIndex() {
        val program = ByteArray(300 * 1000 + 5).also { it[0] = 'H'.code.toByte(); it[1] = 'C'.code.toByte() }
        val big = HdsExport(export.screen, program)
        val frames = ProgramTransfer.frames(big, transferId = 7)
        // screen + start + 301 data + end
        assertEquals(304, frames.size)
        // Chunk count in the START payload (bytes 30-31) and in the HC header (data frame 0, payload bytes 9-10).
        assertEquals(301, frames[1].u16(30))
        assertEquals(301, frames[2].u16(32 + 9))
        // Data frame for chunk 254 is frames[256]: sequence (3 + 254) wraps to 1, chunk index stays 254.
        val chunk254 = frames[256]
        assertEquals(1, chunk254[26].toInt() and 0xFF)
        assertEquals(254, chunk254.u16(30))
        // The last chunk has 5 bytes: 27 header + 5 payload prefix + 5 data + 3 trailer.
        assertEquals(40, frames[302].size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test`
Expected: FAIL. `Unresolved reference 'ProgramTransfer'`.

- [ ] **Step 3: Write the implementation**

`app/protocol/src/main/kotlin/com/masjidclock/wfsender/protocol/ProgramTransfer.kt`:

```kotlin
package com.masjidclock.wfsender.protocol

object Command {
    const val SCREEN = 0x13
    const val START = 0x02
    const val DATA = 0x03
    const val END = 0x04
}

/** Makes the ordered frames of 1 program transfer (spec section 3.3). */
object ProgramTransfer {
    const val CHUNK_SIZE = 1000

    fun frames(export: HdsExport, transferId: Int): List<ByteArray> {
        require(transferId in 0..0xFFFF) { "transfer ID out of range: $transferId" }
        val program = export.program.copyOf()
        val chunkCount = (program.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        require(chunkCount <= 0xFFFF) { "program too large: ${program.size} bytes" }

        // HD2020 writes the chunk count into the HC header. The file on disk has 0 there.
        program[9] = (chunkCount shr 8).toByte()
        program[10] = chunkCount.toByte()

        val tid = byteArrayOf(transferId.toByte(), (transferId shr 8).toByte())
        val frames = ArrayList<ByteArray>(chunkCount + 3)
        frames += HtFrame.build(Command.SCREEN, 1, export.screen)
        frames += HtFrame.build(Command.START, 2, byteArrayOf(0x05) + tid + u16be(chunkCount))
        for (i in 0 until chunkCount) {
            val chunk = program.copyOfRange(i * CHUNK_SIZE, minOf(program.size, (i + 1) * CHUNK_SIZE))
            frames += HtFrame.build(Command.DATA, 3 + i, byteArrayOf(0x05) + tid + u16be(i) + chunk)
        }
        frames += HtFrame.build(Command.END, 3 + chunkCount, byteArrayOf(0x03) + tid)
        return frames
    }

    private fun u16be(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test`
Expected: `BUILD SUCCESSFUL`, 18 tests passed.

- [ ] **Step 5: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock
git add app/protocol
git commit -m "Add ProgramTransfer, verified against the HD2020 capture" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: CardSender send with retries

**Files:**
- Create: `app/protocol/src/main/kotlin/com/masjidclock/wfsender/protocol/CardSender.kt`
- Create: `app/protocol/src/test/kotlin/com/masjidclock/wfsender/protocol/FakeCard.kt`
- Test: `app/protocol/src/test/kotlin/com/masjidclock/wfsender/protocol/CardSenderTest.kt`

**Interfaces:**
- Consumes: `HtFrame`, `Ack`, `UnexpectedReplyException` (Task 1); `HdsExport` (Task 2); `ProgramTransfer.frames` (Task 3).
- Produces:
  - `fun interface CardConnector { fun connect(host: String, port: Int, timeoutMs: Int): java.net.Socket }`
  - `enum class FailureReason { NETWORK, NETWORK_LOST, REJECTED, UNEXPECTED_REPLY }`
  - `sealed class SendResult { object Success : SendResult(); data class Failed(val reason: FailureReason, val status: Int?, val detail: String) : SendResult() }`
  - `interface SendListener { fun onAttemptStarted(attempt: Int, transferId: Int) {}; fun onFrameSent(index: Int, total: Int, frame: ByteArray) {}; fun onAck(ack: Ack) {}; fun onAttemptFailed(attempt: Int, error: String) {} }`
  - `class CardSender(connector: CardConnector, host: String = DEFAULT_HOST, port: Int = DEFAULT_PORT, connectTimeoutMs: Int = 5_000, ackTimeoutMs: Int = 10_000, maxAttempts: Int = 3, probeTimeoutMs: Int = 3_000, newTransferId: () -> Int = { Random.nextInt(0x10000) }, isNetworkLost: () -> Boolean = { false })` with `fun send(export: HdsExport, listener: SendListener = object : SendListener {}): SendResult` (blocking) and `companion object { const val DEFAULT_HOST = "192.168.4.1"; const val DEFAULT_PORT = 6101 }`. `fun probe(): Boolean` is added in Task 5.

- [ ] **Step 1: Write the fake card**

`app/protocol/src/test/kotlin/com/masjidclock/wfsender/protocol/FakeCard.kt`:

```kotlin
package com.masjidclock.wfsender.protocol

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * A local TCP server that acts as a WF card. [handler] runs once for each connection,
 * with the 1-based connection number. When the handler returns, the connection closes.
 */
class FakeCard(
    private val handler: FakeCard.(connection: Int, input: InputStream, output: OutputStream) -> Unit,
) : Closeable {

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    val connections = AtomicInteger()
    val received = CopyOnWriteArrayList<ByteArray>()

    val connector = CardConnector { _, _, timeoutMs ->
        Socket().apply { connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), timeoutMs) }
    }

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                val number = connections.incrementAndGet()
                thread(isDaemon = true) {
                    socket.use {
                        try {
                            handler(this@FakeCard, number, it.getInputStream(), it.getOutputStream())
                        } catch (e: IOException) {
                            // The client closed the connection.
                        }
                    }
                }
            }
        }
    }

    /** Reads 1 HT frame and records it. Returns null at end of stream. */
    fun receive(input: InputStream): ByteArray? {
        val header = readFully(input, 6) ?: return null
        val length = ((header[4].toInt() and 0xFF) shl 8) or (header[5].toInt() and 0xFF)
        val rest = readFully(input, length - 6) ?: return null
        return (header + rest).also { received += it }
    }

    /** Reads frames and ACKs each one with status 0, until end of stream. */
    fun ackAll(input: InputStream, output: OutputStream) {
        while (true) {
            val frame = receive(input) ?: return
            output.write(ack(frame[6].toInt() and 0xFF))
            output.flush()
        }
    }

    /** Reads frames and never answers, until end of stream. */
    fun ignoreAll(input: InputStream) {
        while (receive(input) != null) Unit
    }

    override fun close() = server.close()

    companion object {
        fun ack(command: Int, status: Int = 0): ByteArray = ByteArray(HtFrame.ACK_LENGTH).also {
            it[0] = 'H'.code.toByte()
            it[1] = 'R'.code.toByte()
            it[20] = command.toByte()
            it[21] = status.toByte()
            it[30] = 0xAA.toByte()
        }

        fun readFully(input: InputStream, n: Int): ByteArray? {
            val buffer = ByteArray(n)
            var offset = 0
            while (offset < n) {
                val read = input.read(buffer, offset, n - offset)
                if (read < 0) return null
                offset += read
            }
            return buffer
        }
    }
}
```

- [ ] **Step 2: Write the failing test**

`app/protocol/src/test/kotlin/com/masjidclock/wfsender/protocol/CardSenderTest.kt`:

```kotlin
package com.masjidclock.wfsender.protocol

import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSenderTest {

    private val export = HdsExport.fromDirectory(File(javaClass.getResource("/export-128x64")!!.toURI()))
    private val captured = javaClass.getResourceAsStream("/captured-transfer.bin")!!.readBytes()
    private var card: FakeCard? = null

    @After
    fun closeCard() {
        card?.close()
    }

    private fun startCard(handler: FakeCard.(Int, java.io.InputStream, java.io.OutputStream) -> Unit): FakeCard =
        FakeCard(handler).also { card = it }

    private fun sender(card: FakeCard, isNetworkLost: () -> Boolean = { false }) = CardSender(
        connector = card.connector,
        connectTimeoutMs = 1_000,
        ackTimeoutMs = 300,
        newTransferId = { 0x030c },
        isNetworkLost = isNetworkLost,
    )

    private fun failure(result: SendResult): SendResult.Failed {
        assertTrue("expected a failure, got $result", result is SendResult.Failed)
        return result as SendResult.Failed
    }

    @Test
    fun sendsTheSameBytesAsHd2020AndSucceeds() {
        val card = startCard { _, input, output -> ackAll(input, output) }
        val result = sender(card).send(export)
        assertEquals(SendResult.Success, result)
        assertEquals(1, card.connections.get())
        val all = card.received.fold(ByteArray(0)) { acc, f -> acc + f }
        assertArrayEquals(captured, all)
    }

    @Test
    fun retriesAfterMissingAck() {
        val card = startCard { connection, input, output ->
            if (connection == 1) ignoreAll(input) else ackAll(input, output)
        }
        val result = sender(card).send(export)
        assertEquals(SendResult.Success, result)
        assertEquals(2, card.connections.get())
    }

    @Test
    fun failsWithNetworkAfterThreeAttempts() {
        val card = startCard { _, input, _ -> ignoreAll(input) }
        val failed = failure(sender(card).send(export))
        assertEquals(FailureReason.NETWORK, failed.reason)
        assertEquals(3, card.connections.get())
    }

    @Test
    fun failsWithNetworkWhenNoCardListens() {
        val card = startCard { _, input, output -> ackAll(input, output) }
        card.close()
        val failed = failure(sender(card).send(export))
        assertEquals(FailureReason.NETWORK, failed.reason)
    }

    @Test
    fun stopsAtOnceOnErrorStatus() {
        val card = startCard { _, input, output ->
            receive(input); output.write(FakeCard.ack(Command.SCREEN)); output.flush()
            receive(input); output.write(FakeCard.ack(Command.START, status = 0x05)); output.flush()
            ignoreAll(input)
        }
        val failed = failure(sender(card).send(export))
        assertEquals(FailureReason.REJECTED, failed.reason)
        assertEquals(0x05, failed.status)
        assertEquals(1, card.connections.get())
    }

    @Test
    fun retriesWhenCardClosesTheConnection() {
        val card = startCard { connection, input, output ->
            if (connection == 1) {
                repeat(2) {
                    val frame = receive(input)!!
                    output.write(FakeCard.ack(frame[6].toInt() and 0xFF)); output.flush()
                }
                // Return: the connection closes in the middle of the transfer.
            } else {
                ackAll(input, output)
            }
        }
        assertEquals(SendResult.Success, sender(card).send(export))
        assertEquals(2, card.connections.get())
    }

    @Test
    fun stopsAtOnceOnUnexpectedReply() {
        val card = startCard { _, input, output ->
            receive(input)
            output.write(ByteArray(HtFrame.ACK_LENGTH) { 'X'.code.toByte() }); output.flush()
            ignoreAll(input)
        }
        val failed = failure(sender(card).send(export))
        assertEquals(FailureReason.UNEXPECTED_REPLY, failed.reason)
        assertEquals(1, card.connections.get())
    }

    @Test
    fun stopsAtOnceWhenTheNetworkIsLost() {
        val card = startCard { _, input, _ -> ignoreAll(input) }
        val failed = failure(sender(card, isNetworkLost = { true }).send(export))
        assertEquals(FailureReason.NETWORK_LOST, failed.reason)
        assertEquals(1, card.connections.get())
    }

    @Test
    fun succeedsWhenAckArrivesInPieces() {
        val card = startCard { _, input, output ->
            while (true) {
                val frame = receive(input) ?: break
                val ack = FakeCard.ack(frame[6].toInt() and 0xFF)
                output.write(ack, 0, 10); output.flush()
                Thread.sleep(20)
                output.write(ack, 10, ack.size - 10); output.flush()
            }
        }
        assertEquals(SendResult.Success, sender(card).send(export))
        assertEquals(1, card.connections.get())
    }

    @Test
    fun ignoresAckForOtherCommand() {
        val card = startCard { _, input, output ->
            while (true) {
                val frame = receive(input) ?: break
                output.write(FakeCard.ack(0x77)); output.flush()
                output.write(FakeCard.ack(frame[6].toInt() and 0xFF)); output.flush()
            }
        }
        assertEquals(SendResult.Success, sender(card).send(export))
        assertEquals(16, card.received.size)
    }

    @Test
    fun reportsAttemptsFramesAndAcks() {
        val card = startCard { connection, input, output ->
            if (connection == 1) ignoreAll(input) else ackAll(input, output)
        }
        val events = mutableListOf<String>()
        val listener = object : SendListener {
            override fun onAttemptStarted(attempt: Int, transferId: Int) { events += "attempt $attempt" }
            override fun onFrameSent(index: Int, total: Int, frame: ByteArray) { events += "frame $index/$total" }
            override fun onAck(ack: Ack) { events += "ack" }
            override fun onAttemptFailed(attempt: Int, error: String) { events += "failed $attempt" }
        }
        assertEquals(SendResult.Success, sender(card).send(export, listener))
        assertEquals(listOf("attempt 1", "frame 1/16", "failed 1", "attempt 2"), events.take(4))
        assertEquals("frame 16/16", events[events.size - 2])
        assertEquals("ack", events.last())
        assertEquals(16, events.count { it == "ack" })
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test`
Expected: FAIL. `Unresolved reference 'CardConnector'`, `'CardSender'`, `'SendResult'`.

- [ ] **Step 4: Write the implementation**

`app/protocol/src/main/kotlin/com/masjidclock/wfsender/protocol/CardSender.kt`:

```kotlin
package com.masjidclock.wfsender.protocol

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.random.Random

/** Opens a TCP connection to the card. The Android app gives a socket that is bound to the card WiFi. */
fun interface CardConnector {
    fun connect(host: String, port: Int, timeoutMs: Int): Socket
}

enum class FailureReason { NETWORK, NETWORK_LOST, REJECTED, UNEXPECTED_REPLY }

sealed class SendResult {
    object Success : SendResult()
    data class Failed(val reason: FailureReason, val status: Int?, val detail: String) : SendResult()
}

/** Progress of a send. All methods are called on the thread that calls [CardSender.send]. */
interface SendListener {
    fun onAttemptStarted(attempt: Int, transferId: Int) {}
    fun onFrameSent(index: Int, total: Int, frame: ByteArray) {}
    fun onAck(ack: Ack) {}
    fun onAttemptFailed(attempt: Int, error: String) {}
}

/** Sends a program to a WF card (spec section 6.1). The calls block; use a background thread. */
class CardSender(
    private val connector: CardConnector,
    private val host: String = DEFAULT_HOST,
    private val port: Int = DEFAULT_PORT,
    private val connectTimeoutMs: Int = 5_000,
    private val ackTimeoutMs: Int = 10_000,
    private val maxAttempts: Int = 3,
    private val probeTimeoutMs: Int = 3_000,
    private val newTransferId: () -> Int = { Random.nextInt(0x10000) },
    private val isNetworkLost: () -> Boolean = { false },
) {

    fun send(export: HdsExport, listener: SendListener = object : SendListener {}): SendResult {
        var lastError = ""
        for (attempt in 1..maxAttempts) {
            val transferId = newTransferId()
            listener.onAttemptStarted(attempt, transferId)
            val frames = ProgramTransfer.frames(export, transferId)
            try {
                connector.connect(host, port, connectTimeoutMs).use { socket ->
                    socket.soTimeout = ackTimeoutMs
                    val output = socket.getOutputStream()
                    val input = socket.getInputStream()
                    frames.forEachIndexed { index, frame ->
                        output.write(frame)
                        output.flush()
                        listener.onFrameSent(index + 1, frames.size, frame)
                        val command = frame[6].toInt() and 0xFF
                        val ack = awaitAck(input, command)
                        listener.onAck(ack)
                        if (ack.status != 0) {
                            val detail = "status 0x%02x for command 0x%02x".format(ack.status, ack.command)
                            listener.onAttemptFailed(attempt, detail)
                            return SendResult.Failed(FailureReason.REJECTED, ack.status, detail)
                        }
                    }
                }
                return SendResult.Success
            } catch (e: UnexpectedReplyException) {
                val detail = e.message ?: "unexpected reply"
                listener.onAttemptFailed(attempt, detail)
                return SendResult.Failed(FailureReason.UNEXPECTED_REPLY, null, detail)
            } catch (e: IOException) {
                lastError = e.toString()
                listener.onAttemptFailed(attempt, lastError)
                if (isNetworkLost()) return SendResult.Failed(FailureReason.NETWORK_LOST, null, lastError)
            }
        }
        return SendResult.Failed(FailureReason.NETWORK, null, lastError)
    }

    /**
     * Reads ACKs until one answers [command] or has an error status.
     * An ACK with status 0 for a different command is ignored.
     */
    private fun awaitAck(input: InputStream, command: Int): Ack {
        val deadline = System.nanoTime() + ackTimeoutMs * 1_000_000L
        while (true) {
            val ack = Ack.parse(readExactly(input, HtFrame.ACK_LENGTH))
            if (ack.command == command || ack.status != 0) return ack
            if (System.nanoTime() > deadline) {
                throw SocketTimeoutException("no ACK for command 0x%02x".format(command))
            }
        }
    }

    companion object {
        const val DEFAULT_HOST = "192.168.4.1"
        const val DEFAULT_PORT = 6101

        internal fun readExactly(input: InputStream, n: Int): ByteArray {
            val buffer = ByteArray(n)
            var offset = 0
            while (offset < n) {
                val read = input.read(buffer, offset, n - offset)
                if (read < 0) throw EOFException("the card closed the connection")
                offset += read
            }
            return buffer
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test`
Expected: `BUILD SUCCESSFUL`, 29 tests passed.

- [ ] **Step 6: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock
git add app/protocol
git commit -m "Add CardSender with retries and a fake card for tests" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: CardSender probe

**Files:**
- Modify: `app/protocol/src/main/kotlin/com/masjidclock/wfsender/protocol/CardSender.kt` (add `probe()` and `PROBE`)
- Test: `app/protocol/src/test/kotlin/com/masjidclock/wfsender/protocol/CardProbeTest.kt`

**Interfaces:**
- Consumes: `CardSender`, `CardConnector`, `CardSender.readExactly` (Task 4); `Ack.isHr` (Task 1); `FakeCard` (Task 4).
- Produces: `fun CardSender.probe(): Boolean` (member function) and `CardSender.PROBE: ByteArray` (companion `val`).

- [ ] **Step 1: Write the failing test**

`app/protocol/src/test/kotlin/com/masjidclock/wfsender/protocol/CardProbeTest.kt`:

```kotlin
package com.masjidclock.wfsender.protocol

import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardProbeTest {

    private var card: FakeCard? = null

    @After
    fun closeCard() {
        card?.close()
    }

    private fun startCard(handler: FakeCard.(Int, java.io.InputStream, java.io.OutputStream) -> Unit): FakeCard =
        FakeCard(handler).also { card = it }

    private fun sender(card: FakeCard) = CardSender(connector = card.connector, probeTimeoutMs = 300)

    @Test
    fun findsACardThatAnswersWithHr() {
        val request = AtomicReference<ByteArray>()
        val card = startCard { _, input, output ->
            request.set(FakeCard.readFully(input, 8))
            output.write(FakeCard.ack(0x00, 0x04)); output.flush()
            input.read()
        }
        assertTrue(sender(card).probe())
        assertArrayEquals(byteArrayOf(0x08, 0x00, 0x01, 0x20, 0x05, 0x00, 0x00, 0x01), request.get())
        assertArrayEquals(request.get(), CardSender.PROBE)
    }

    @Test
    fun findsACardWhenTheReplyArrivesInPieces() {
        val card = startCard { _, input, output ->
            FakeCard.readFully(input, 8)
            output.write('H'.code); output.flush()
            Thread.sleep(20)
            output.write('R'.code); output.flush()
            input.read()
        }
        assertTrue(sender(card).probe())
    }

    @Test
    fun noReply() {
        val card = startCard { _, input, _ -> while (input.read() >= 0) Unit }
        assertFalse(sender(card).probe())
    }

    @Test
    fun replyWithoutHr() {
        val card = startCard { _, input, output ->
            FakeCard.readFully(input, 8)
            output.write(ByteArray(31) { 'X'.code.toByte() }); output.flush()
            input.read()
        }
        assertFalse(sender(card).probe())
    }

    @Test
    fun nothingListens() {
        val card = startCard { _, _, _ -> }
        card.close()
        assertFalse(sender(card).probe())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test`
Expected: FAIL. `Unresolved reference 'probe'`, `Unresolved reference 'PROBE'`.

- [ ] **Step 3: Write the implementation**

In `CardSender.kt`, add this function to `class CardSender`, after `send`:

```kotlin
    /** Sends the harmless SDK version packet (spec section 3.4). True if the reply starts with HR. */
    fun probe(): Boolean = try {
        connector.connect(host, port, probeTimeoutMs).use { socket ->
            socket.soTimeout = probeTimeoutMs
            socket.getOutputStream().apply {
                write(PROBE)
                flush()
            }
            Ack.isHr(readExactly(socket.getInputStream(), 2))
        }
    } catch (e: IOException) {
        false
    }
```

In the `companion object` of `CardSender`, add:

```kotlin
        /** SDK version request: length 8, command 0x2001, version 0x01000005 (little-endian). */
        val PROBE: ByteArray get() = byteArrayOf(0x08, 0x00, 0x01, 0x20, 0x05, 0x00, 0x00, 0x01)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test`
Expected: `BUILD SUCCESSFUL`, 34 tests passed.

- [ ] **Step 5: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock
git add app/protocol
git commit -m "Add card probe to CardSender" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Android module and ZIP import

**Files:**
- Modify: `app/settings.gradle.kts` (include `:android`)
- Create: `app/local.properties` (not committed), `app/android/build.gradle.kts`, `app/android/src/main/AndroidManifest.xml`
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/site/ZipImporter.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/site/ZipImporterTest.kt`

**Interfaces:**
- Consumes: `HdsExport`, `InvalidExportException`, `HdsExport.SCREEN_FILE`, `HdsExport.PROGRAM_FILE` (Task 2).
- Produces:
  - `data class Card(val label: String, val dir: java.io.File, val width: Int, val height: Int, val programSize: Int)`
  - `data class Site(val name: String, val cards: List<Card>)`
  - `class ImportException(message: String) : Exception(message)`
  - `class ZipImporter(maxUnpackedBytes: Long = 20L * 1024 * 1024)` with `fun import(zip: java.io.InputStream, zipName: String, siteDir: java.io.File): Site` and `companion object { fun load(siteDir: java.io.File): Site? }`

- [ ] **Step 1: Add the Android module**

`app/local.properties` (ignored by git):

```properties
sdk.dir=/home/towfique/Android/Sdk
```

In `app/settings.gradle.kts`, replace `include(":protocol")` with:

```kotlin
include(":protocol")
include(":android")
```

`app/android/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.masjidclock.wfsender"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.masjidclock.wfsender"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
```

`app/android/src/main/AndroidManifest.xml` (Task 8 adds the activity):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="false"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="Masjid Clock Sender"
        android:theme="@android:style/Theme.Material.Light.NoActionBar" />
</manifest>
```

- [ ] **Step 2: Write the failing test**

`app/android/src/test/kotlin/com/masjidclock/wfsender/site/ZipImporterTest.kt`:

```kotlin
package com.masjidclock.wfsender.site

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipImporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val siteDir by lazy { File(tmp.newFolder("files"), "site") }

    private fun screen(width: Int, height: Int) = ByteArray(66).also {
        it[0] = 'H'.code.toByte(); it[1] = 'S'.code.toByte()
        it[4] = (width shr 8).toByte(); it[5] = width.toByte()
        it[6] = (height shr 8).toByte(); it[7] = height.toByte()
    }

    private fun program(size: Int = 100) = ByteArray(size).also {
        it[0] = 'H'.code.toByte(); it[1] = 'C'.code.toByte()
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun import(zip: ByteArray, name: String = "site-a.zip", importer: ZipImporter = ZipImporter()) =
        importer.import(ByteArrayInputStream(zip), name, siteDir)

    private fun importError(zip: ByteArray, importer: ZipImporter = ZipImporter()): String =
        assertThrows(ImportException::class.java) { import(zip, importer = importer) }.message!!

    @Test
    fun importsTwoCardFoldersAndIgnoresOtherFiles() {
        val site = import(
            zipOf(
                "english/screen.hds" to screen(128, 64),
                "english/progam.hds" to program(200),
                "english/set.hds" to ByteArray(1024),
                "english/smart.ss" to ByteArray(118),
                "english/font.hds" to ByteArray(10),
                "hijri/screen.hds" to screen(128, 32),
                "hijri/progam.hds" to program(),
            )
        )
        assertEquals("site-a", site.name)
        assertEquals(listOf("english", "hijri"), site.cards.map { it.label })
        val english = site.cards[0]
        assertEquals(128, english.width)
        assertEquals(64, english.height)
        assertEquals(200, english.programSize)
        assertTrue(File(english.dir, "progam.hds").isFile)
        assertFalse(File(english.dir, "set.hds").exists())
        assertFalse(File(english.dir, "smart.ss").exists())
        assertFalse(File(english.dir, "font.hds").exists())
        assertTrue(english.dir.canonicalPath.startsWith(siteDir.canonicalPath))
    }

    @Test
    fun filesAtTheZipRootUseTheZipName() {
        val site = import(zipOf("screen.hds" to screen(128, 64), "progam.hds" to program()), name = "masjid-x.zip")
        assertEquals(listOf("masjid-x"), site.cards.map { it.label })
    }

    @Test
    fun labelsAreRelativePaths() {
        val site = import(
            zipOf(
                "m2/english/screen.hds" to screen(128, 64),
                "m2/english/progam.hds" to program(),
                "m3/english/screen.hds" to screen(128, 32),
                "m3/english/progam.hds" to program(),
            )
        )
        assertEquals(listOf("m2/english", "m3/english"), site.cards.map { it.label })
        assertEquals(listOf(64, 32), site.cards.map { it.height })
    }

    @Test
    fun acceptsZipFromMacAndWindowsTools() {
        val site = import(
            zipOf(
                "__MACOSX/english/._screen.hds" to ByteArray(10),
                "english\\SCREEN.HDS" to screen(128, 64),
                "english\\Progam.hds" to program(),
                "english/._progam.hds" to ByteArray(10),
            )
        )
        assertEquals(listOf("english"), site.cards.map { it.label })
        assertTrue(File(site.cards[0].dir, "screen.hds").isFile)
    }

    @Test
    fun rejectsZipWithoutCardFolders() {
        assertTrue(importError(zipOf("readme.txt" to ByteArray(3))).contains("no display folders"))
    }

    @Test
    fun rejectsFolderWithOnlyScreenFile() {
        val message = importError(zipOf("english/screen.hds" to screen(128, 64)))
        assertTrue(message, message.contains("'english'") && message.contains("progam.hds is missing"))
    }

    @Test
    fun rejectsInvalidExport() {
        val bad = screen(128, 64).also { it[0] = 'X'.code.toByte() }
        val message = importError(zipOf("english/screen.hds" to bad, "english/progam.hds" to program()))
        assertTrue(message, message.contains("not a valid HD2020 export"))
    }

    @Test
    fun rejectsDotDotPath() {
        val message = importError(zipOf("../evil/screen.hds" to screen(128, 64), "../evil/progam.hds" to program()))
        assertTrue(message, message.contains("unsafe path"))
        assertFalse(File(siteDir.parentFile.parentFile, "evil").exists())
        assertFalse(File(siteDir.parentFile, "evil").exists())
    }

    @Test
    fun rejectsAbsolutePath() {
        val message = importError(zipOf("/etc/screen.hds" to screen(128, 64)))
        assertTrue(message, message.contains("unsafe path"))
    }

    @Test
    fun rejectsZipLargerThanTheLimit() {
        val small = ZipImporter(maxUnpackedBytes = 1_000)
        val message = importError(zipOf("a/screen.hds" to screen(128, 64), "a/progam.hds" to program(2_000)), small)
        assertTrue(message, message.contains("too large"))
    }

    @Test
    fun rejectsFileThatIsNotAZip() {
        importError(ByteArray(500) { 7 })
    }

    @Test
    fun newImportReplacesTheEarlierSite() {
        import(zipOf("a/screen.hds" to screen(128, 64), "a/progam.hds" to program()), name = "one.zip")
        import(zipOf("b/screen.hds" to screen(128, 64), "b/progam.hds" to program()), name = "two.zip")
        val loaded = ZipImporter.load(siteDir)!!
        assertEquals("two", loaded.name)
        assertEquals(listOf("b"), loaded.cards.map { it.label })
    }

    @Test
    fun failedImportKeepsTheEarlierSite() {
        import(zipOf("a/screen.hds" to screen(128, 64), "a/progam.hds" to program()), name = "one.zip")
        importError(zipOf("readme.txt" to ByteArray(3)))
        val loaded = ZipImporter.load(siteDir)!!
        assertEquals("one", loaded.name)
        assertEquals(listOf("a"), loaded.cards.map { it.label })
    }

    @Test
    fun loadReturnsNullWhenNothingWasImported() {
        assertNull(ZipImporter.load(siteDir))
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :android:testDebugUnitTest`
Expected: FAIL. `Unresolved reference 'ZipImporter'`, `'ImportException'`.
The first run downloads the Android Gradle plugin and the AndroidX libraries. This can take several minutes.

- [ ] **Step 4: Write the implementation**

`app/android/src/main/kotlin/com/masjidclock/wfsender/site/ZipImporter.kt`:

```kotlin
package com.masjidclock.wfsender.site

import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.protocol.InvalidExportException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

data class Card(val label: String, val dir: File, val width: Int, val height: Int, val programSize: Int)

data class Site(val name: String, val cards: List<Card>)

/** The ZIP cannot be used. The message is shown to the person. */
class ImportException(message: String) : Exception(message)

/** Unpacks a site ZIP into the private storage of the app (spec section 5.1). */
class ZipImporter(private val maxUnpackedBytes: Long = 20L * 1024 * 1024) {

    /** Replaces the site in [siteDir] with the cards in [zip]. On failure, [siteDir] does not change. */
    fun import(zip: InputStream, zipName: String, siteDir: File): Site {
        val name = siteName(zipName)
        val temp = File(siteDir.parentFile, siteDir.name + ".tmp")
        temp.deleteRecursively()
        temp.mkdirs()
        try {
            try {
                unpack(zip, temp)
            } catch (e: IOException) {
                throw ImportException("Could not read the ZIP file. Ask the sender for a new file.")
            }
            val site = scan(temp, name)
            if (site.cards.isEmpty()) {
                throw ImportException(
                    "The ZIP file has no display folders. Each folder must contain screen.hds and progam.hds."
                )
            }
            siteDir.deleteRecursively()
            if (!temp.renameTo(siteDir)) throw ImportException("Could not save the files on the phone.")
            File(siteDir, NAME_FILE).writeText(name)
            return scan(siteDir, name)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun unpack(zip: InputStream, target: File) {
        var total = 0L
        val buffer = ByteArray(8192)
        ZipInputStream(zip).use { entries ->
            while (true) {
                val entry = entries.nextEntry ?: break
                val path = entry.name.replace('\\', '/')
                if (isUnsafe(path)) throw ImportException("The ZIP file contains an unsafe path: ${entry.name}")
                if (entry.isDirectory) continue
                val parts = path.split('/').filter { it.isNotEmpty() }
                if (parts.isEmpty() || "__MACOSX" in parts) continue
                val fileName = parts.last()
                if (fileName.startsWith("._")) continue
                val canonical = when (fileName.lowercase()) {
                    HdsExport.SCREEN_FILE -> HdsExport.SCREEN_FILE
                    HdsExport.PROGRAM_FILE -> HdsExport.PROGRAM_FILE
                    else -> continue
                }
                val dir = parts.dropLast(1).fold(target) { parent, part -> File(parent, part) }
                dir.mkdirs()
                File(dir, canonical).outputStream().use { out ->
                    while (true) {
                        val read = entries.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxUnpackedBytes) {
                            throw ImportException("The ZIP file is too large. The maximum is 20 MB.")
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    private fun isUnsafe(path: String): Boolean =
        path.startsWith("/") || DRIVE.containsMatchIn(path) || path.split('/').any { it == ".." }

    companion object {
        private const val NAME_FILE = ".site-name"
        private val DRIVE = Regex("^[A-Za-z]:")

        /** The site that was imported last, or null. */
        fun load(siteDir: File): Site? {
            val nameFile = File(siteDir, NAME_FILE)
            if (!nameFile.isFile) return null
            return try {
                scan(siteDir, nameFile.readText())
            } catch (e: ImportException) {
                null
            }
        }

        private fun siteName(zipName: String): String =
            zipName.substringAfterLast('/').replace(Regex("(?i)\\.zip$"), "")

        private fun scan(root: File, siteName: String): Site {
            val cards = root.walkTopDown()
                .filter { dir ->
                    dir.isDirectory &&
                        (File(dir, HdsExport.SCREEN_FILE).isFile || File(dir, HdsExport.PROGRAM_FILE).isFile)
                }
                .map { dir ->
                    val label = dir.relativeTo(root).invariantSeparatorsPath.ifEmpty { siteName }
                    val export = try {
                        HdsExport.fromDirectory(dir)
                    } catch (e: InvalidExportException) {
                        throw ImportException("The folder '$label' is not a valid HD2020 export: ${e.message}")
                    }
                    Card(label, dir, export.width, export.height, export.program.size)
                }
                .sortedBy { it.label }
                .toList()
            return Site(siteName, cards)
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :android:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 14 tests passed.

- [ ] **Step 6: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock
git add app/settings.gradle.kts app/android
git status --short   # local.properties must NOT be in the list
git commit -m "Add Android module and site ZIP importer" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Messages and send log

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/send/Messages.kt`
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/send/SendLog.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/send/MessagesTest.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/send/SendLogTest.kt`

**Interfaces:**
- Consumes: `SendResult`, `FailureReason`, `SendListener`, `Ack` (Tasks 1, 4); `Card` (Task 6).
- Produces:
  - `object Messages` with `const val` texts `SUCCESS`, `WEAK_SIGNAL`, `NETWORK_LOST`, `UNEXPECTED_REPLY`, `WIFI_INSTRUCTIONS`, `NOT_CONNECTED`, `NO_DISPLAY`, `DISPLAY_FOUND`, `NO_SITE`, and `fun forResult(result: SendResult): String`, `fun confirmation(card: Card): String`, `fun cardRow(card: Card): String`
  - `class SendLog(siteName: String, card: Card, appVersion: String, androidVersion: String, now: () -> java.util.Date = { java.util.Date() }) : SendListener` with `fun finish(result: SendResult)` and `fun text(): String`

- [ ] **Step 1: Write the failing tests**

`app/android/src/test/kotlin/com/masjidclock/wfsender/send/MessagesTest.kt`:

```kotlin
package com.masjidclock.wfsender.send

import com.masjidclock.wfsender.protocol.FailureReason
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.site.Card
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class MessagesTest {

    private val card = Card("english", File("x"), 128, 64, 12586)

    @Test
    fun resultMessagesMatchTheSpec() {
        assertEquals("Done. The display shows the new program.", Messages.forResult(SendResult.Success))
        assertEquals(
            "The WiFi signal is too weak. Move nearer to the display and try again.",
            Messages.forResult(SendResult.Failed(FailureReason.NETWORK, null, "")),
        )
        assertEquals(
            "The phone left the WiFi of the display. Connect again and try again.",
            Messages.forResult(SendResult.Failed(FailureReason.NETWORK_LOST, null, "")),
        )
        assertEquals(
            "The display rejected the program (code 0x05). Contact the person who sent you the file.",
            Messages.forResult(SendResult.Failed(FailureReason.REJECTED, 5, "")),
        )
        assertEquals(
            "The display sent an unexpected reply. Contact the person who sent you the file.",
            Messages.forResult(SendResult.Failed(FailureReason.UNEXPECTED_REPLY, null, "")),
        )
    }

    @Test
    fun confirmationNamesTheCardAndTheSize() {
        assertEquals(
            "This program is for a 128 × 64 display named 'english'. Is the phone connected to this display?",
            Messages.confirmation(card),
        )
    }

    @Test
    fun cardRowShowsLabelAndSize() {
        assertEquals("english — 128 × 64", Messages.cardRow(card))
    }
}
```

`app/android/src/test/kotlin/com/masjidclock/wfsender/send/SendLogTest.kt`:

```kotlin
package com.masjidclock.wfsender.send

import com.masjidclock.wfsender.protocol.Ack
import com.masjidclock.wfsender.protocol.FailureReason
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.site.Card
import java.io.File
import java.util.Date
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class SendLogTest {

    @Test
    fun recordsHeaderAttemptsFramesAcksAndResult() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val log = SendLog(
            siteName = "site-a",
            card = Card("english", File("x"), 128, 64, 12586),
            appVersion = "0.1.0",
            androidVersion = "13",
            now = { Date(0) },
        )
        log.onAttemptStarted(1, 0x030c)
        val frame = ByteArray(96).also { it[6] = 0x13; it[26] = 1 }
        log.onFrameSent(1, 16, frame)
        log.onAck(Ack(0x13, 0))
        log.onAttemptFailed(1, "java.net.SocketTimeoutException: Read timed out")
        log.finish(SendResult.Failed(FailureReason.NETWORK, null, "java.net.SocketTimeoutException: Read timed out"))

        val expected = """
            Masjid Clock Sender 0.1.0, Android 13
            Time: 1970-01-01 00:00:00
            Site: site-a
            Card: english, 128 × 64, progam.hds 12586 bytes
            Attempt 1, transfer ID 0x030c
              sent 1/16: command 0x13, sequence 1, 96 bytes
              ACK: command 0x13, status 0x00
              attempt 1 failed: java.net.SocketTimeoutException: Read timed out
            Result: FAILED (NETWORK) java.net.SocketTimeoutException: Read timed out
        """.trimIndent()
        assertEquals(expected, log.text())
    }

    @Test
    fun recordsSuccess() {
        val log = SendLog("s", Card("a", File("x"), 64, 32, 100), "0.1.0", "13", now = { Date(0) })
        log.finish(SendResult.Success)
        assertEquals("Result: SUCCESS", log.text().lines().last())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :android:testDebugUnitTest`
Expected: FAIL. `Unresolved reference 'Messages'`, `'SendLog'`.

- [ ] **Step 3: Write the implementation**

`app/android/src/main/kotlin/com/masjidclock/wfsender/send/Messages.kt`:

```kotlin
package com.masjidclock.wfsender.send

import com.masjidclock.wfsender.protocol.FailureReason
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.site.Card

/** All texts that the person sees (spec sections 5.2, 5.3, 6.2). */
object Messages {
    const val SUCCESS = "Done. The display shows the new program."
    const val WEAK_SIGNAL = "The WiFi signal is too weak. Move nearer to the display and try again."
    const val NETWORK_LOST = "The phone left the WiFi of the display. Connect again and try again."
    const val UNEXPECTED_REPLY = "The display sent an unexpected reply. Contact the person who sent you the file."
    const val WIFI_INSTRUCTIONS =
        "Connect the phone to the WiFi of the display in Android settings. The password is 88888888."
    const val NOT_CONNECTED = "Not connected to WiFi"
    const val NO_DISPLAY = "No display found on this WiFi"
    const val DISPLAY_FOUND = "Display found"
    const val NO_SITE = "Open the ZIP file that you received. It contains the programs for the displays."

    fun forResult(result: SendResult): String = when (result) {
        SendResult.Success -> SUCCESS
        is SendResult.Failed -> when (result.reason) {
            FailureReason.NETWORK -> WEAK_SIGNAL
            FailureReason.NETWORK_LOST -> NETWORK_LOST
            FailureReason.REJECTED ->
                "The display rejected the program (code 0x%02X). Contact the person who sent you the file."
                    .format(result.status ?: 0)
            FailureReason.UNEXPECTED_REPLY -> UNEXPECTED_REPLY
        }
    }

    fun confirmation(card: Card): String =
        "This program is for a ${card.width} × ${card.height} display named '${card.label}'. " +
            "Is the phone connected to this display?"

    fun cardRow(card: Card): String = "${card.label} — ${card.width} × ${card.height}"
}
```

`app/android/src/main/kotlin/com/masjidclock/wfsender/send/SendLog.kt`:

```kotlin
package com.masjidclock.wfsender.send

import com.masjidclock.wfsender.protocol.Ack
import com.masjidclock.wfsender.protocol.SendListener
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.site.Card
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The log of 1 send, shared as plain text (spec section 6.3). */
class SendLog(
    siteName: String,
    card: Card,
    appVersion: String,
    androidVersion: String,
    now: () -> Date = { Date() },
) : SendListener {

    private val lines = mutableListOf<String>()

    init {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now())
        add("Masjid Clock Sender $appVersion, Android $androidVersion")
        add("Time: $time")
        add("Site: $siteName")
        add("Card: ${card.label}, ${card.width} × ${card.height}, progam.hds ${card.programSize} bytes")
    }

    override fun onAttemptStarted(attempt: Int, transferId: Int) =
        add("Attempt $attempt, transfer ID 0x%04x".format(transferId))

    override fun onFrameSent(index: Int, total: Int, frame: ByteArray) =
        add(
            "  sent $index/$total: command 0x%02x, sequence %d, %d bytes"
                .format(frame[6].toInt() and 0xFF, frame[26].toInt() and 0xFF, frame.size)
        )

    override fun onAck(ack: Ack) = add("  ACK: command 0x%02x, status 0x%02x".format(ack.command, ack.status))

    override fun onAttemptFailed(attempt: Int, error: String) = add("  attempt $attempt failed: $error")

    fun finish(result: SendResult) = add(
        when (result) {
            SendResult.Success -> "Result: SUCCESS"
            is SendResult.Failed -> "Result: FAILED (${result.reason}) ${result.detail}"
        }
    )

    @Synchronized
    fun text(): String = lines.joinToString("\n")

    @Synchronized
    private fun add(line: String) {
        lines += line
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :android:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 19 tests passed.

- [ ] **Step 5: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock
git add app/android
git commit -m "Add user messages and send log" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: WiFi network, view model, and screens

This task has no unit tests: it is Android UI and network code. The protocol and the import logic that it calls are tested in Tasks 1–7. Verification is a successful build and the manual checks in Step 6.

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/net/CardNetwork.kt`
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/AppViewModel.kt`
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/MainActivity.kt`
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/ui/Screens.kt`
- Modify: `app/android/src/main/AndroidManifest.xml` (add the activity)

**Interfaces:**
- Consumes: `CardConnector`, `CardSender(connector, isNetworkLost = ...)`, `CardSender.send`, `CardSender.probe`, `SendListener`, `SendResult`, `HdsExport.fromDirectory` (protocol); `ZipImporter`, `ZipImporter.load`, `ImportException`, `Site`, `Card` (Task 6); `Messages`, `SendLog` (Task 7).
- Produces: the runnable app. `class CardNetwork(context: Context)` with `val current: Network?`, `fun start()`, `fun stop()`, `fun connectorFor(network: Network): CardConnector`. `enum class ConnectionStatus { NOT_CONNECTED, NO_DISPLAY, DISPLAY_FOUND }`. `sealed interface SendState { Idle; Sending(sent, total); Finished(success, message, log) }`.

- [ ] **Step 1: Write `CardNetwork`**

`app/android/src/main/kotlin/com/masjidclock/wfsender/net/CardNetwork.kt`:

```kotlin
package com.masjidclock.wfsender.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.masjidclock.wfsender.protocol.CardConnector
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Tracks the connected WiFi network, also when it has no internet access (spec section 5.3).
 * Sockets from [connectorFor] are bound to that network, so traffic never goes over mobile data.
 */
class CardNetwork(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    @Volatile
    var current: Network? = null
        private set

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            current = network
        }

        override fun onLost(network: Network) {
            if (current == network) current = null
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivity.registerNetworkCallback(request, callback)
    }

    fun stop() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }

    fun connectorFor(network: Network) = CardConnector { host, port, timeoutMs ->
        val socket = Socket()
        try {
            network.bindSocket(socket)
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket
        } catch (e: IOException) {
            socket.close()
            throw e
        }
    }
}
```

- [ ] **Step 2: Write `AppViewModel`**

`app/android/src/main/kotlin/com/masjidclock/wfsender/AppViewModel.kt`:

```kotlin
package com.masjidclock.wfsender

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masjidclock.wfsender.net.CardNetwork
import com.masjidclock.wfsender.protocol.CardSender
import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.protocol.SendListener
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.send.SendLog
import com.masjidclock.wfsender.site.Card
import com.masjidclock.wfsender.site.ImportException
import com.masjidclock.wfsender.site.Site
import com.masjidclock.wfsender.site.ZipImporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConnectionStatus { NOT_CONNECTED, NO_DISPLAY, DISPLAY_FOUND }

sealed interface SendState {
    data object Idle : SendState
    data class Sending(val sent: Int, val total: Int) : SendState
    data class Finished(val success: Boolean, val message: String, val log: String) : SendState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val siteDir = File(app.filesDir, "site")
    private val importer = ZipImporter()
    private val network = CardNetwork(app).also { it.start() }
    private var checkJob: Job? = null

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

    fun importZip(uri: Uri) {
        viewModelScope.launch {
            _importError.value = null
            val resolver = getApplication<Application>().contentResolver
            try {
                val imported = withContext(Dispatchers.IO) {
                    val name = displayName(resolver, uri) ?: "site.zip"
                    val input = resolver.openInputStream(uri) ?: throw ImportException("Could not open the file.")
                    input.use { importer.import(it, name, siteDir) }
                }
                _site.value = imported
                selectCard(null)
            } catch (e: ImportException) {
                _importError.value = e.message
            } catch (e: SecurityException) {
                _importError.value = "Could not open the file."
            }
        }
    }

    fun selectCard(card: Card?) {
        _selectedCard.value = card
        _sendState.value = SendState.Idle
        checkJob?.cancel()
        if (card != null) startConnectionChecks()
    }

    private fun startConnectionChecks() {
        checkJob = viewModelScope.launch {
            while (isActive) {
                if (_sendState.value !is SendState.Sending) {
                    _connection.value = withContext(Dispatchers.IO) { checkConnection() }
                }
                delay(2_000)
            }
        }
    }

    private fun checkConnection(): ConnectionStatus {
        val wifi = network.current ?: return ConnectionStatus.NOT_CONNECTED
        val found = CardSender(network.connectorFor(wifi)).probe()
        return if (found) ConnectionStatus.DISPLAY_FOUND else ConnectionStatus.NO_DISPLAY
    }

    fun send() {
        val card = _selectedCard.value ?: return
        val wifi = network.current ?: return
        if (_sendState.value is SendState.Sending) return
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
            val result = withContext(Dispatchers.IO) {
                val sender = CardSender(network.connectorFor(wifi), isNetworkLost = { network.current != wifi })
                sender.send(HdsExport.fromDirectory(card.dir), progress)
            }
            log.finish(result)
            _sendState.value = SendState.Finished(result == SendResult.Success, Messages.forResult(result), log.text())
        }
    }

    fun clearImportError() {
        _importError.value = null
    }

    override fun onCleared() {
        network.stop()
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment
}
```

- [ ] **Step 3: Write the screens**

`app/android/src/main/kotlin/com/masjidclock/wfsender/ui/Screens.kt`:

```kotlin
package com.masjidclock.wfsender.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card as CardBox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masjidclock.wfsender.AppViewModel
import com.masjidclock.wfsender.ConnectionStatus
import com.masjidclock.wfsender.SendState
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.site.Card
import com.masjidclock.wfsender.site.Site

private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

@Composable
fun WfSenderApp(model: AppViewModel) {
    val site by model.site.collectAsStateWithLifecycle()
    val importError by model.importError.collectAsStateWithLifecycle()
    val card by model.selectedCard.collectAsStateWithLifecycle()
    val connection by model.connection.collectAsStateWithLifecycle()
    val sendState by model.sendState.collectAsStateWithLifecycle()
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importZip(uri)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val selected = card
            if (selected == null) {
                SiteScreen(site, onOpenZip = { openZip.launch(ZIP_TYPES) }, onSelect = model::selectCard)
            } else {
                SendScreen(selected, connection, sendState, onSend = model::send, onBack = { model.selectCard(null) })
            }
            importError?.let { message ->
                AlertDialog(
                    onDismissRequest = model::clearImportError,
                    confirmButton = { TextButton(onClick = model::clearImportError) { Text("OK") } },
                    title = { Text("Cannot open the ZIP file") },
                    text = { Text(message) },
                )
            }
        }
    }
}

@Composable
private fun SiteScreen(site: Site?, onOpenZip: () -> Unit, onSelect: (Card) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Masjid Clock Sender", style = MaterialTheme.typography.headlineSmall)
        if (site == null) {
            Text(Messages.NO_SITE)
        } else {
            Text("Site: ${site.name}", style = MaterialTheme.typography.titleMedium)
            Text("Select the display to update:")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(site.cards) { card ->
                    CardBox(modifier = Modifier.fillMaxWidth().clickable { onSelect(card) }) {
                        Text(Messages.cardRow(card), modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
        Button(onClick = onOpenZip, modifier = Modifier.fillMaxWidth()) { Text("Open ZIP") }
    }
}

@Composable
private fun SendScreen(
    card: Card,
    connection: ConnectionStatus,
    sendState: SendState,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }
    val sending = sendState is SendState.Sending

    BackHandler(enabled = true) { if (!sending) onBack() }
    if (sending) KeepScreenOn()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(Messages.cardRow(card), style = MaterialTheme.typography.headlineSmall)
        when (sendState) {
            SendState.Idle -> {
                Text(Messages.WIFI_INSTRUCTIONS)
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) {
                    Text("Open WiFi settings")
                }
                val (status, color) = when (connection) {
                    ConnectionStatus.NOT_CONNECTED -> Messages.NOT_CONNECTED to Color(0xFFB00020)
                    ConnectionStatus.NO_DISPLAY -> Messages.NO_DISPLAY to Color(0xFFB00020)
                    ConnectionStatus.DISPLAY_FOUND -> Messages.DISPLAY_FOUND to Color(0xFF1B5E20)
                }
                Text(status, color = color, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { confirming = true },
                    enabled = connection == ConnectionStatus.DISPLAY_FOUND,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Send") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
            is SendState.Sending -> {
                if (sendState.total == 0) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Connecting to the display…")
                } else {
                    LinearProgressIndicator(
                        progress = { sendState.sent.toFloat() / sendState.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Sending… ${sendState.sent} of ${sendState.total}")
                }
                Text("Keep the phone near the display.")
            }
            is SendState.Finished -> {
                Text(
                    sendState.message,
                    color = if (sendState.success) Color(0xFF1B5E20) else Color(0xFFB00020),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sendState.log)
                    context.startActivity(Intent.createChooser(share, "Share log"))
                }, modifier = Modifier.fillMaxWidth()) { Text("Share log") }
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Send the program?") },
            text = { Text(Messages.confirmation(card)) },
            confirmButton = { TextButton(onClick = { confirming = false; onSend() }) { Text("Yes, send") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
```

- [ ] **Step 4: Write `MainActivity` and register it**

`app/android/src/main/kotlin/com/masjidclock/wfsender/MainActivity.kt`:

```kotlin
package com.masjidclock.wfsender

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.masjidclock.wfsender.ui.WfSenderApp

class MainActivity : ComponentActivity() {

    private val model: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { WfSenderApp(model) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A ZIP from "Open with" (ACTION_VIEW) or from the share sheet (ACTION_SEND). */
    private fun handleIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        model.importZip(uri)
    }
}
```

In `app/android/src/main/AndroidManifest.xml`, replace the self-closing `<application ... />` element with:

```xml
    <application
        android:allowBackup="false"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="Masjid Clock Sender"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="application/zip" />
                <data android:mimeType="application/x-zip-compressed" />
                <data android:mimeType="application/octet-stream" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="application/zip" />
                <data android:mimeType="application/x-zip-compressed" />
                <data android:mimeType="application/octet-stream" />
            </intent-filter>
        </activity>
    </application>
```

- [ ] **Step 5: Build the app and run all tests**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test :android:testDebugUnitTest :android:assembleDebug`
Expected: `BUILD SUCCESSFUL`. The APK is at `app/android/build/outputs/apk/debug/android-debug.apk`.
If the build fails, read the first compiler error, fix only that error, and run the command again.

- [ ] **Step 6: Manual smoke check (only if a phone or an emulator is available)**

If `~/Android/Sdk/platform-tools/adb devices` lists a device:

```bash
~/Android/Sdk/platform-tools/adb install -r /home/towfique/workspace/led-masjid-clock/app/android/build/outputs/apk/debug/android-debug.apk
```

Check: the app opens; the Site screen shows the "Open ZIP" text; "Open ZIP" opens the file picker; with a test ZIP (folder `english/` with the spike `screen.hds` and `progam.hds`), the card row shows "english — 128 × 64"; the Send screen shows "Not connected to WiFi" or "No display found on this WiFi", and "Send" is disabled.
If no device is available, write that in the task report. The user does the real-card tests in Task 9.

- [ ] **Step 7: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock
git add app/android
git commit -m "Add WiFi-bound connection, view model, and screens" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Release signing and README

**Files:**
- Modify: `app/android/build.gradle.kts` (release signing from `app/keystore.properties`)
- Create: `app/README.md`

**Interfaces:**
- Consumes: the app from Task 8.
- Produces: a signed release APK when `app/keystore.properties` exists; documentation for the user.

- [ ] **Step 1: Add the signing configuration**

In `app/android/build.gradle.kts`, add at the top of the file, after the `import` line:

```kotlin
import java.util.Properties

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
```

In the `android { }` block, add before `buildTypes`:

```kotlin
    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
```

In `buildTypes { release { } }`, add:

```kotlin
            signingConfig = signingConfigs.findByName("release")
```

- [ ] **Step 2: Verify the release build without a key**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :android:assembleRelease`
Expected: `BUILD SUCCESSFUL`, and `app/android/build/outputs/apk/release/android-release-unsigned.apk` exists (no key yet).

- [ ] **Step 3: Write the README**

`app/README.md`:

````markdown
# Masjid Clock Sender (Android)

The app sends HD2020 programs to Huidu WF cards (WF1, WF2, WF4) over the card WiFi.
It is for sites that have no laptop. Design: `docs/superpowers/specs/2026-10-18-android-wf-sender-design.md`.

## Make a site ZIP

1. In HD2020, export the program of each card with the U-disk option.
2. Make 1 folder for each card. Copy `screen.hds` and `progam.hds` of that card into the folder.
   Use a clear folder name, for example `english` or `hijri`. The app shows this name.
3. Compress the folders into 1 ZIP file. Give the ZIP the name of the site, for example `masjid-north.zip`.
4. Send the ZIP to the person at the site.

The app ignores `set.hds`, `smart.ss`, and `font.hds`. It never sends them.

## Build

Use Java 21. Run the commands in this folder.

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test :android:testDebugUnitTest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :android:assembleRelease
```

The release APK is `android/build/outputs/apk/release/android-release.apk`.
`local.properties` must contain `sdk.dir=` with the path of the Android SDK.

## Release key

Android installs an update over an earlier version only if both use the same key.

**CAUTION: Keep the key file and its passwords in a safe place, with a backup copy outside this computer.
If you lose the key, every phone must uninstall the app before it can install a new version.
Never commit the key or `keystore.properties` to git.**

Make the key 1 time:

```bash
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/release.jks -alias wfsender -keyalg RSA -keysize 4096 -validity 10000
```

Then make `keystore.properties` in this folder:

```properties
storeFile=keystore/release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=wfsender
keyPassword=YOUR_KEY_PASSWORD
```

## Install on a phone

1. Send the APK file to the phone (for example by WhatsApp).
2. Open the APK. If Android asks, allow "Install unknown apps" for that app.
3. Install the app.

## Use

1. Open the site ZIP with the app ("Open with" in WhatsApp), or tap "Open ZIP" in the app.
2. Select the display.
3. Connect the phone to the WiFi of the display in Android settings. The password is 88888888.
4. When the app shows "Display found", tap "Send", and then "Yes, send".
5. If the send fails, tap "Share log" and send the log to the person who sent you the ZIP.

## Manual tests before a release (spec section 7.2)

1. Visible change: send a `TEST` text program from HD2020, then send the clock program from the app.
   The display must change from `TEST` to the clock.
2. Weak signal: send from far away. The app must retry and then show the weak-signal message.
3. Mobile data on: the send must be successful.
4. No card WiFi, and a WiFi that is not a card: the status must not be "Display found".
5. Import from WhatsApp: receive a site ZIP in WhatsApp and open it with the app.

The protocol was confirmed only on a WF1 card with a 128 × 64 screen.
Test WF2 and WF4 cards and other screen sizes before you use the app with them.
````

- [ ] **Step 4: Run the full build and all tests**

Run: `cd /home/towfique/workspace/led-masjid-clock/app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew clean :protocol:test :android:testDebugUnitTest :android:assembleDebug :android:assembleRelease`
Expected: `BUILD SUCCESSFUL`; 34 protocol tests and 19 Android unit tests passed.

- [ ] **Step 5: Commit**

```bash
cd /home/towfique/workspace/led-masjid-clock
git add app/android/build.gradle.kts app/README.md
git status --short   # keystore/ and keystore.properties must NOT be in the list
git commit -m "Add release signing configuration and README" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 6: Tell the user what to do next**

The user must:
1. Make the release key (README section "Release key"). Suggest that they run the `keytool` command themselves with `! keytool ...`, because it asks for passwords.
2. Build the signed release APK.
3. Do the manual tests on a real card (README section "Manual tests before a release"), first the visible-change test.
