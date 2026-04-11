# ShieldBlock Ultra - Enterprise-Grade DNS Firewall for Android

ShieldBlock Ultra is a robust, VPN-based DNS filtering solution for Android that provides real-time ad blocking, privacy protection, and network security. It establishes a local VPN tunnel to intercept DNS queries, filtering them against multi-source blocklists without sending your traffic to a remote proxy.

## 🛡️ Core Features

### 1. Advanced DNS Filtering
*   **Multi-Source Blocklists:** Integrated with StevenBlack, OISD, and 1Hosts. Support for custom URLs and local .txt/.hosts files.
*   **Regex & Wildcard Support:** Block complex domain patterns (e.g., `*.telemetry.google.com`).
*   **NXDOMAIN Response:** Locally resolves blocked domains to prevent tracking and save bandwidth.
*   **Real-time Whitelisting:** Easily unblock domains via the Live Feed or manual editor.

### 2. Privacy Analytics Dashboard
*   **Privacy Score & Grade:** Dynamic (A-F) rating based on blocking efficiency.
*   **Live Feed:** Real-time ticker showing recently intercepted tracking attempts.
*   **Network Monitor:** Live throughput monitoring (KB/s) processed through the tunnel.
*   **Visual Trends:** 24-hour bar charts showing peak activity times.

### 3. Application & Network Control
*   **Split Tunneling (App Exclusion):** Bypass the VPN for specific apps (e.g., banking or streaming).
*   **Security Audit:** Identifies high-risk apps with excessive tracker activity.
*   **Network-Specific Rules:** Automatically disable protection on trusted Wi-Fi SSIDs (Home/Work).

### 4. Smart Automation
*   **Scheduled Protection:** Set custom "Active Hours" for automatic filtering.
*   **Auto-Update Engine:** Background Worker (WorkManager) keeps blocklists fresh.
*   **Performance Profiles:** Choose between 'Performance', 'Balanced', or 'Battery Saver' modes.

## 🛠️ Technical Architecture

*   **VpnService:** Native Android API used to capture IP packets.
*   **DnsProxy:** Custom UDP parser that decodes DNS queries, checks against the `BlacklistManager` (Set-based lookup), and forwards safe queries to upstream DNS (Cloudflare, Google, etc.).
*   **Material 3 (Emerald Aegis):** A fluid, dark-mode-first design system using Material You principles.
*   **Persistence:** Encrypted SharedPreferences for config and local file-based storage for large domain databases.

## 🚀 Setup & Installation

1.  **Clone & Build:** Open the project in Android Studio (Giraffe+ recommended).
2.  **Permissions:** Upon first launch, grant "Notification" and "Location" (for Wi-Fi SSID detection) permissions.
3.  **VPN Authorization:** Accept the system VPN dialog to allow ShieldBlock to intercept traffic locally.
4.  **Update Lists:** Go to `Settings > Blocklist Sources` and trigger a "Force Update" to download the latest rules.

## ⚖️ License & Disclaimer

*ShieldBlock is a local DNS proxy. It does not provide anonymity (like a traditional VPN) but focuses on privacy and content filtering. Use responsibly.*
