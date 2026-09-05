## [0.7.1](https://github.com/munimtechnologies/munim-bluetooth/compare/v0.7.0...v0.7.1) (2026-09-05)

### 🐛 Bug Fixes

* **android:** report link loss on established connections and guard shared state ([8be7d65](https://github.com/munimtechnologies/munim-bluetooth/commit/8be7d65a21b0e86e934957f8fcd4ded1ba311dcf))
* **ios:** run CoreBluetooth calls on the main thread and validate L2CAP PSMs ([08c8704](https://github.com/munimtechnologies/munim-bluetooth/commit/08c8704b1bbb027fe37861b6db645ab77681984d))

### 🛠️ Other changes

* drop GitHub Actions; release locally via release:local ([d2b10a7](https://github.com/munimtechnologies/munim-bluetooth/commit/d2b10a7367d94f68a2b89c03134ba4bb2a1405f9))
* drop the duplicate 0.7.0 changelog entry ([6f30e12](https://github.com/munimtechnologies/munim-bluetooth/commit/6f30e123f2247db232a6953e7e0f9cadf80e5d76))
* **release:** 0.7.0 [skip ci] ([b895955](https://github.com/munimtechnologies/munim-bluetooth/commit/b895955ee5ddde5f98c69da318ea4a3bff619e6a))
* restore GitHub Actions workflows ([6d19e23](https://github.com/munimtechnologies/munim-bluetooth/commit/6d19e23a839fa8cac6e12e294253567a52e179cb))
* upgrade Nitro to 0.36.5 and regenerate bindings ([2c4b62b](https://github.com/munimtechnologies/munim-bluetooth/commit/2c4b62bfea57cda1de078fd343afec254fa18818)), closes [margelo/nitro#1573](https://github.com/margelo/nitro/issues/1573)

## [0.7.0](https://github.com/munimtechnologies/munim-bluetooth/compare/v0.6.1...v0.7.0) (2026-08-12)

### ✨ Features

* complete iOS parity - peripheral manual request mode, GATT operation queue, manufacturer data entries, bond state events, scan filters, and L2CAP write backpressure ([4ac842c](https://github.com/munimtechnologies/munim-bluetooth/commit/4ac842cfd5b91a67e5867a66df98f341d4a280a7))

## [0.6.1](https://github.com/munimtechnologies/munim-bluetooth/compare/v0.6.0...v0.6.1) (2026-08-07)

### 🐛 Bug Fixes

* restore cross-platform BLE discovery ([add08e4](https://github.com/munimtechnologies/munim-bluetooth/commit/add08e4ce795f887a0882b8840fece5c393ed345))

### 🛠️ Other changes

* let semantic-release generate changelog ([c9cef34](https://github.com/munimtechnologies/munim-bluetooth/commit/c9cef3473d46b726d22eabe2e7eaa428a36b4b2e))

## [0.6.0](https://github.com/munimtechnologies/munim-bluetooth/compare/v0.5.0...v0.6.0) (2026-08-04)

### ✨ Features

* expose Classic Bluetooth discovery metadata ([0f2c952](https://github.com/munimtechnologies/munim-bluetooth/commit/0f2c952777ffe8a89294cbc6f1c234d0dd79a045))

## [0.5.0](https://github.com/munimtechnologies/munim-bluetooth/compare/v0.4.5...v0.5.0) (2026-07-13)

### ✨ Features

* add encryptionRequired option to openL2CAPChannel ([ac2cc8a](https://github.com/munimtechnologies/munim-bluetooth/commit/ac2cc8a98f56347a0dcb4f75fe208da1aef82473))

## [0.4.5](https://github.com/munimtechnologies/munim-bluetooth/compare/v0.4.4...v0.4.5) (2026-07-11)

### 🐛 Bug Fixes

* scope Android Bluetooth permissions by operation ([1d64351](https://github.com/munimtechnologies/munim-bluetooth/commit/1d643517065e02e1d705eab428fa91363a6d86f5))

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
