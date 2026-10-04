# HackExLogger

Vibe-coded to the moon and back, just like the game.

**HackExLogger** is an open-source companion and organization tool for the mobile game **HackEx 2**. It allows players to track, search, validate, and export virtual in-game target intelligence (such as IP addresses, player names, software levels, and in-game crypto wallet addresses) gathered during gameplay.

---

## Features

- **On-Demand Game Screen Scraper**: Captures visible text from the HackEx game interface when manually triggered via a floating quick-action overlay button.
- **In-Game Overlay Toolbar**: Floating camera button and target validation controls overlay the game window for fast target clipboard copying and validation tracking.
- **Local On-Device Database**: Persists collected target intelligence locally on-device using Android `SharedPreferences` with thread-safe access.
- **Regex Game Text Parser**: Extracts in-game target profiles, software inventories, and activity logs using pattern matching tailored to HackEx UI text layouts.
- **Search & Filtering**: Quick filter target records by virtual IP address, username, software name, or software level.
- **JSON Import & Export**: Export your target database to JSON or import target bundles using Android's Storage Access Framework.

---

## System Permissions Explained

HackExLogger requires specific Android permissions to provide overlay controls and read in-game screen text:

| Permission | Purpose |
| :--- | :--- |
| `SYSTEM_ALERT_WINDOW` ("Display over other apps") | Renders the floating quick-action overlay camera button and validation toolbar over the HackEx game window. |
| `FOREGROUND_SERVICE` | Keeps the floating overlay service active during gameplay sessions. |
| `BIND_ACCESSIBILITY_SERVICE` | Used strictly as an **on-demand UI screen reader** to read visible text node elements on screen when the user manually taps the floating overlay button while playing HackEx. |

---

## Architecture Overview

```
com.whisk.hackexlogger
 ├── MainActivity                   # Dashboard for database browsing, searching, and JSON backup/restore.
 ├── ScraperAccessibilityService    # Accessibility Service that reads active window text nodes upon user trigger.
 ├── OverlayService                 # Foreground Service presenting floating UI widgets over the game interface.
 ├── DatabaseManager                # Thread-safe repository for persisting and merging target records on-device.
 ├── HackExParser                   # Regex pattern matcher for extracting in-game stats, software, and logs.
 ├── TargetAdapter                  # RecyclerView adapter for rendering target cards and headers.
 ├── TargetRecord & ConsoleBundle   # Data models and JSON export/import schema definitions.
```

---

## How It Works

1. **Enable Permissions**: Launch HackExLogger and grant "Display over other apps" and the "HackExLogger Accessibility Service" when prompted.
2. **Open HackEx**: Launch the HackEx game app. A small floating camera button will appear on your screen.
3. **Capture Data**: Navigate to a target's profile, software page, wallet screen, or log screen in HackEx, and tap the floating camera button.
4. **View & Search**: Return to HackExLogger to view parsed target stats, search software levels, or export your target database as a JSON bundle.

---

## Privacy & Local Storage

All target data created or parsed by HackExLogger is stored **100% locally on your device** inside private app storage. No external servers or remote telemetry APIs are used.

---

## License

This project is open-source and available under the [MIT License](LICENSE).
