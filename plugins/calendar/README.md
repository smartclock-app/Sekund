# plugins/calendar

The **calendar widget**. It fetches nothing itself: it follows the calendar *sources* picked in its settings
(Google, Trakt, ... contributed by other plugins to `ExtensionPoints.CalendarSources`), merges their events,
and shows them grouped as "This Week", "Next Week", then by month.

## Files

| File | What it does |
|---|---|
| `CalendarPlugin.kt` | Registers the `calendar` widget type. |
| `CalendarSettings.kt` | Settings: sources, max events, title filters, odd/even week labels. Same keys as the legacy importer writes. |
| `CalendarWidget.kt` | Follows each source live (`context.instances.watch`), merges them, regroups every minute, draws one card per group. |
| `MergedEvents.kt` | Combines the sources' states; a failing source adds a warning and keeps the others. |
| `EventGrouping.kt` | Pure logic: drop finished/hidden events, sort, cap, group under headings. |
| `WeekParity.kt` | Monday-of-week and odd/even weeks (counted from Monday 1 January 2024 = even). |
| `TitleFilter.kt` | One "hide events matching" entry: exact title, or regular expression search. |
| `EventTimeText.kt` | The "when" text: `Today (14:00 - 15:30)`, `Friday 20th — Sunday 22nd`, ... |
| `EventRow.kt` | Draws one event: colour bar, title lines, when. |

## Changes from the old app

- Next week is found by comparing Mondays, not ISO week numbers, so it works across new year.
- The odd/even week label is actually shown, and worked out per week rather than per event.
- A multi-day all-day event ending on the 1st no longer shows the next month's name.
- Title filters apply to every source, not just Google, and also match exact titles containing regex characters.
- Times follow the 12/24 hour preference.
- A broken or missing source shows a warning card; the other sources' events still show.
