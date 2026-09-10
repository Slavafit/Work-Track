# News

## Version 2.18

- Added customer payment history per object with date, amount, notes, editing and confirmed deletion.
- Separated recorded work and materials, received payments, amount due and advance/overpayment in the object screen and shared object report.
- Kept proposals out of the balance and allowed settlement after work completion.
- Added database migration 7 to 8 without changing existing amounts.
- Included payments in archive format 3 and kept imports from formats 1 and 2 compatible.
- Added tests for partial payment, overpayment, editing, cross-object protection, overflow, migration and archive restore.

## Version 2.17

- Added euro-cent input with either comma or period and up to two decimal places.
- Stored money as integer cents and formatted screens, proposals and reports with two decimals.
- Added database migration 6 to 7 that converts whole euros exactly once, with overflow checks before changing data.
- Preserved existing draft text in euros and converted stored cents back to euros when editing.
- Updated archives to version 2 with explicit EUR_CENT units; version 1 archives remain importable with checked conversion.
- Added tests for decimal precision, formatting, Room migration, rollback and legacy archive conversion.

## Version 2.16

- Added automatic local recovery of the proposal editor, including incomplete service and material lines, without requiring Activity saved state.
- Added a confirmed discard-draft action; a discarded draft cannot return from stale Activity state.
- Preserved the saved proposal ID after saving and retained edits after a failed save.
- Excluded local editor recovery from Android backup/transfer and added a recovery error message for corrupt draft data.
- Added regression tests for on-disk input, fresh editor recovery, discard, save failure, deletion, and corrupt recovery data.

## Version 2.15

- Added manual ZIP backup and restore in Settings using the Android document picker.
- Included all saved work records and available photo files, with a count of missing photos.
- Added archive validation and a preview before confirming replacement of current work data.
- Restored database records in one transaction and copied photos into private app storage for sharing through FileProvider.
- Kept settings, company name, and license unchanged during manual restore; excluded device-bound license state from Android backup/transfer.
- Added regression tests for archive round trips, rollback, missing photos, invalid data, checksums, paths, and size limits.

## Version 2.14

- Preserved hidden services in existing proposals and required all lines to be valid before saving or sharing.
- Moved the proposal editor out of the main activity; retained drafts across tabs and Android saved-state recreation.
- Loaded proposal services and materials together and prevented stale loads and duplicate saves.
- Added confirmation before deleting proposals or discarding a draft; retained draft input after save errors.
- Rejected invalid whole-euro input and overflowing totals without silently altering the entered amount.
- Enforced completed-object read-only access in both the UI and transactional repository operations.
- Stopped object creation from overwriting a shared customer record.
- Made manual license checks bypass the cache and removed license response bodies from logs.
- Added regression tests for money, proposals, repository integrity, saved state, and license cache behavior.

## Version 2.13

- Added priced materials directly to each worker inside a work day, alongside services.
- Added creation, editing, and deletion of worker material entries.
- Included worker materials in worker, day, object, and report totals.

## Version 2.12

- Redesigned the directory shortcuts in Settings as cleaner icon cards.
- Added materials with prices to proposals alongside services.
- Included materials in saved proposals, shared proposal text, item counts, and the combined total.

## Version 2.11

- Added inline creation of new services while selecting a service in work entries and proposals.
- Added a materials directory with creation, editing, activation, and hiding.
- Added soft shadows to cards and key buttons, plus smooth card content animations.

## Version 2.10

- Redesigned object cards with clearer address and customer grouping, a status badge, and separate total and work-day statistics.
- Allowed work entries and proposal items with a zero amount.

## Version 2.9

- Restored object reports to the previous text sharing style instead of PDF.
- Removed the duplicate object total line from the report header.
- Removed per-worker totals from object reports while keeping day totals.

## Version 2.8

- Fixed expired licenses being treated as connection errors.
- Added explicit expired license and expired trial states in the license gate and Settings.
- Made remaining days display as 0 for expired license states.

## Version 2.7

- Changed object report sharing to a single PDF file so photos appear after the report text.
- Added secure FileProvider sharing for generated report PDFs.

## Version 2.6

- Moved object report photo summary to the end of the report text.

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
