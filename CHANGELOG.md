## [0.4.4](https://github.com/munimtechnologies/munim-bluetooth/compare/v0.4.3...v0.4.4) (2026-07-04)

### 🐛 Bug Fixes

* resolve dependency vulnerabilities ([089bea6](https://github.com/munimtechnologies/munim-bluetooth/commit/089bea6224c836b4366d0631cf94fd2fe8c0cc17))

## [0.4.3](https://github.com/munimtechnologies/munim-bluetooth/compare/v0.4.2...v0.4.3) (2026-05-22)

### 🐛 Bug Fixes

* stabilize Bluetooth device smoke test ([3463a06](https://github.com/munimtechnologies/munim-bluetooth/commit/3463a069ce18893bc509944e8fb68d05a178ccd8))

## [0.4.1](https://github.com/munimtechnologies/munim-bluetooth/compare/v0.4.0...v0.4.1) (2026-05-16)

### 📚 Documentation

* move installation near platform matrix ([875128b](https://github.com/munimtechnologies/munim-bluetooth/commit/875128b01f19b4b78a4b269c230836ac4582ce35))

### 🛠️ Other changes

* publish Apache license metadata ([f3741b7](https://github.com/munimtechnologies/munim-bluetooth/commit/f3741b7407514982f4d8b7d2c18941b9e225104a))
* remove dependabot ([b77743f](https://github.com/munimtechnologies/munim-bluetooth/commit/b77743ff3290c35bac25b2334fa5b5249f270c14))

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
