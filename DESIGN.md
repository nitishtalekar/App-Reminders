# Design notes

**Mode:** Operate (a daily-use tool). Expression lives in details, not decoration.
**Use scene:** glanced at on a phone, often in a dim room or on a lock-screen-adjacent home screen: dark, high contrast, one thing at a time.

## Palette
| Token | Value | Use |
|---|---|---|
| Bg | `#0D0E10` | app background |
| Raised | `#1C1F23` | dialogs, snackbar, disabled button |
| Line | `#2A2D31` | hairlines, outlines |
| Text | `#ECEAE4` | primary (warm off-white) |
| Muted | `#9A9DA3` | secondary (6.9:1 on Bg) |
| Accent | `#F0B24B` | the only accent: action, "active", today |
| Danger | `#FF7A6B` | reserved for overdue and delete |

## Type
Space Grotesk (variable, OFL). Light 44 for the date headline and light numerals for countdowns; SemiBold for titles; Medium caps for section labels. Large jumps between steps on purpose. Widget uses system sans (RemoteViews cannot load custom fonts).

## Shape language
Square = reminder, circle = birthday, bar = today. Flat fills and 1px hairlines; no shadows, no gradients, no card nesting, square controls.

## Signature
The countdown numeral ("3 / days", "0 / today", "2 / days late") is the visual centre of every row and the widget's wide layout.

## Widget
Three intentionally different layouts: Compact (title + status), Wide (big numeral column + title/date), Large (adds date line, age and notes). One item at a time with `2/5` position; "All clear." when empty.
