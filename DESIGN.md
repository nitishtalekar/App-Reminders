# Design notes

**Mode:** Operate (a daily-use tool). Cool, soft, flowing; expression lives in shape and one pattern, not decoration.

## Palette (cool)
| Token | Value | Use |
|---|---|---|
| Bg | `#0A0E14` | app background |
| Surface / Raised | `#111823` / `#182233` | fields, bar, dialogs |
| Line | `#243044` | outlines |
| Text | `#E8EEF6` | primary |
| Muted | `#8FA0B8` | secondary (about 7:1 on Bg) |
| Accent | `#5EC8F2` | ice cyan: reminders, actions |
| Violet | `#B8A2FF` | birthdays only |
| Danger | `#FF7D8E` | overdue / delete only |

## Type
Space Grotesk (variable, OFL) in the app. Widget uses system sans in its heaviest weight (`sans-serif-black`) because RemoteViews cannot load custom fonts.

## Shapes
Rounded squares = reminders, circles = birthdays. Pill buttons, chips, tabs and a floating pill tab bar; circular add button; 20dp soft fields and rows. No hairline lists.

## Pattern
`ui/Pattern.kt`: flat Truchet quarter-arc tiling at ~6% alpha behind the whole app. Widget: concentric arcs (bottom-right) on reminders, overlapping dots (top-right, violet) on birthdays.

## Widget
Compact / Wide / Large layouts, one item at a time. Reminders: slate card, cyan, arcs. Birthdays: violet-tinted card, larger name, dots. Overdue: coral.
