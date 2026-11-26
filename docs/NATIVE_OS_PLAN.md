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

## Phase 1 — Unlock verification → REVISED: signing keys already leaked

`sys.oem_unlock_allowed=1` is promising but not proof — UNISOC unlock support varies.

**However, BROM key analysis (2026-11-26) changed the picture completely:**

Every AVB signing key this device uses is a **publicly leaked key**
(`TheGammaSqueeze/UnisocBypass/keys`, originally from Hovatek leaks). Verified by
searching each 1032-byte `rsa4096_*_pub.bin` blob inside the dumped partitions:

| Partition | Signing key | We have private key? |
|---|---|---|
| `vbmeta_a/b` (top-level) | `rsa4096_vbmeta` | ✅ `keys/rsa4096_vbmeta.pem` |
| `vbmeta_system_a/b` | `rsa4096_system` | ✅ |
| `vbmeta_vendor_a/b` | `rsa4096_vendor` | ✅ |
| `vbmeta_product_a/b` | `rsa4096_product` | ✅ |
| `boot_a/b` (image + dtbo share it) | `rsa4096_boot` | ✅ |
| `l_modem/l_ldsp/l_gdsp/pm_sys` chain | `rsa4096_modem` | ✅ |
| recovery | `rsa4096_recovery` | ✅ |
| `vbmeta_system_ext_a` | AVB0 present, **no leaked match** | ❌ (but nearly empty) |
| `vbmeta_odm`, `avbmeta_rs`, `common_rs*` | **partitions are all-zero** | n/a — unused |

`keys/` now holds all 7 private PEMs + pub blobs. Consequences:

- We can mint **correctly signed** `boot`, `dtbo`, `vbmeta`, and chained
  `vbmeta_*` images — the exact thing the P20HD port needed the leaked key for.
- Custom images can be flashed **via BROM `spd_dump w` without any bootloader
  unlock** — signature verification passes because signatures are genuine.
- Bootloader unlock is now *optional* (nice for fastboot convenience), not a
  prerequisite. `fastboot flashing unlock` still wipes userdata — avoid unless needed.
- Remaining unknowns: whether SPL/uboot enforce an *additional* Unisoc
  boot-chain signature (SIMGHDR) on `boot`/`splloader` beyond AVB — the `boot`
  pubkey blob inside `boot_a.bin` suggests AVB-footer style; the P20HD notes
  (`docs/UNISOC_AVB_SIGNING.md`) document the full signing recipe to follow.

```sh
adb reboot bootloader   # optional path only
fastboot flashing get_unlock_ability
```

## Phase 2 — Image analysis (dump complete → concrete results)

Dumped and analyzed 2026-11-26 (`brom-dump/` + `extracted-boot/`):

- **`boot_a.bin`** = Android boot header **v2**, page 2048:
  kernel 18.7 MB ARM64 Image (magic `ARMd` @0x38), ramdisk 9.2 MB gzip → 21 MB
  newc cpio (470 entries: `init`, `fstab.s9863a3h10`, `ueventd.s9863a3h10.rc`,
  `init` binary). This is the **generic Unisoc SC9863A BSP kernel** — ramdisk
  ships fstab/ueventd/init.rc variants for *all* SC9863A boards (1c10, 1h10,
  1h20, 2h10, 3c10, **3h10** = ours).
- **Main DTB**: appended inside `boot_a.bin` at offset `0x1a9a040` (87 KB FDTv17)
  → extracted to `extracted-boot/main.dtb` + decompiled `main.dts` (3,576 lines).
  Board DT: `model="Spreadtrum SC9863A SoC"`, `compatible="sprd,sc9863a"` —
  full PMIC (`sc2721_pmic`, `pmic_fchg/fgu/adc/typec`), DSI/DPHY, WCN, audio
  (`sound_vbc_r1p0v3_sprd_codec`), sensors, USB, eMMC nodes all present.
- **`dtb_a/b` and `vendor_boot_a/b` partitions are EMPTY** (all zeros) — stock
  firmware doesn't use them; everything needed is in `boot` + `dtbo`.
- **`uboot_a.bin`** contains its own small DTB @ `0xa3e48` (4.8 KB) → `uboot.dtb`.
- **`dtbo_a.bin`** = 5 board-variant overlays (~30 KB FDT each) → `dtbo_0..4.dts`.
  **`dtbo_4` is our variant** — identified by the only tablet-sized panel
  (135×218 mm):
  - Panels: `lcd_ums512_zjgd_boe_83102h_fhd` / `..._83102e_fhd` — BOE FHD,
    HX83102-class controller, MIPI-DSI 4-lane rgb888, init sequences embedded
    (`sprd,initial-command` 655/675 B), `display-timings` per panel.
  - Touch: `himax_ts@48` (`himax,hxcommon`, irq GPIO 0x90, rst 0x91,
    coords 0x4b0×0x780 = **1200×1920**); alt `gslx680@40`.
  - Wireless: `sprd,sc2355-wifi` + `sprd,marlin3-fm` (SC2355 combo — explains
    `wcnmodem_a/b` partitions).
  - Power: `sprd,marlin3` PMIC, `ti,bq2560x_chg` charger, `simple-battery`,
    `charger-manager`, `gpio-keys`, `generic-adc-thermal`, sensor-main/sub/main2,
    `microarray,afs121` mic array. **No fingerprint, no flash LED** in our variant.
- **`vbmeta_a.bin`** (1 MB) — contains the leaked `rsa4096_vbmeta` pubkey at
  `0x3898` plus all chain pubkeys (see Phase 1 table). Signing solved.

Already pulled earlier: `partitions/{vendor,product,system_ext}.tar.gz` — HAL
blobs, `lib/modules/*.ko` for this exact kernel, `etc/fstab`, firmware.

## Phase 3 — OS image build (decision point after Phase 2)

| Option | Description | Verdict |
|---|---|---|
| **A. Droidian + libhybris** | Debian userland, Android HAL via hybris — touch/Wi-Fi/audio keep working. P20HD precedent. | **Primary candidate.** Best "open source + working device" path. |
| B. phh GSI (Android) | Vanilla AOSP GSI in super partition; needs unlock + `vbmeta --disable-verity --disable-verification`. | Fallback — still Android, smaller gains. Signing keys in hand make this easy too. |
| C. postmarketOS | Mainline-ish Linux | **Rejected** — no HX83102 touch, WCN Wi-Fi, GE8322 GPU, VBC audio drivers. |
| D. DSU trial boot | `adb reboot dsu` with GSI — **no unlock needed**, tests GSI compatibility risk-free | Do this early — cheap signal. |

**Signing pipeline (all pieces now local):**

1. Build boot image (kernel + our `main.dtb`/`dtbo_4` config + ramdisk).
2. `avbtool add_hash_footer` / sign with `rsa4096_boot.pem` → add `--prop` flags.
3. Generate chained `vbmeta_*` images signed with respective leaked PEMs.
4. Top `vbmeta.img` (exactly 1 MiB) signed with `rsa4096_vbmeta.pem`,
   descriptors pointing at our images' hashes — follows
   `P20HD-Droidian/scripts/13_sign_boot_avb.sh` + `docs/UNISOC_AVB_SIGNING.md`.
5. Flash via `spd_dump w` in download mode (no unlock needed) or fastboot if unlocked.

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
| ~~vbmeta key ≠ leaked key~~ | **resolved — all chain keys leaked** | sign images per Phase 3 pipeline |
| SIMGHDR/boot-chain sig beyond AVB | unknown until first flash | P20HD signing docs; test on slot `_b` first |
| `vbmeta_system_ext` key unknown | low | partition nearly empty; likely unused |
| NV/RF partition write accident | low if careful | read-only rule; dump first; never include in flash sets |
| Touch/Wi-Fi dead after port | medium | that's what libhybris/HAL reuse is for; web fallback for deck UI |
| FDL session drops mid-dump | observed once | re-run `adb reboot autodloader` + same command; completed files persist |

## Immediate next actions

1. ✅ Userland snapshot + `/vendor` pull — done.
2. ✅ **BROM partition dump COMPLETE** — 72 partitions, 4.95 GB → `brom-dump/`
   (everything except `userdata`, intentionally skipped). Includes `hypervsior_a/b`.
3. ✅ **Signing keys verified** — all chain keys are public leaks; PEMs in `keys/`.
4. ✅ **Device tree extracted** — `extracted-boot/main.dts` + `dtbo_4.dts` (our
   variant: BOE 83102 FHD, Himax touch, SC2355 Wi-Fi).
5. Package `brom-dump` → GitHub Release (split <2 GB/asset) for off-site backup.
6. Try `adb reboot dsu` GSI trial (phh arm64 AB GSI ~1 GB) — cheap OS-swap signal.
7. Phase 3 build: follow `P20HD-Droidian` scripts — kernel + our dtb + Debian
   rootfs + libhybris → signed boot/vbmeta → flash to slot `_b` first.
