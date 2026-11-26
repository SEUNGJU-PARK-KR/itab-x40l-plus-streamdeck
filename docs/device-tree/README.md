# Extracted Device Trees — ITAB X40L Plus (s9863a3h10)

Decompiled from the BROM partition dump (`brom-dump/`, 2026-11-26).

| File | Source | Contents |
|---|---|---|
| `main.dtb` / `main.dts` | appended inside `boot_a.bin` @ `0x1a9a040` | Board device tree — PMIC (`sc2721`/`marlin3`), DSI/DPHY, WCN, audio, sensors, USB, eMMC. `model="Spreadtrum SC9863A SoC"` |
| `dtbo_0.dts` … `dtbo_4.dts` | `dtbo_a.bin` (5 FDT overlays) | Board-variant configs. **`dtbo_4` is this device**: BOE 83102H/E FHD panel (1200×1920, DSI 4-lane), Himax touch @0x48, SC2355 Wi-Fi, BQ2560x charger |
| `uboot.dtb` | `uboot_a.bin` @ `0xa3e48` | U-Boot SPL chain device tree |

`dtb_a/b` and `vendor_boot_a/b` partitions are empty on stock firmware — the
main DTB lives inside `boot`, panel/touch config inside `dtbo`.

The `.dts` files were produced by a minimal FDT→DTS decompiler (property values
shown as hex blobs where they are binary init-command arrays — e.g.
`sprd,initial-command` holds the MIPI DSI panel init stream).
