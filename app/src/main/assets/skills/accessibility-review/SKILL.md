---
name: accessibility-review
description: Review a web or Android screen for accessible names, focus, input and text layout.
---

Start from the screen the user identified. Trace its interactive controls, including error, empty and loading states. Inspect accessible names, focus order, keyboard actions, text scaling, and whether state changes are conveyed through text or semantics. For Compose, inspect semantics and contentDescription at the actual clickable parent. For HTML, inspect native elements and associated labels.

Give each finding a source path and line, the affected user action, and the change needed. Apply edits only when requested. Use source checks for literal changes. Record any checks that require a screen reader, a keyboard, or a rendered screen separately.
