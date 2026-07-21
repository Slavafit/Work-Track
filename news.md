# News

## Version 2.5

- Changed object report sharing to attach available work day photos as image files.
- Removed raw photo URI links from object report text.
- Added report text counters for attached and unavailable photos.

## Version 2.4

- Added photo links for work days using persisted device gallery access.
- Added photo availability status and deletion of photo links without deleting device files.
- Included work day photo links in object reports with available/missing status.
- Added editing for existing work entries inside a work day.

## Version 2.3

- Fixed company name input in Settings so typing is no longer overwritten by saved settings updates.

## Version 2.2

- Improved Settings directories block with a book icon and clearer directory buttons.

## Version 2.1

- Simplified bottom navigation by keeping only primary app workflows.
- Moved workers and work types into Settings under Directories.
- Added internal settings navigation with Back support for directory screens.

## Version 2.0

- Added saved proposal storage with Room database migration.
- Added saved proposal list, editing, deleting, saving, and repeat sharing.
- Preserved existing database data with migration from version 1 to 2.

## Version 1.9

- Added Proposal / Presupuesto section.
- Added draft proposals with object selection, service lines, prices, total, and customer sharing.
- Added localized proposal strings in Russian, English, and Spanish.

## Version 1.8

- Added company name setting.
- Added company information to object reports so customers can see who issued the report.

## Version 1.7

- Simplified object reports by hiding per-worker totals when a worker has only one service entry.

## Version 1.6

- Fixed duplicated work day totals when a day has multiple workers.
- Reworked object and work day summary SQL to calculate totals, worker counts, and entry counts independently.

## Version 1.5

- Fixed report generation stability when the app language differs from the system language.
- Made report date and money formatting follow the selected app language.
- Added safe fallback handling so report generation errors do not crash the app.
- Replaced the euro symbol in code with a Unicode escape for safer encoding.

## Version 1.4

- Refined object report formatting by removing customer phone from the header.
- Made worker rows clearer by explicitly labeling each worker.

## Version 1.3

- Expanded object reports with customer phone, day count, day totals, worker totals, service details, and notes.
- Included object days with no services in object reports.

## Version 1.2

- Fixed screen flickering when opening a work day.
- Stabilized work day and object data subscriptions in Compose.

## Version 1.1

- Reworked the work day flow around workers and their services.
- Added per-worker service lists and totals inside each work day.
- Added service creation from a selected worker card.
- Improved worker selection when creating a work day.
- Improved system Back navigation across all main sections.
- Improved object and settings screen layout consistency.
- Fixed customer name autofill when picking a phone contact.

## Version 1.0

- Added license activation and license status screens.
- Added app settings with theme and language selection.
- Added developer contact information with a Telegram link.
- Added app version information.
- Added customer selection from the internal customer list when creating an object.
- Added contact picker support for customer and worker phone fields.
- Added support for phone numbers that start with `+`.
- Improved object, work day, worker, work type, report, and settings screens.
- Improved system Back behavior across nested screens and main sections.
- Improved worker selection when creating a work day with a searchable multi-select list.
- Improved work entry creation with a dropdown work type selector and required cost field.
- Fixed localization and encoding issues across Russian, English, and Spanish resources.
- Fixed launcher app icon setup.
