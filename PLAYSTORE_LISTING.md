# 🚀 Google Play Store Listing & Submission Guide: App Launcher

**App Title**: `App Launcher: Minimal & Clean`  
**Package Name**: `com.cleanlauncher.app`  
**Publisher**: The Competitive Edge  
**Category**: Personalization / Tools  
**Content Rating**: Everyone (PEGI 3 / IARC 3+)  
**Privacy Policy URL**: [https://bapanayya.github.io/App-Launcher/privacy-policy.html](https://bapanayya.github.io/App-Launcher/privacy-policy.html)  
**Target SDK**: 35 (Android 15 ready)  
**Min SDK**: 26 (Android 8.0 Oreo+)  

---

## 📝 Store Metadata (Ready to Copy-Paste)

### 1. App Title (Max 30 characters)
```text
App Launcher: Minimal & Clean
```
*(Length: 29 characters)*

---

### 2. Short Description (Max 80 characters)
```text
Ultra-clean categorized app launcher with wallpaper transparency & reminders.
```
*(Length: 78 characters)*

---

### 3. Full Description (Max 4000 characters)
```text
App Launcher is a modern, ultra-clean, and privacy-first Android home launcher engineered by The Competitive Edge. It transforms cluttered, multi-page home screens into a sleek, intentional single-screen experience with full wallpaper transparency, instant categorization, and smart voice reminders.

Unlike conventional launchers that scatter newly installed apps across endless home screen slides, App Launcher keeps your desktop completely clutter-free. It features an elegant top hub for your essential daily apps, a dedicated bottom dock, and organizes everything else inside an intuitive, categorized app drawer.

🌟 KEY FEATURES:

🖼️ 1. Complete Wallpaper & Screen Saver Transparency
• Your customized wallpaper and screen savers remain 100% visible behind the home screen.
• Frosted glassmorphic cards with crisp white typography and subtle drop shadows ensure complete text legibility across all wallpapers (bright, dark, or multi-colored).
• Smooth translucent scrim transitions when opening your categorized app drawer.

📑 2. Three Flexible Layout Modes
• Sections Mode: Categorized continuous scroll with translucent cards, headers, and app counts.
• Grid Mode: Compact 4-column app grid for quick visual scanning.
• Minimal List Mode: High-density vertical list with compact icons, bold titles, and category badge chips.
• Automatically remembers your preferred layout mode across app launches.

🏷️ 3. Dynamic Category Customization & Reordering
• Create custom app categories on the fly.
• Move categories up and down (▲ / ▼) to tailor your preferred viewing order.
• Reassign any app to any category with an intuitive long-press contextual sheet.

⏰ 4. Smart App Reminders with Voice & Buzzer Alerts
• Never miss important app tasks (e.g. daily attendance, routine check-ins, or scheduled updates).
• Set one-time or daily repeating reminders for any installed application.
• Features an exact alarm buzzer accompanied by customizable Text-to-Speech (TTS) voice announcements (e.g. "You Need to Post Attendance on your APFRS app").
• Automatically reschedules your alarms after device reboots.

🛡️ 5. 100% On-Device Privacy & Zero Data Collection
• Operates completely offline without requiring cloud logins or remote servers.
• Zero personal data collected, zero telemetry, and zero tracking SDKs.
• Zero advertisements: no pop-ups, no banners, and no sponsored clutter.

Developed with pride by The Competitive Edge.
Official Channel: youtube.com/@TheCompetitiveEdge-b4z
```

---

## 🎨 Google Play Graphic Assets Location

All required store graphics have been generated and are located in your workspace:

| Asset | Dimensions | File Path |
|---|---|---|
| **App Icon** | 512 x 512 px (PNG, 32-bit) | `assets/playstore/app-icon-512x512.png` |
| **Feature Graphic** | 1024 x 500 px (PNG) | `assets/playstore/feature-graphic-1024x500.png` |
| **Phone Screenshot 1** | 1080 x 1920 px (PNG) | `assets/playstore/screenshots/screenshot-1-transparent-home.png` |
| **Phone Screenshot 2** | 1080 x 1920 px (PNG) | `assets/playstore/screenshots/screenshot-2-categorized-drawer.png` |
| **Phone Screenshot 3** | 1080 x 1920 px (PNG) | `assets/playstore/screenshots/screenshot-3-layout-modes.png` |
| **Phone Screenshot 4** | 1080 x 1920 px (PNG) | `assets/playstore/screenshots/screenshot-4-voice-reminders.png` |

---

## 📋 Google Play Console: Policy Questionnaire Answers

When completing **Policy and programs** &rarr; **App content** in Google Play Console:

1. **Privacy Policy**:
   - URL: `https://bapanayya.github.io/App-Launcher/privacy-policy.html`
2. **App Access**:
   - Select: **All functionality is available without restrictions** (No login, credentials, or membership required).
3. **Ads**:
   - Select: **No, my app does not contain ads**.
4. **Content Rating (IARC Questionnaire)**:
   - Category: **Utility, Productivity, Communication, or other**
   - Violence, Sexual Content, Language, Controlled Substances: Select **No** to all.
   - Result: **PEGI 3 / Everyone / IARC 3+**.
5. **Target Audience & Content**:
   - Target age groups: Select **18 and over** (or 13+).
   - Could your store listing appeal to children?: Select **No**.
6. **News Apps**:
   - Select: **No**.
7. **COVID-19 Contact Tracing & Status Apps**:
   - Select: **My app is not a COVID-19 contact tracing or status app**.
8. **Data Safety**:
   - Does your app collect or share any of the required user data types?: Select **No**.
   - Is all user data handled locally on the device?: Select **Yes**.
   - Note: The app does not collect, transmit, or share any user or device data.
9. **Government Apps**:
   - Select: **No, this app is not developed by or on behalf of a government**.
10. **Financial Features**:
    - Select: **My app does not provide any financial features**.
11. **Health Apps**:
    - Select: **My app does not provide health-related features**.

---

## 🔒 Sensitive Permissions Declaration: Package Visibility (QUERY_ALL_PACKAGES)

Google Play Console requires a specific declaration for `QUERY_ALL_PACKAGES`:

1. **Permitted Core Functionality**:
   - Select: **Launcher / Home Screen Application**
2. **Why does your app need broad package visibility?**:
   ```text
   App Launcher is an Android Home Screen Launcher (declaring android.intent.category.HOME in AndroidManifest.xml). Its core and primary responsibility is to discover, categorize, and launch all user-installed applications on the device. Without querying installed packages, the launcher cannot display applications in the categorized app drawer or enable search and launch functionality. All package data is read strictly into local memory and is never collected, stored remotely, or transmitted off the device.
   ```
3. **Video / Demonstration Link (if requested by Play review)**:
   - Point to a short unlisted YouTube video showing the launcher discovering and launching apps from the drawer.

---

## 📦 Android App Bundle (.aab) & Keystore Information

- **Signed AAB Bundle**: `AppLauncher.aab` (3.45 MB, optimized with R8)
- **Release Keystore File**: `app/upload-keystore.jks`
- **Keystore Alias**: `cleanlauncher`
- **Store / Key Password**: `cleanlauncher123`
- **Validity**: Until **February 10, 2054** (10,000 days)
- **Target SDK**: `35` (Android 15)
- **Min SDK**: `26` (Android 8.0)
