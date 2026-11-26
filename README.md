<p align="center">
  <img src="assets/banner.png" alt="LuminaTweaks Banner" width="100%">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Status-Stable-green?style=for-the-badge" alt="Status">
  <img src="https://img.shields.io/badge/License-Apache%202.0-orange?style=for-the-badge" alt="License">
  <img src="https://img.shields.io/badge/Android-11+-blue?style=for-the-badge&logo=android" alt="Android Version">
</p>

---

## What is LuminaTweaks?

**LuminaTweaks** is a modular Android root performance suite built with a lightweight **Rust system daemon** and a **Material Design 3 Expressive** manager app. It dynamically detects active game sessions to stabilize framerates, tune CPU/GPU scheduling, and restore power-saving defaults the moment you exit a game.

---

## Key Features

- **Adaptive Performance Profiles** — Automatic switching between *Eco*, *Balanced*, *Performance*, and *Performance Lite* with per-app overrides.
- **Lumina Game Deck** — Floating *in-game overlay* featuring live FPS/frametime telemetry, CPU/GPU radial gauges, quick profile & refresh-rate toggles, wavy media controls, freeform app shortcuts, and a split-screen widget customizer.
- **Low-Overhead Rust Daemon** — Native background service handling process detection, sysfs/thermal tuning, and hardware state synchronization with minimal memory footprint.
- **Material 3 Expressive UI** — Clean edge-to-edge Jetpack Compose interface with dynamic color theming and real-time device diagnostics.

---

## Installation

Compatible with **Android 11+** on the following root solutions:

- **KernelSU / KernelSU Next**
- **APatch**
- **Magisk**

1. Download the latest release ZIP from [Releases](https://github.com/kimal404/LuminaTweaks/releases).
2. Flash the module via your root manager and reboot your device.
3. Open **Lumina Manager** to configure per-app profiles and enable **Game Deck**.

---

## Credits & Acknowledgements

- **Author:** [@kimal404](https://github.com/kimal404)
- **[Encore Tweaks](https://github.com/Rem01Gaming/encore)** by [@Rem01Gaming](https://github.com/Rem01Gaming) — System performance tweak references and binder process-observer logic.
- **[AZenith](https://github.com/Liliya2727/AZenith)** by [@Liliya2727](https://github.com/Liliya2727) — ColorEngine theme system and UI/UX feature design references.

For complete third-party copyrights, component attributions, and license notices, see **[NOTICE.md](NOTICE.md)**.

---

## Community & Links

- [GitHub Repository](https://github.com/kimal404/LuminaTweaks)
- [Telegram Channel](https://t.me/MallArchiven)

---

## License

Licensed under the [Apache License 2.0](LICENSE).
