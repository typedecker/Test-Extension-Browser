# Extension Browser for Android

A fully functional Android browser that supports Firefox WebExtensions - the same extension format used by Firefox on desktop. This allows you to use ad blockers, password managers, dark mode extensions, and thousands of other browser extensions directly on your Android device.

## Key Features

- Full Browser Functionality: Navigation, tabs, bookmarks, history, find-in-page
- Desktop-Class Extensions: Install .xpi files from Firefox Add-ons or other sources
- WebExtension API Support: Content scripts, background scripts, browser actions, page actions
- Tab Management: Multiple tabs with proper session handling
- URL Handling: Deep links, search queries, HTTPS enforcement
- Fullscreen Support: For video playback and immersive browsing
- Extension Manager: Install, remove, enable/disable extensions from within the app

## How It Works

This app uses GeckoView - Mozilla's official Android embedding SDK for Firefox. GeckoView provides:

1. The same rendering engine as Firefox (Quantum/SpiderMonkey)
2. Native WebExtension support - no hacks or workarounds needed
3. Desktop user agent mode - extensions behave exactly like on desktop Firefox

Extensions are loaded via GeckoView's WebExtensionStore, which handles:
- Parsing manifest.json (both Manifest V2 and V3)
- Loading content scripts into web pages
- Running background/service workers
- Providing browser API access to extension code
- Managing extension permissions

## Building the App

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34 (API level 34)
- Minimum Gradle version: 8.5

### Build Steps

1. Open in Android Studio:
   - File > Open > Select the BrowserWithExtensions folder

2. Sync Gradle:
   - Android Studio will automatically sync dependencies
   - This downloads GeckoView (~200MB) and other libraries

3. Build:
   cd BrowserWithExtensions
   ./gradlew assembleDebug

4. Install on Device:
   ./gradlew installDebug

### APK Locations

- Debug APK: app/build/outputs/apk/debug/app-debug.apk
- Release APK: app/build/outputs/apk/release/app-release.apk

## Using Extensions

### Installing an Extension

1. From a File:
   - Download any .xpi file (from addons.mozilla.org or other sources)
   - Open the app > Tap the Extensions FAB button
   - Tap "From File" and select your .xpi file

2. From a URL:
   - In the Extensions screen, tap "From URL"
   - Enter the direct download link to an .xpi file
   - Example: https://addons.mozilla.org/firefox/downloads/file/XXXXX/uBlock_Origin-1.xpi

3. By Opening an XPI File:
   - Download an .xpi file in any file manager
   - Tap it > Choose "Extension Browser" to open
   - The extension will be installed automatically

### Recommended Extensions to Try

| Extension | Purpose |
|-----------|---------|
| uBlock Origin | Ad blocking |
| Dark Reader | Dark mode for all websites |
| Violentmonkey | Userscript manager |
| Bitwarden | Password management |
| Stylus | Custom CSS/themes |

### Extension Compatibility

Most Firefox desktop extensions work because:

- Content scripts (injecting JS/CSS into pages)
- Browser actions (toolbar icons)
- Page actions
- Background scripts/service workers
- WebRequest API (for ad blocking)
- Storage API
- Tabs API (limited on mobile)
- Context menus

Some limitations:
- Extensions requiring desktop-specific features may not work fully
- Some UI-heavy extensions designed for desktop may look awkward on mobile

## Project Structure

BrowserWithExtensions/
  app/src/main/java/com/browserextensions/browserwithextensions/
    BrowserApplication.kt          - App init + GeckoRuntime setup
    MainActivity.kt                - Main browser with GeckoView
    ExtensionsActivity.kt          - Extension manager UI
    ExtensionsAdapter.kt           - RecyclerView adapter for extensions
    ExtensionInstallActivity.kt    - Handles .xpi file opening
    GeckoRuntimeExtensionsManager.kt - Extension loading/unloading logic
  app/src/main/res/
    layout/                        - UI layouts
    menu/                          - Toolbar menus
    values/                        - Strings, colors, themes
    mipmap-*/                      - App icons
  build.gradle                     - Dependencies including GeckoView

## Troubleshooting

Extensions not loading?
- Make sure the .xpi file is from Firefox (not Chrome)
- Check Logcat for errors: adb logcat | grep ExtManager
- Some extensions require specific permissions that may need manual approval

App crashes on startup?
- Ensure GeckoView downloaded correctly during Gradle sync
- Try clearing app data and reinstalling

Extension doesn't work as expected?
- The extension may use desktop-only APIs
- Try a different version of the extension
- Check if it requires permissions not available on Android

## License

MIT License - Feel free to modify and distribute.
