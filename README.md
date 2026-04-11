# ShieldBlock - Advanced DNS Firewall & VPN

ShieldBlock is a powerful, privacy-focused Android application that acts as a local VPN to intercept and filter DNS requests. It effectively blocks ads, trackers, social media widgets, and malicious domains system-wide.

## 🚀 Key Features

### 🛡️ System-Wide Protection
- **Local VPN Interception**: Blocks ads and trackers across all your apps using a high-performance local DNS proxy.
- **NXDOMAIN Blocking**: Instantly drops requests to blacklisted domains, saving data and improving battery life.
- **Real-time Statistics**: Live dashboard tracking blocked requests and estimated data savings.

### ⚙️ Filtering & Customization
- **Multiple Blocklists**: Choose from standard Adblock, Social Media blockers, and Fake News filters.
- **Custom Sources**: Add your own hosts file URLs (raw text) to expand your protection.
- **Whitelist Management**: Easily allow specific domains that you trust or need.
- **Import/Export**: Backup and restore your whitelist with simple text-based import/export.

### 🌐 Advanced Network Tools
- **Split Tunneling (App Exclusion)**: Exclude specific apps from the VPN tunnel to ensure compatibility with local network services or banking apps.
- **DNS Benchmark**: Test the latency of popular DNS providers (Google, Cloudflare, AdGuard) to find the fastest one for your location.
- **Custom DNS**: Set your own preferred DNS upstream server (e.g., 1.1.1.1 or 8.8.8.8).

### 🎨 Modern Experience
- **Emerald Aegis Theme**: A sleek, Material 3 dark theme with fluid animations and pulse effects.
- **Home Screen Widget**: Quick-toggle protection and view blocked stats without opening the app.
- **Auto-Start**: Optionally start protection automatically when your device boots up.
- **Detailed Logs**: Searchable activity log to monitor every intercepted request.

## 🛠️ Technology Stack
- **Kotlin**: Core application logic.
- **VpnService API**: For local packet interception.
- **WorkManager**: For periodic background blocklist updates.
- **Material 3**: Modern, accessible UI components.
- **OkHttp**: Efficient network requests for blocklist updates.

## 📱 How It Works
ShieldBlock establishes a local VPN tunnel on your device. Unlike traditional VPNs, it doesn't route your traffic to a remote server. Instead, it only intercepts DNS queries (UDP Port 53). Queries for blocked domains are answered with an 'NXDOMAIN' (non-existent domain) response locally, while safe queries are forwarded to your chosen DNS provider.

---
*ShieldBlock - Your data, your rules.*
