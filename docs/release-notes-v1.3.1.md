# EduFlow 1.3.1

This release improves backup clarity and makes reinstall behavior more predictable.

## Clearer archive and CSV actions

EduFlow now more clearly distinguishes restorable EduFlow archives from CSV exports. CSV files remain intended for viewing, analysis, and external use; they cannot be restored into EduFlow.

## Accurate archive metadata

Newly created EduFlow archives record the current app and database metadata. The portable archive format is unchanged (`backupFormatVersion` remains 2).

## Predictable clean reinstall

Android automatic app-data restore is disabled. After uninstalling and reinstalling, EduFlow starts clean unless you explicitly restore an EduFlow archive.

## Import diagnostics

Internal diagnostics for archive imports were improved to make future compatibility and import failures easier to investigate.
