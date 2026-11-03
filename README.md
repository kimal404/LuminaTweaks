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

## ⚡ Overview / Ringkasan

**LuminaTweaks** is a modern runtime tweak suite for dynamic performance and graphics tuning, driven by a low-overhead native Rust daemon (`luminad`) and a modern Jetpack Compose dashboard. It provides granular system profiling, advanced texture LOD tuning, and hardware diagnostics to ensure optimal gaming stability without sacrificing system integrity.

**LuminaTweaks** adalah rangkaian penyesuaian (*tweak suite*) modern untuk optimasi performa dan tuning grafis tingkat lanjut, ditenagai oleh daemon native Rust (`luminad`) dengan overhead rendah serta antarmuka Jetpack Compose. Menghadirkan pengaturan profil sistem granular, kontrol LOD tekstur grafis, dan diagnostik hardware untuk stabilitas gaming maksimal tanpa mengorbankan integritas sistem.

---

## 📖 Quick Guide / Panduan Ringkas

### 🤖 Optimization / Optimasi

* **Universal Boot Baseline:** Automatically configures TCP BBR, applies low-latency storage I/O schedulers (Kyber / mq-deadline), and locks thermal zones to a predictable `step_wise` governor at boot.<br>
  **Baseline Boot Universal:** Mengonfigurasi TCP BBR secara otomatis, menerapkan I/O scheduler storage latensi rendah (Kyber / mq-deadline), dan mengunci zona termal ke governor `step_wise` yang stabil saat booting.
* **Dynamic Profile Switcher:** Automatically switches system profiles (**Performance**, **Performance Lite**, **Balanced**, or **Eco**) based on active foreground state.<br>
  **Pengalih Profil Dinamis:** Beralih profil sistem secara otomatis (**Performa**, **Performa Ringan**, **Seimbang**, atau **Hemat Daya**) mengikuti status aplikasi di latar depan.

### 🌐 Core Features / Fitur Utama

* **Texture LOD Offset (ANGLE Pipeline):** Fine-tune texture Level of Detail from **-3** (forcing maximum 16x Anisotropic Filtering quality) up to **+4** (reducing TMU/DRAM GPU bandwidth overhead).<br>
  **Offset LOD Tekstur (Pipeline ANGLE):** Mengatur Level of Detail (LOD) tekstur dari rentang **-3** (memaksa kualitas 16x Anisotropic Filtering maksimal) hingga **+4** (meringankan beban bandwidth TMU/DRAM GPU).
* **Pixel Sharp Mode:** Force raw nearest-neighbor texture filtering for sharp, pixel-clean rendering without unwanted blur.<br>
  **Mode Pixel Sharp:** Memaksa pemfilteran tekstur *nearest-neighbor* untuk tampilan tajam tanpa efek blur.
* **Refresh Rate Controller:** Supports global display refresh rate presets and granular Per-App configurations with real-time hardware Hz polling.<br>
  **Kontrol Refresh Rate:** Mendukung preset refresh rate global maupun konfigurasi per-aplikasi dengan pemantauan Hz hardware secara *real-time*.
* **Memory & ZRAM Management:** Optimizes memory swap behavior and compression algorithms for responsive multitasking.<br>
  **Manajemen Memori & ZRAM:** Mengoptimalkan alokasi swap memori dan algoritma kompresi untuk multitasking yang responsif.
* **Thermal Throttling Toggle:** Safely toggle aggressive thermal throttling restrictions for sustained framerate stability.<br>
  **Saklar Thermal Throttling:** Kontrol pembatasan thermal throttling secara aman untuk menjaga stabilitas frame rate jangka panjang.
* **Hardware Diagnostic Suite:** Integrated dashboard to inspect live battery health cycles, UFS storage integrity, and kernel system logs.<br>
  **Suite Diagnostik Hardware:** Dasbor terintegrasi untuk memeriksa siklus kesehatan baterai, integritas penyimpanan UFS, dan log kernel sistem.

---

## 📦 Installation / Pemasangan

This is a root module compatible across modern Android root environments. Install via your preferred root manager:

Modul root ini kompatibel dengan berbagai lingkungan root Android modern. Pasang melalui manajer root pilihan Anda:

* **KernelSU** (v0.9.0+)
* **APatch** (v10763+)
* **Magisk** (v26.0+)

> **Requirement:** Android 11 or higher (Android 11+).

---

## 🛠️ Contributing & Bug Reports / Kontribusi & Laporan Masalah

We welcome contributions to make **LuminaTweaks** even better!

Kami menyambut segala bentuk kontribusi untuk menjadikan **LuminaTweaks** lebih baik!

* **Report Bugs:** Open an [Issue](https://github.com/kimal404/lumina-tweaks/issues) with detailed logs from `/data/adb/luminatweaks/luminad.log` or logcat.<br>
  **Laporkan Bug:** Buka [Issue](https://github.com/kimal404/lumina-tweaks/issues) dan lampirkan log dari `/data/adb/luminatweaks/luminad.log` atau logcat.
* **Pull Requests:** Fork the repository, create a branch for your feature, and submit a PR with clear documentation.<br>
  **Pull Request:** Fork repositori, buat branch untuk fitur Anda, dan kirimkan PR disertai dokumentasi yang jelas.

---

## 🙏 Credits & Acknowledgements / Kontribusi & Ucapan Terima Kasih

### Core Developers / Pengembang Utama

| Role / Peran | Developer / Pengembang |
| :--- | :--- |
| **Author & Daemon Developer** | [@MalCLA](https://github.com/kimal404) |

### References & Inspirations / Referensi & Inspirasi

| Area / Bidang | Credits / Penghargaan |
| :--- | :--- |
| **Architecture & Daemon Insights** | Android Root & Kernel Optimization Community |
| **Graphics Pipeline** | Google ANGLE Project |

---

## 📢 Stay Updated / Komunitas Resmi

Get the latest updates, experimental builds, and discussions through our official channels:

Dapatkan pembaruan rilis terbaru, build eksperimental, dan ruang diskusi melalui saluran resmi:

<p align="left">
  <a href="https://github.com/kimal404/lumina-tweaks">
    <img src="https://img.shields.io/badge/GitHub-Repository-black?style=for-the-badge&logo=github" alt="GitHub Repo">
  </a>
  <a href="https://t.me/">
    <img src="https://img.shields.io/badge/Telegram-Channel-blue?style=for-the-badge&logo=telegram" alt="Telegram Channel">
  </a>
</p>

---

## ⚖️ License / Lisensi

This project is licensed under the **Apache License 2.0**.

Proyek ini dilisensikan di bawah **Apache License 2.0**.

> Licensed under the Apache License, Version 2.0 (the "License");
> you may not use this file except in compliance with the License.
> You may obtain a copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>
