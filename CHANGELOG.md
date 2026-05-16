## [0.4.0](https://github.com/munimtechnologies/munim-bluetooth/compare/v0.3.27...v0.4.0) (2026-05-16)

### ✨ Features

* add complete device bluetooth transports ([f48cf6b](https://github.com/munimtechnologies/munim-bluetooth/commit/f48cf6bbb4a725543a45639e2202618911bf3aa4))

# Changelog

## 0.4.0

- Added iOS Apple Multipeer Connectivity discovery, invitation, peer state, and encrypted peer messaging APIs.
- Completed iOS central write/subscribe paths and iOS peripheral GATT read/write/subscribe request handling.
- Added platform capability reporting for optional BLE, Classic Bluetooth, L2CAP, and Multipeer features.
- Improved Android BLE robustness with scan failure events, advertising payload handling, service cleanup, timeouts, PHY, extended advertising, L2CAP, Classic Bluetooth, and foreground background-session support where the OS allows it.
- Added Expo config plugin support for Bluetooth background modes, local network usage, and Multipeer Bonjour services.
- Updated README support matrix, device-to-device messaging guidance, background/terminated behavior notes, and release metadata.
