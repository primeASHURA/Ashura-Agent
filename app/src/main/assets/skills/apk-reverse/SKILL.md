# APK reverse engineering

Use the built-in `apk_*` tools. They read the container directly; nothing is decompiled and no
external toolchain is needed.

## Workflow

1. `apk_overview` — zip layout, dex list, entry sizes. Confirms the file is an APK and shows where
   the weight sits (dex, native libs, assets).
2. `apk_manifest` — package, version, min/target SDK, permissions, activities, services, receivers,
   providers, application label and class. This is your attack surface map.
3. `apk_dex` — class/method/string counts plus class descriptors. Pass `query` to find methods by
   name (`query: "login"`, `"crypto"`, `"token"`). Repeat per dex when the app is multidex.
4. `apk_strings` — grep printable strings across dex and resources (`query: "https://"`,
   `"api_key"`, `"firebase"`). Skips images and `.so` files.
5. `read_file` on a small entry is not possible for binary entries; use `apk_strings` instead.

## Editing the container

- `apk_entry_write` — replace or add one zip entry from a workspace file, writes a new APK.
- `apk_entry_delete` — remove one entry, writes a new APK.
- Both outputs are unsigned. Re-sign (`apksigner sign`) before installing, otherwise the package
  manager rejects the update.

## Editing smali (dex level)

- `dex_smali` — disassemble one class from a `.dex` or from a dex inside an APK to smali text.
- `smali_assemble` — assemble a workspace folder of `.smali` files into a `.dex`.
- `apk_smali_replace` — assemble a smali folder and replace one dex entry inside an APK, writing a
  new unsigned APK.

The reassembly rule is strict: smali assembles exactly the class set you give it. A dex needs its
**whole** class set to be valid, so an extracted single class cannot replace a multidex app's entry
unless the original classes were all extracted first and kept together. For small dexes (a few
hundred classes) extract, edit, reassemble; for large ones edit at container level instead.

Round trip verified on device: a hand-written `com.test.Hello` smali assembled to a 536-byte dex and
disassembled back with identical instructions.

## Limits that matter

- DEX bytecode is only exposed through smali; there is no raw opcode editor.
- The apk_* read tools never decode bytecode; use `dex_smali` for that.
- `resources.arsc` is binary and not parsed; labels that reference `@string/...` stay as resource ids.
- Signing, v2/v3 signature validation and certificate extraction are not implemented. Output APKs
  must be re-signed before installing.
