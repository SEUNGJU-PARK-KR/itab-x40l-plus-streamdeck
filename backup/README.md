# Device State Snapshot — ITAB X40L Plus

Captured: 2026-11-26 · Android 11 · slot `_a` · verified boot `green` · `oem_unlock_allowed=1`

This directory holds a **userland snapshot** — enough to rebuild the Termux + Alpine
Linux server and the deck-launcher app state without touching the OS. It does **not**
cover bootloader/partition-level recovery; for that see `docs/NATIVE_OS_PLAN.md`
(Phase 0b: BROM full-partition dump).

## Contents

| Path | What | Restore |
|---|---|---|
| `alpine-rootfs.tar.gz` (20 MB) | proot-distro Alpine container rootfs — Alpine 3.24.4 + python3 + bash + service scripts | `proot-distro install alpine`, then extract into `$PREFIX/var/lib/proot-distro/containers/alpine/rootfs` |
| `termux-home.tar.gz` (3 KB) | `$HOME` — start/stop/watchdog scripts, `.termux/`, `.ssh/authorized_keys`, logs | extract into Termux `$HOME` |
| `pkg-list.txt` | `pkg list-installed` (109 packages) | `pkg install` each, or reinstall minimal set |
| `deck-app-data/` | deck-launcher `shared_prefs` XMLs (AgentDeck bridge config, linux server state) | copy back via `run-as com.itab.deck` or just reconfigure in UI |
| `partitions/vendor.tar.gz` | `/vendor` full pull — HAL blobs, kernel modules (`lib/modules`), firmware, fstab, libs (~550 MB raw) | extract with `tar xzf -C <dir>`; member names are `./…` |
| `partitions/product.tar.gz` | `/product` full pull — priv-apps incl. `IncarFota.apk`, overlay, libs | same |
| `partitions/system_ext.tar.gz` | `/system_ext` full pull | same |

All three are **reference material for OS porting** (see `docs/NATIVE_OS_PLAN.md`
Phase 2), not flashable images. `/system` (rootfs, ~1 GB, stock AOSP) intentionally
not pulled — re-pull per-APK via `pm path` if ever needed.

## What's intentionally excluded

- `rootfs/{data,apex,odm,product,system,system_ext,vendor,linkerconfig}` inside the
  Alpine tar — empty proot bind-mount stubs owned by other UIDs, regenerated on start.
- `alpine/shm` — runtime shm, regenerated.
- Termux `$PREFIX` (249 MB) — reinstallable via `pkg`; see `pkg-list.txt`.
- No private keys anywhere: Termux `.ssh` contains only `authorized_keys`;
  `agentdeck_bridge.xml` token is empty.
- Five root-only `build.prop`/`default.prop` files are excluded from the partition
  tars (`/vendor/{build,default}.prop`, `/vendor/odm/etc/build.prop`,
  `/product/build.prop`, `/system_ext/build.prop`). Their `ro.*` values are captured
  in `docs/device/getprop.txt` instead.

## Restore procedure (Linux server)

```sh
# on the tablet, inside Termux
pkg install proot-distro
proot-distro install alpine
# stop services first
sh $HOME/stop-itab.sh
# extract rootfs over the fresh install
cd $PREFIX/var/lib/proot-distro/containers/alpine
rm -rf rootfs && mkdir rootfs
tar xzf /sdcard/alpine-rootfs.tar.gz --strip-components=2 -C rootfs
# restore home scripts
tar xzf /sdcard/termux-home.tar.gz -C $HOME
sh $HOME/start-itab.sh
```

## Not covered — real brick recovery

If bootloader/GPT/super is corrupted, this snapshot cannot help. The recovery path is
the BROM read/write dump described in `docs/NATIVE_OS_PLAN.md` Phase 0b, which requires
SC9863A FDL files and physical button access to the tablet.
