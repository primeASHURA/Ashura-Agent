# REA on Android — reverse engineering with mobile tooling

The upstream REA workflow assumes Ghidra and desktop binaries. On Android the equivalents are JADX,
apktool, smali/baksmali, aapt2 and apksigner, driven from Termux over SSH. Keep the same discipline:
identify, map, observe, then change the smallest thing.

## Environment

- Termux on the same device, sshd running on port 8022 (`sshd` in Termux, `passwd` set).
- Settings → SSH stores host/user/password; the agent uses `ssh_exec`, `ssh_tools`, `ssh_install`,
  `ssh_decompile`.
- Required tools: `openjdk-17`, `apktool`, `jadx`, `aapt2` (build-tools), `apksigner`.
- Workspace must live on shared storage (`/sdcard/...`) so Termux can read and write it directly.
  The app derives the path from the SAF tree and decompiles into the same workspace folder.

## Flow

1. `apk_overview` + `apk_manifest` locally: package, versions, components, dex list.
2. `ssh_tools` — if tools are missing, `ssh_install` (default packages) then re-check.
3. `ssh_decompile apk:<name>.apk mode:jadx` → Java view in `<name>_jadx/` inside the workspace.
   Use `mode:apktool` when you need `AndroidManifest.xml`, `res/` or smali.
4. Grep the decompiled tree with `search_text` for the behaviour: endpoints, checks, flags.
5. Patch: smali edit + `apk_smali_replace`, or resource/HTML edit + `apk_entry_write`.
6. Sign: `zipalign -p 4` then `apksigner sign` (Termux or PC), install, verify.

## Rules

- Read the decompiled code before patching; never guess a method body.
- One change at a time, rebuild, test; keep a diff of every smali edit.
- Record package, version, exact method and line in notes/findings.md.
- Server-verified checks cannot be patched away; say so instead of shipping a dead patch.

## Defence line

Client checks are advisory. Integrity attestation, server-side entitlement and signature
verification are the only things that survive a decompiler.
