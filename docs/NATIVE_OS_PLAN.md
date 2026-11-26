# Native OS Replacement Plan — ITAB X40L Plus (SC9863A)

Goal: replace stock Android 11 with an open-source Linux userland while keeping the
deck-launcher UI and the Termux/Alpine server stack — or better, make the Linux
server *native* instead of proot-based. Expected gain: idle RAM ~1.5 GB → ~0.5 GB,
OS footprint ~3.3 GB → ~1–1.5 GB, faster boot.

Device facts (verified on-device, `docs/device/`):

- SoC: Unisoc/Spreadtrum **SC9863A** (`ro.hardware=s9863a3h10`, SharkL3, 8× Cortex-A55,
  PowerVR GE8322). Android 11, kernel 4.14.193, arm64.
- Boot state: slot `_a`, `verifiedbootstate=green`, `flash.locked=1`,
  **`sys.oem_unlock_allowed=1`** ← OEM unlock is permitted by the device.
- Partitioning: A/B + dynamic `super`, AVB/dm-verity, FBE f2fs userdata.
- Firmware origin: `ro.fota.oem=incar9863_11.0`, `ro.fota.version=PAYHERE_V1.0_20240626` —
  white-label build by Incar for a Payhere deployment. FOTA app = `IncarFota.apk`
  (`com.incar.update`), check server `my-ota-project.appspot.com` (alive, API unknown).

---

## Phase 0 — Recovery foundation (do first, in order)

### 0a. Userland snapshot — DONE

`backup/` contains the Alpine rootfs, Termux home, package list, deck-launcher prefs,
and a full `/vendor` pull. Restores the *services* quickly. Does **not** cover
bootloader/partition corruption.

### 0b. BROM full-partition dump — IN PROGRESS (breakthrough 2026-11-26)

A complete read-only dump of every partition is our replacement for a stock `.pac`
(which is not publicly available — see 0c). With it we can restore *this exact device*
to its current state from BROM mode alone.

**FDL blocker RESOLVED.** Borrowed FDL1/FDL2 from the SC9863A device pack in
`TomKing062/CVE-2022-38694_unlock_bootloader` (ZTE Blade A5 2019 variant,
`fdl/a5/`) works on this board — FDL is SoC-family-level, not board-level.

Working recipe (Windows host, SPRD Driver R4.20.4201 already installed):

1. `adb reboot autodloader` → device enumerates as `SPRD U2S Diag(COM7)`.
2. `spd_dump.exe fdl fdl1-sign.bin 0x5000 fdl fdl2-sign.bin 0x9efffe00 exec ...`
   — FDL handshake succeeds, `partition_list` returns the full GPT (73 entries,
   saved to `fdl/a5/partition.xml`; also `pgpt.bin` 32 KB primary GPT).
3. **Required patch**: stock `spd_dump.exe` `FindPort()` mis-parses the friendly
   name `SPRD U2S Diag(COM7)` (reads port digits at a fixed offset that lands past
   the digits → "find port failed"). Binary patch applied at file offset `0x10448`:
   search string `SPRD U2S Diag` → `SPRD U2S Dia` (one byte shorter, offset lands on
   the `7`). Original saved as `spd_dump.exe.orig`. The `hisense/` copy of
   `spd_dump.exe` (82 KB, different build) has the same bug — patch it the same way
   if that pack is ever needed.
4. Full-partition read: `exec path <dir> r <part> r <part> ... reset` — each `r`
   writes `<part>.bin`. One session dumps everything; files persist even if a later
   partition fails.

**Dump status**: all P0 boot-chain + NV/RF + OS-image partitions dumping to
`brom-dump/` (~5 GB total; `userdata` intentionally skipped — 24.7 GB, mostly empty,
userland already snapshotted). Known gap: `hypervsior_a/b` (note SPRD's typo'd
name) failed with "part not exist" — re-dump with the literal name afterwards.

**Brick-recovery property**: with `brom-dump/` complete, any bricked state can be
restored by re-entering download mode and `w`-riting the P0 partitions back — no
`.pac` or signed images required for the *stock* state.

### 0c. Stock `.pac` acquisition — parallel track

No public download exists for ITAB X40L Plus (checked vendor site + firmware
aggregators). Realistic sources:

- **ITAB/Incar/Payhere support request** — cite build `PAYHERE_V1.0_20240626`,
  model `ITAB_X40L_Plus`, platform `Sprd9863_11.0`. ODMs usually provide pac on request.
- **FOTA server** `my-ota-project.appspot.com` — app strings show `/fota`, `/incarupdate`,
  `/md5sum`, `/package.zip` paths; endpoint requires the app's request format (would need
  jadx decompile or traffic capture to reproduce). Could yield the official update zip.
- **Same-SoC pac** for FDL extraction only (not for flashing — wrong board = brick).

## Phase 1 — Unlock verification

`sys.oem_unlock_allowed=1` is promising but not proof — UNISOC unlock support varies.

```sh
adb reboot bootloader
fastboot devices
fastboot flashing get_unlock_ability   # or: fastboot oem device-info
```

- If unlock allowed → `fastboot flashing unlock` **wipes userdata** → Phase 0b dump
  must already be complete, and our userland snapshot makes rebuild cheap.
- If denied → project continues in "BROM-only" mode: we can still dump and, with
  signed images (leaked vbmeta key), potentially flash via BROM/ResearchDownload
  without fastboot unlock — but AVB verification behavior on UNISOC BROM flash is
  less certain; treat as research.

## Phase 2 — Image analysis (material mostly already on disk)

Already pulled / extractable without root:

- `partitions/{vendor,product,system_ext}.tar.gz` → full partition pulls —
  HAL blobs, `etc/fstab`, `lib/modules/*.ko` (exact kernel module set), `firmware/`.
  (Five root-only `build.prop` files excluded — values in `docs/device/getprop.txt`.)
- `/system` (rootfs, ~1 GB) — pull later only if needed; mostly stock AOSP.
- `/product`, `/system_ext` — pull same way if needed.
- `extracted/kernel.config`, `fstab.s9863a3h10`, `init.s9863a3h10.rc` — already in repo.
- Kernel .config confirms DSU/GSI hooks (`avb_keys=...q-gsi` in fstab).

Still locked inside `boot`/`dtb`/`dtbo`/`vbmeta` partitions — needs Phase 0b dump:

- `boot.img` → kernel Image + **device tree blob (dtb)** — the single most important
  artifact for porting (panel timing, touch address, regulators).
- `dtbo.img` → overlay DT (display/touch config on many Unisoc devices).
- `vbmeta.img` → **verify signing key**: compare against the publicly leaked
  `rsa4096_vbmeta.pem` used by Hovatek-modded UNISOC devices. If it matches, we can
  sign our own boot images (vbmeta must be exactly 1 MiB).
- `splloader`/`sml`/`uboot` → keep for unbrick; do not modify.

Kernel source: downstream SC9863A trees exist (e.g. Samsung Galaxy A03 Core /
A13 kernel — same SoC). The Teclast P20HD Droidian bringup
(`P20HD-Droidian` on GitHub) documents: Halium patches on that kernel, vendor blob
cataloging, AVB-signed boot image build. Treat it as the reference implementation —
**but it targets `s9863a1h10`; our board `s9863a3h10` needs its own dtb/vendor mapping**
(panel HX83102 init sequence, touch I2C address, PMIC rail voltages — all recoverable
from our dumped dtb + vendor blobs).

## Phase 3 — OS image build (decision point after Phase 2)

| Option | Description | Verdict |
|---|---|---|
| **A. Droidian + libhybris** | Debian userland, Android HAL via hybris — touch/Wi-Fi/audio keep working. P20HD precedent. | **Primary candidate.** Best "open source + working device" path. |
| B. phh GSI (Android) | Vanilla AOSP GSI in super partition; needs unlock + `vbmeta --disable-verity --disable-verification`. | Fallback — still Android, smaller gains. |
| C. postmarketOS | Mainline-ish Linux | **Rejected** — no HX83102 touch, WCN Wi-Fi, GE8322 GPU, VBC audio drivers. |
| D. DSU trial boot | `adb reboot dsu` with GSI — **no unlock needed**, tests GSI compatibility risk-free | Do this early — cheap signal. |

Stream deck under native Linux: AgentDeck protocol is already web-based — port the UI
to a web app served by the same Alpine→native service, or run the Android APK under
Waydroid (ARM support uncertain — likely no). Web app is the clean path.

## Phase 4 — Flash & verify

- Build signed `boot` (+ `dtbo`, `vbmeta` 1 MiB) → flash via BROM (`spd_dump write`) or
  ResearchDownload with a pac we assemble from dumped partitions.
- Keep slot `_b` untouched until `_a` boots cleanly; `misc` slot-switch as fallback.
- Unbrick path: BROM dump restore of P0 partitions — this is why 0b comes first.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| ~~No FDL for SC9863A~~ | **resolved** | borrowed ZTE Blade A5 2019 pack FDL works |
| OEM unlock denied despite prop=1 | medium | BROM path; signed-image flash research |
| Board mismatch (3h10 vs 1h10) | certain | own dtb/dtbo + vendor blobs; P20HD as reference only |
| vbmeta key ≠ leaked key | check `vbmeta_a.bin` after dump | then need device-specific sign or verity-off exploit |
| NV/RF partition write accident | low if careful | read-only rule; dump first; never include in flash sets |
| Touch/Wi-Fi dead after port | medium | that's what libhybris/HAL reuse is for; web fallback for deck UI |
| FDL session drops mid-dump | observed once | re-run `adb reboot autodloader` + same command; completed files persist |

## Immediate next actions (no risk, can start now)

1. ✅ Userland snapshot + `/vendor` pull — done.
2. ✅ **BROM partition dump** — in progress → `brom-dump/` (FDL borrowed from
   `fdl/a5/` pack; spd_dump port bug patched; see 0b for exact recipe).
3. When dump completes: re-dump `hypervsior_a/b` (typo'd name), then analyze
   `boot_a.bin` (kernel+dtb), `dtbo_a.bin`, `vbmeta_a.bin` (signing key check vs
   leaked `rsa4096_vbmeta.pem`).
4. Try `adb reboot dsu` GSI trial (needs a GSI image — phh arm64 AB GSI ~1 GB download).
5. Keep `.pac` acquisition as parallel track (0c) — still no public source.
