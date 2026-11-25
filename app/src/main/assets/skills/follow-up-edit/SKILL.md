---
name: follow-up-edit
description: Apply a follow-up change to existing source and verify what reached the selected workspace.
---

Use the latest request to identify the previous feature and the detail that needs changing. Find the existing implementation with search_text and inspect its callers with read_lines. If the previous answer named a path, check the current file before relying on it.

Apply the smallest edit that satisfies the new requirement. When the requested change should appear as literal source, use check_source_text on the saved path. For a rename, also inspect references to the old name. If a check fails, read the affected lines and correct the mismatch. Report the changed paths, the checks performed, and any behavior that still requires a device or runtime test.
