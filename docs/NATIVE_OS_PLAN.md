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

### 0b. BROM full-partition dump — THE brick-recovery baseline

A complete read-only dump of every partition is our replacement for a stock `.pac`
(which is not publicly available — see 0c). With it we can restore *this exact device*
to its current state from BROM mode alone.

**Blocker: FDL1/FDL2 files for SC9863A.** Options, in order of preference:

1. Extract from any stock `.pac` of a same-SoC device (Teclast P20HD, Samsung Galaxy
   A03 Core/A13, ITAB siblings, generic "SC9863A" firmwares on gsmfirmware-style sites).
   FDL is SoC-family-level, not board-level — borrowing is standard practice.
2. SharkL3 BROM exploit payloads (public research exists for SC9863A/UMS9230-family
   bootroms) — allows read/write without signed FDL, higher skill requirement.
3. Ask ITAB/Incar/Payhere support for the `PAYHERE_V1.0_20240626` package or FDL files.

**Hardware procedure** (needs the physical tablet + USB cable to this PC):

1. Power off. Hold **Volume Up** (or the board-specific BROM key — may require opening
   the case to a test point on early units) and plug USB.
2. Device enumerates as `SPRD U2S Diag` / `SCI USB2Serial` (drivers already installed:
   `drivers/SPD_Driver_R4.20.4201` in itab-firmware).
3. Use `spd_dump` (Linux/WSL) in read mode:
   `spd_dump --wait 300 exec_addr 0x65000800 read_flash partname start_sector size outputfile`
   or full dump via `--read-flash` / partition-table dump first (`r partition_table`).
4. Dump priority order — **never skip NV/RF**:

   | Tier | Partitions | Why |
   |---|---|---|
   | P0 boot chain | `splloader` (boot0/1), `sml_a/b`, `uboot_a/b`, `uboot_log`, `trustos_a/b`, `teecfg_a/b`, `pm_sys_a/b`, `hypervisor_a/b` | brick = no power/no display |
   | P0 security | `prodnv`, `persist`, `l_fixnv1/2_a/b`, `l_runtimenv1/2`, `l_modem_a/b`, `l_deltanv_a/b`, `l_gdsp_a/b`, `l_ldsp_a/b`, `gnssmodem_a/b` | IMEI/RF calibration — **read-only forever, never write** |
   | P0 OS | `boot_a/b`, `dtb_a/b`, `dtbo_a/b`, `vbmeta*`/`avbmeta_rs*`, `super`, `cache`, `metadata`, `logo`, `fbootlogo`, `misc`, `miscdata` | full OS restore |
   | P1 data | `userdata` (optional — contains our setup; 24 GB, mostly empty) | convenience, not survival |

5. Verify dumps: `spd_dump` prints per-partition SHA; store under
   `backup/brom-dump/<date>/` with the scatter/partition table.

**Risk:** read-only → low. Main risk is *not doing it* before any unlock/flash.

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

- `/vendor` full pull → `backup/partitions/vendor/` — HAL blobs, `etc/fstab`,
  `build.prop`, `lib/modules/*.ko` (exact kernel module set), `firmware/`.
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
| No FDL for SC9863A | medium | same-SoC pac extraction; SharkL3 exploit; vendor request |
| OEM unlock denied despite prop=1 | medium | BROM path; signed-image flash research |
| Board mismatch (3h10 vs 1h10) | certain | own dtb/dtbo + vendor blobs; P20HD as reference only |
| vbmeta key ≠ leaked key | unknown until dump | then need device-specific sign or verity-off exploit |
| NV/RF partition write accident | low if careful | read-only rule; dump first; never include in flash sets |
| Touch/Wi-Fi dead after port | medium | that's what libhybris/HAL reuse is for; web fallback for deck UI |

## Immediate next actions (no risk, can start now)

1. ✅ Userland snapshot + `/vendor` pull — done/in progress.
2. Pull `/product` and `/system_ext` (same method, ~1.4 GB) — blob/prop reference.
3. Try `adb reboot dsu` GSI trial (needs a GSI image — phh arm64 AB GSI ~1 GB download).
4. Source FDL1/FDL2 for SC9863A (search same-SoC pac files; check P20HD repo docs —
   its bringup notes may name the FDL source used).
5. When physical access available: BROM dump per Phase 0b.
