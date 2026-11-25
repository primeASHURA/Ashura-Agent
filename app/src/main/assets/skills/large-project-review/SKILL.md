---
name: large-project-review
description: Review multi-module source repositories with paged listings and bounded source reads.
---

Identify the project entry points and module boundaries from the root settings, manifests or package files. Follow the user's feature through its entry point, implementation and tests. Inspect only relevant generated or dependency files.

If list_files returns next_offset, pass that offset with the same directory to get the next page. If a workspace changes while paging, restart that directory. For large UTF-8 files, request consecutive read_lines windows. Honor has_more and long_lines_clipped. When search_text reaches its scan limit or skips large files, narrow the search to a module and inspect the relevant large files directly.

For each finding, cite the file and line and explain the observed behavior. Report modules inspected, remaining modules and checks requiring a runtime. Keep the original request and outstanding work visible in long reviews.
