# Standardisasi Aturan Versi Aplikasi (Versioning Rules)

Dokumen ini berisi pedoman resmi bagi AI Agent maupun Developer dalam memperbarui versi aplikasi **MotoCare** setiap kali melakukan perubahan kode.

---

## 📌 Format Penomoran Versi (Semantic Versioning - SemVer)

Aplikasi menggunakan format penomoran **`versionName`** berupa `MAJOR.MINOR.PATCH` (contoh: `1.0.0`) dan **`versionCode`** berupa angka bulat berurutan (contoh: `1`, `2`, `3`).

### 1. Kategori Kenaikan Versi (`versionName`)

Setiap perubahan pada kode harus diklasifikasikan ke dalam salah satu dari tiga kategori berikut untuk menaikkan `versionName`:

| Jenis Perubahan | Deskripsi | Aturan Perubahan `versionName` | Contoh |
| :--- | :--- | :--- | :--- |
| **PATCH** (Bug Fix) | Perbaikan bug, perbaikan typo, peningkatan performa ringan, atau penyesuaian visual kecil yang tidak menambah fungsionalitas baru. | Naikkan angka terakhir (paling kanan). | `1.0.0` ➡️ `1.0.1` |
| **MINOR** (Fitur Baru) | Penambahan fitur baru, halaman baru, atau fungsionalitas interaktif baru tanpa merusak fitur lama. | Naikkan angka tengah, lalu reset angka PATCH ke `0`. | `1.0.1` ➡️ `1.1.0` |
| **MAJOR** (Update Besar) | Perubahan besar pada arsitektur aplikasi, perombakan total desain UI, migrasi database besar, atau pembaruan yang mengubah alur utama aplikasi secara signifikan. | Naikkan angka pertama (paling kiri), lalu reset angka MINOR dan PATCH ke `0`. | `1.1.0` ➡️ `2.0.0` |

### 2. Aturan Wajib `versionCode`

*   **`versionCode` wajib naik +1** untuk **setiap rilis baru** (baik itu kategori PATCH, MINOR, maupun MAJOR).
*   Hal ini sangat penting karena sistem operasi Android dan Google Play Store menggunakan `versionCode` untuk mendeteksi apakah suatu APK merupakan versi terbaru dari versi yang sudah terinstal sebelumnya.

---

## 🤖 Instruksi Khusus untuk AI Agent

Setiap kali Anda (AI Agent) diminta untuk memperbaiki bug atau menambahkan fitur baru:
1.  **Analisis jenis perubahan** yang baru saja Anda lakukan (apakah PATCH, MINOR, atau MAJOR).
2.  Buka file `/app/build.gradle.kts`.
3.  Perbarui nilai **`versionCode`** dengan menambahkannya sebanyak `+1`.
4.  Perbarui nilai **`versionName`** sesuai aturan SemVer di atas.
5.  Selesaikan tugas dan informasikan nomor versi baru tersebut kepada pengguna dalam rangkuman akhir Anda.
