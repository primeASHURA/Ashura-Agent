---
name: apk-project-review
description: Inspect Android app metadata and UI resources, or review an explicitly selected source decode.
---

Use the method selected for this request. Locate the requested APK in the selected workspace. If several files match, ask which one the user means.

For direct inspection, use apk_manifest for package metadata, apk_resources for layouts and localized strings, and apk_compare for release differences. For a UI recreation, use those observations and the user's requirements to write original source in a separate project folder. Identify which behaviors still need screenshots or a device test.

For source decompilation, use the configured SSH decompiler and its verified output report. Read the generated source in the reported folder. On a partial result, state which requested questions the available source can answer. Preserve the original APK and prior output folders.

In large projects, start with relevant modules and build configuration. Page through list_files using next_offset, search inside the selected module, and use read_lines windows for large source files. Cite the inspected paths and mark any incomplete coverage.
