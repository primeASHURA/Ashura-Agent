# Unslop
Design and polish app screens and websites around their actual content and primary task.

Adapted for Ashura from Unslop by Damir Sokolovsky (https://heydamir.design/unslop), licensed under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). This edition adapts the workflow to mobile and workspace tools.

1. Inspect the screen entry point, shared theme, components and assets with search_text and read_lines. Use an attached screenshot to identify concrete issues. Preserve existing interactions and recognizable brand choices.
2. Identify the audience, primary action and one product-specific visual detail. For a tracking dashboard that might be the actual timeline; for a photo editor let the selected image occupy the main surface. Choose a hierarchy based on what the user does most often.
3. Adjust spacing, then color, icons, copy, typography, layout, imagery and motion. Group related controls. Remove filler copy, gratuitous badges and decorative cards. Reuse the project's type scale and one purposeful accent. Reserve stronger contrast for the primary action, selections and errors.
4. For Android, inspect Compose semantics or View labels, system insets, keyboard overlap, 48dp touch targets, long translations and enlarged text. Preserve back navigation. For web, check narrow and wide layouts, reading order, keyboard focus, form labels and reduced motion. Include realistic loading, empty and error states where appropriate.
5. Apply focused edits using edit_file. Read the changed regions back and run check_source_text for explicit source expectations. Where previews are available, compare the same screen and data before and after at two widths. Report visual or interaction checks that still need a device or browser.

For a new interface, map the primary user flow before choosing components. Identify the screen entry points, shared layout and theme, where state lives, and where data is loaded or saved. Follow the existing architecture. Extract a shared component when multiple screens need the same behavior. Keep validation and business rules outside visual components where the project already separates them.

Choose typography, spacing, color and interaction states based on the product and its content. Use real labels and representative data. Make the primary action easy to find and keep secondary actions quieter. Check the main flow and responsive or adaptive layout before reporting completion. If rendering tools are unavailable, describe the checks that were performed in source and the preview or device checks still pending.
