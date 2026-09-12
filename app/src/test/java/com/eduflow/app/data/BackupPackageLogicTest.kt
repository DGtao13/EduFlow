package com.eduflow.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPackageLogicTest {
    private val full = PortableBackup(
        manifest = BackupManifest(createdAt = "2026-09-12T10:00:00"),
        data = BackupData(
            subjects = listOf(SubjectDto(1, "Математика", null, null, null, 0xFF000000)),
            templates = listOf(TemplateDto(2, "А")),
            cycleConfig = CycleConfigDto(1, "2026-09-07", 2),
            cycleEntries = listOf(CycleEntryDto(3, 0, 2)),
            slots = listOf(SlotDto(4, 2, 1, 1, "08:00", "08:45", 1, null, null, null)),
            privateLessons = emptyList(), lessons = emptyList(), exceptions = emptyList(), events = emptyList(), tasks = emptyList(), checklist = emptyList(), reminders = emptyList()
        ),
        settings = BackupSettings(false, false, 8, 0, "2026-09-15", "2027-05-13")
    )

    @Test fun schoolProgramContainsOnlyReusableSchoolConfiguration() {
        val program = BackupPackageLogic.scoped(full, PackageType.SCHOOL_PROGRAM)
        BackupPackageLogic.validate(program)
        assertEquals(PackageType.SCHOOL_PROGRAM, program.manifest.packageType)
        assertTrue(program.data.tasks.isEmpty())
        assertTrue(program.data.privateLessons.isEmpty())
        assertEquals(1, program.data.slots.size)
    }

    @Test fun studyDataCarriesProgramFingerprintButNoProgramTables() {
        val study = BackupPackageLogic.scoped(full, PackageType.STUDY_DATA)
        BackupPackageLogic.validate(study)
        assertTrue(study.data.templates.isEmpty())
        assertTrue(study.data.slots.isEmpty())
        assertEquals(64, study.manifest.programFingerprint?.length)
    }

    @Test(expected = BackupException::class)
    fun incompatibleStudyDataIsRejectedBeforeImport() {
        val study = BackupPackageLogic.scoped(full, PackageType.STUDY_DATA)
        BackupPackageLogic.validate(study.copy(manifest = study.manifest.copy(programFingerprint = "0".repeat(64))), full)
    }
}
