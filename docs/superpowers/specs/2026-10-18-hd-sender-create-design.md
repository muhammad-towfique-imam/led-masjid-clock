# HD Sender: Create Packages and the .hdpkg Format — Design

- **Date:** 2026-10-18
- **Branch:** `android-wf-sender`
- **Code location:** `app/` (module `android`)
- **Version:** 0.3.0
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
| USB access | Direct file paths with the "All files access" permission (Android 11 and later) or the storage permission (Android 7–10). The raw-USB approach is not used: it does not support exFAT |
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

### 4.1 Home screen

- The title "HD Sender" and 2 large buttons: **Send** and **Create**.
- **Send** opens the 3-step Send page of the base spec (section 5.2).
- **Create** opens the Create page.
- The Send page and the Create page have a "Back" button that returns to the home screen.
  During a send, "Back" is disabled (base spec, section 5.2).
- A file from "Open with" (for example from WhatsApp) opens the Send page directly and loads the file.

### 4.2 Create page

The page has 4 steps. Each step shows ✔ done, ● current, or ○ waiting, as on the Send page.

1. **Allow file access**
   - Android 11 and later: the step has a button "Allow file access". It opens the Android
     "All files access" settings page for HD Sender (`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`).
     The person turns on the switch and returns to the app.
   - Android 7–10: the button asks for `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE`
     in the normal Android permission dialog.
   - The app checks the permission each time the page becomes visible. When the permission is
     given, the step is done and its button is hidden.
2. **Connect the USB drive**
   - The app lists the mounted removable volumes (section 5.1) every 2 seconds while the page is
     visible and the permission is given.
   - If there is none: "Connect the USB drive with the HD2020 export to the phone. Use a USB OTG adapter."
3. **Check the files**
   - For each removable volume, the app reads `screen.hds` and `progam.hds` at the root of the
     volume and checks them with `HdsExport`. The file names are matched without regard to case.
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
- The root folder of a volume:
  - Android 11 and later: `StorageVolume.getDirectory()`.
  - Android 7–10: the app finds the root from `Context.getExternalFilesDirs(null)`: for each folder
    on removable storage, the root is the part of the path before `/Android/data/`.
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
| `READ_EXTERNAL_STORAGE` | 7–10 (`maxSdkVersion="29"`) | Read the USB drive |
| `WRITE_EXTERNAL_STORAGE` | 7–10 (`maxSdkVersion="29"`) | Write to Downloads |

The manifest also sets `android:requestLegacyExternalStorage="true"` for Android 10.
The Send flow does not need these permissions.

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
