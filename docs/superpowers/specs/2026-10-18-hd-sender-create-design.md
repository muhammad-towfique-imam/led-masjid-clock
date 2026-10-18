# HD Sender: Create Packages and the .hdpkg Format — Design

- **Date:** 2026-10-18
- **Branch:** `android-wf-sender`
- **Code location:** `app/` (module `android`)
- **Version:** 0.3.1 (Create needs Android 11 or later)
- **Base spec:** `docs/superpowers/specs/2026-10-18-android-wf-sender-design.md`
- **Status:** Approved in conversation. Waiting for review of this written spec.

## 1. Purpose

Today, a person makes the package for a display by hand on a PC: they copy `screen.hds` and
`progam.hds` from the HD2020 U-disk export into a folder and compress it.

With this change, a person can make the package on the phone:

1. They export the program in HD2020 to a USB drive.
2. They connect the USB drive to the phone with a USB OTG adapter.
3. They make the package in the app, and then save it or share it (for example by WhatsApp)
   to the person at the remote site.

Packages use the new extension `.hdpkg`.

**Change 2026-10-18 (version 0.3.1):** Create needs Android 11 or later. All phones that use Create
run Android 11 or later, and stock Android 7–10 does not give apps a file path to a USB OTG drive.
The Send flow still works from Android 7.0.

### 1.1 Success criteria

1. A person connects a USB drive with an HD2020 U-disk export, taps "Create", and saves or
   shares an `.hdpkg` file without help.
2. The Send flow opens that `.hdpkg` file and sends it to the display.
3. The app reads USB drives with exFAT and with FAT32.

## 2. Decisions

| Topic | Decision |
|---|---|
| Package extension | `.hdpkg`. The content is a ZIP file, as before |
| Send accepts | Only `.hdpkg` files (`.zip` files are rejected) |
| USB access | Direct file paths with the "All files access" permission. Create needs Android 11 or later. The raw-USB approach is not used: it does not support exFAT |
| Save | The app asks for a file name in its own dialog and saves the file in the Downloads folder |
| Share | The app asks for a file name too, then opens the Android share sheet |
| Home screen | 2 buttons: "Send" and "Create" |

## 3. The .hdpkg package

- A ZIP file with `screen.hds` and `progam.hds` at its root. It contains no other files.
- The display name in the Send flow is the file name without `.hdpkg`.
  Example: `masjid-north-english.hdpkg` shows as "masjid-north-english — 128 × 64".
- The ZIP import rules of the base spec (section 5.1) stay the same: exactly 1 display,
  the 20 MB limit, the unsafe-path checks, and the `HdsExport` checks.

## 4. Screens

### 4.1 Navigation (changed in version 0.4.0)

- The app uses Material 3 with a fixed green color scheme. Light and dark mode follow the phone setting.
- A bottom navigation bar has 2 tabs: **Send** and **Create**. The app opens on Send.
  (Versions 0.3.x had a home screen with 2 buttons. It is removed.)
- Each tab has a top app bar with its title: "Send" or "Create a package". On Create, an info
  icon in the top app bar shares the storage report ("Share details", for support).
- The back button: on Create, it opens Send; on Send, it closes the app. During a send, the back
  button does nothing and both tabs are disabled.
- Tapping the Send tab while it is open does nothing, so a result stays on the screen.
- A file from "Open with" (for example from WhatsApp) opens the Send tab and loads the file.
- Each step is a card. Its header shows a check (done), a filled number (current), or an outlined
  number (waiting). The content of a waiting step is hidden.

### 4.2 Create page

The page has 4 steps. Each step shows ✔ done, ● current, or ○ waiting, as on the Send page.

1. **Allow file access**
   - The step has a button "Allow file access". It opens the Android "All files access" settings
     page for HD Sender (`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`). If the phone has no such
     page, the app opens the general "All files access" page, and then the app settings page.
     The person turns on the switch and returns to the app.
   - The app checks the permission each time the page becomes visible. When the permission is
     given, the step is done and its button is hidden.
   - On Android 7–10, the Create page shows only: "Create needs Android 11 or later. This phone
     has an older Android version. You can still use Send."
2. **Connect the USB drive**
   - The app lists the mounted removable volumes (section 5.1) every 2 seconds while the page is
     visible and the permission is given.
   - If there is none: "Connect the USB drive with the HD2020 export to the phone. Use a USB OTG adapter."
3. **Check the files**
   - For each removable volume, the app reads `screen.hds` and `progam.hds` at the root of the
     volume and checks them with `HdsExport`. The file names are matched without regard to case.
   - If the 2 files together are larger than 20 MB (the import limit of the Send flow), the step
     shows "The HD2020 export is larger than 20 MB. The app cannot send a program of this size."
     The app does not read files that are too large. It reads the files again only when their
     size or time changes.
   - The first volume with a valid export is used. The step shows
     "Found on: <volume name> — 128 × 64 display".
   - If no volume has a valid export: "The USB drive has no HD2020 export. Use the U-disk export of HD2020."
   - Android does not separate a USB drive from an SD card. If both have an export, the app uses
     the first one, and the step shows its volume name.
4. **Save or share**
   - 2 buttons: **Save** and **Share**. Both open the same name dialog: "File name", with the
     hint "for example masjid-north-english", and the buttons "OK" and "Cancel".
   - **Save** writes `<name>.hdpkg` to the Downloads folder (section 5.3), then shows
     "Saved: Downloads/<file name>".
   - **Share** writes `<name>.hdpkg` to the cache folder of the app, then opens the Android share
     sheet with the file (`ACTION_SEND`, type `application/octet-stream`, through a `FileProvider`).

### 4.3 Name rules

- Allowed characters: letters A–Z and a–z, digits 0–9, space, `-`, and `_`.
- Length: 1 to 50 characters, after spaces at the start and the end are removed.
- The person does not type `.hdpkg`. If they type it, the app removes it.
- If the name is not valid, the dialog shows: "Use only English letters, numbers, spaces, - and _
  (maximum 50 characters)." and "OK" is disabled.

These rules agree with the rule for folder names in a ZIP (base spec, section 5.1), so the Send
flow can always read the name.

## 5. Behavior details

### 5.1 Removable volumes

- `StorageManager.getStorageVolumes()`: the volumes that are removable and in the state `mounted`.
- The root folder of a volume is `StorageVolume.getDirectory()` (Android 11 and later).
- The volume name is `StorageVolume.getDescription(context)`.

### 5.2 Package writer

- It writes a ZIP with 2 entries, `screen.hds` and `progam.hds`, at the root, with the bytes of the
  export without change. It does not write the chunk count into `progam.hds`
  (the sender does this at send time).

### 5.3 Save to Downloads

- The folder is the public Downloads folder (`Environment.DIRECTORY_DOWNLOADS`).
- If `<name>.hdpkg` exists, the app uses `<name> (1).hdpkg`, `<name> (2).hdpkg`, and so on.
  It never overwrites a file.
- After the write, the app calls the media scanner for the file, so that it appears in the
  Files app at once.
- If the write fails: "Could not save the file. Check that the phone has free space."

### 5.4 Send flow changes

- The import accepts a file only if its name ends with `.hdpkg`, without regard to case.
  Other files: "This file is not an HD Sender package. Use a file that ends with .hdpkg."
- The display name (and the site name) is the file name without `.hdpkg`.
- The "Load file" button opens the file picker with all file types (`*/*`), because Android has
  no type for `.hdpkg`. The app checks the name after the person selects a file.
- The "Open with" intent filters stay the same (`application/octet-stream`, `application/zip`,
  `application/x-zip-compressed`).

### 5.5 Permissions

| Permission | Android versions | Purpose |
|---|---|---|
| `MANAGE_EXTERNAL_STORAGE` | 11 and later | Read the USB drive, write to Downloads |

The Send flow does not need this permission.

"All files access" is a strong permission, and Google Play reviews apps that ask for it.
The app is distributed as an APK file, so this does not block the release.

## 6. Code units

All new units are in the `android` module, package `com.masjidclock.wfsender`.

| Unit | Purpose | JVM unit tests |
|---|---|---|
| `pkg/PackageName` | Checks and cleans the name that the person types (section 4.3) | Yes |
| `pkg/PackageWriter` | Writes an `HdsExport` as an `.hdpkg` ZIP to an `OutputStream` (section 5.2) | Yes |
| `pkg/Downloads` | Finds a free file name in a folder and writes the file (section 5.3) | Yes, for the free name and the write |
| `usb/ExportFinder` | Finds the first root folder with a valid export (section 4.2, step 3) | Yes |
| `usb/RemovableVolumes` | Lists the removable volumes (section 5.1) | No (Android API) |
| `CreateViewModel` | The Create steps, the permission check, Save, and Share | No (Android API) |
| `ui/HomeScreen`, `ui/CreateScreen` | The screens | No |

The `ZipImporter` gets the `.hdpkg` name rule (section 5.4).

## 7. Testing

### 7.1 Automatic tests (JVM)

| Area | Tests |
|---|---|
| `PackageName` | Valid name. Spaces at start and end removed. `.hdpkg` typed by the person removed. Empty name. 51 characters. Not allowed characters (`/`, `.`, `é`, Bangla letters) |
| `PackageWriter` | The written package contains exactly 2 entries with the original bytes. The `ZipImporter` imports the written package, and the label is the package name |
| `Downloads` | Free name when no file exists. `name (1)` and `name (2)` when files exist. The write creates the file with the correct bytes |
| `ExportFinder` | No roots. A root without an export. A root with an invalid export. Upper-case file names. The first valid root of 2 is used |
| `ZipImporter` | A `.zip` file is rejected. An `.HDPKG` name (upper case) is accepted. The label of a package with files at its root is the name without `.hdpkg` |

### 7.2 Manual tests (on a phone)

1. The first Create asks for the file access. The second Create does not ask again.
2. With no USB drive, the app shows the message. When the drive is connected, the app finds it.
3. A USB drive without an export shows the message.
4. Save puts the file in Downloads. A second save with the same name gives " (1)".
5. Share to WhatsApp, then "Open with" → HD Sender → send it to the display.
6. Load a `.zip` file in the Send flow: the app shows the "not an HD Sender package" message.

## 8. Out of scope

- Exports in a folder on the USB drive (HD2020 writes the U-disk export at the root).
- A choice between 2 USB drives or SD cards with exports.
- Packages with more than 1 display.
- Editing a package.
