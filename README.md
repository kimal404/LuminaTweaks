<p align="center">
  <img src="assets/banner.png" alt="LuminaTweaks Banner" width="100%">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Status-Stable-green?style=for-the-badge" alt="Status">
  <img src="https://img.shields.io/badge/License-Apache%202.0-orange?style=for-the-badge" alt="License">
  <img src="https://img.shields.io/badge/Android-11+-blue?style=for-the-badge&logo=android" alt="Android Version">
  <img src="https://img.shields.io/badge/Daemon-Rust-black?style=for-the-badge&logo=rust" alt="Rust Daemon">
</p>

---

## ⚡ What is LuminaTweaks?

**LuminaTweaks** is an automated Android root optimization module powered by a low-overhead, native Rust daemon (`luminad`) paired with a modern Jetpack Compose manager application.

Rather than running heavy polling scripts or applying static, generic sysctl tweaks, LuminaTweaks dynamically adapts system parameters, memory bus frequencies (DDR/LLCC/L3), GPU pipelines, and SoC thermal headroom in real time based on active foreground state. It is engineered to eliminate frame pacing jitter and sustain peak framerates during heavy gaming, while immediately restoring dynamic idle baselines when returning to daily tasks.

---

## 📦 Installation

LuminaTweaks requires root privileges on Android 11+ and is compatible with:

* **KernelSU** / **KernelSU Next**
* **APatch**
* **Magisk**

Flash the release package through your root manager and reboot.

---

## 🙏 Credits & Acknowledgements

### Core Development
* **Author:** [@kimal404](https://github.com/kimal404)

### Inspirations & Upstream References
* **[Encore Tweaks](https://github.com/Rem01Gaming)** by [@Rem01Gaming](https://github.com/Rem01Gaming) — Low-level interconnect bus scaling, KGSL power level logic, and hardware throttling bypass architecture.
* **[AZenith](https://github.com/Liliya2727)** by [@Liliya2727](https://github.com/Liliya2727) — Scheduler governor tuning, asynchronous sysfs dispatching, and daemon structural insights.

---

## 📢 Official Channels

<p align="left">
  <a href="https://github.com/kimal404/LuminaTweaks">
    <img src="https://img.shields.io/badge/GitHub-Repository-black?style=for-the-badge&logo=github" alt="GitHub Repo">
  </a>
  <a href="https://t.me/MallArchiven">
    <img src="https://img.shields.io/badge/Telegram-Channel-blue?style=for-the-badge&logo=telegram" alt="Telegram Channel">
  </a>
</p>

---

## ⚖️ License

Licensed under the **Apache License 2.0**.
