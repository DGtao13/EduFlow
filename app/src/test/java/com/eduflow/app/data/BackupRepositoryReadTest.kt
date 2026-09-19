package com.eduflow.app.data

import androidx.room.Room
import com.eduflow.app.data.local.EduFlowDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class BackupRepositoryReadTest {
    private lateinit var context: android.content.Context
    private lateinit var database: EduFlowDatabase
    private lateinit var repository: BackupRepository
    private val json = Json { encodeDefaults = true }

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, EduFlowDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = BackupRepository(context, database)
    }

    @After fun tearDown() = database.close()

    @Test fun validSchoolProgramRetainsItsPackageType() {
        val source = program()
        val decoded = repository.read(ByteArrayInputStream(archive(source)))
        assertEquals(PackageType.SCHOOL_PROGRAM, decoded.manifest.packageType)
        assertEquals(source.manifest.programFingerprint, decoded.manifest.programFingerprint)
    }

    @Test fun snapshotUsesActualAppAndRoomMetadata() = runBlocking {
        val snapshot = repository.snapshot()
        assertEquals(2, snapshot.manifest.backupFormatVersion)
        assertEquals(EduFlowDatabase.SCHEMA_VERSION, snapshot.manifest.roomVersion)
        assertEquals(context.packageManager.getPackageInfo(context.packageName, 0).versionName, snapshot.manifest.appVersion)
        assertEquals(PackageType.FULL_ARCHIVE, snapshot.manifest.packageType)
    }

    @Test fun unknownZipEntryHasDiagnosticCategory() {
        val files = archiveFiles(program()).toMutableMap().apply { put("unexpected.json", "{}") }
        assertFailure(BackupFailure.UNEXPECTED_ENTRY) { repository.read(ByteArrayInputStream(archive(files))) }
    }

    @Test fun missingRequiredZipEntryHasDiagnosticCategory() {
        val files = archiveFiles(program()).toMutableMap().apply { remove("settings.json") }
        assertFailure(BackupFailure.REQUIRED_ENTRY_MISSING) { repository.read(ByteArrayInputStream(archive(files))) }
    }

    @Test fun malformedManifestHasDiagnosticCategory() {
        val files = archiveFiles(program()).toMutableMap().apply { put("manifest.json", "{") }
        assertFailure(BackupFailure.MANIFEST_DECODE_FAILED) { repository.read(ByteArrayInputStream(archive(files))) }
    }

    @Test fun fingerprintMismatchHasDiagnosticCategory() {
        val source = program().copy(manifest = program().manifest.copy(programFingerprint = "0".repeat(64)))
        assertFailure(BackupFailure.FINGERPRINT_MISMATCH) { repository.read(ByteArrayInputStream(archive(source))) }
    }

    private fun program(): PortableBackup {
        val full = PortableBackup(
            manifest = BackupManifest(createdAt = "2026-09-18T09:26:49.720"),
            data = BackupData(
                subjects = listOf(SubjectDto(26, "Мат (ФУЧ)", "Мат (ФУЧ)", "Учител", "31", 4285143962)),
                templates = listOf(TemplateDto(9, "Седмица А")),
                cycleConfig = CycleConfigDto(1, "2026-09-21", 9),
                cycleEntries = listOf(CycleEntryDto(3, 0, 9)),
                slots = listOf(SlotDto(73, 9, 3, 7, "13:30", "14:15", 26, null, null, null, "fuch-math-9-3-7")),
                privateLessons = emptyList(), lessons = emptyList(), exceptions = emptyList(), events = emptyList(),
                tasks = emptyList(), checklist = emptyList(), reminders = emptyList()
            ),
            settings = BackupSettings(false, false, 8, 0, "2026-09-15", "2027-05-13")
        )
        return BackupPackageLogic.scoped(full, PackageType.SCHOOL_PROGRAM)
    }

    private fun archiveFiles(backup: PortableBackup) = mapOf(
        "manifest.json" to json.encodeToString(BackupManifest.serializer(), backup.manifest),
        "data.json" to json.encodeToString(BackupData.serializer(), backup.data),
        "settings.json" to json.encodeToString(BackupSettings.serializer(), backup.settings)
    )

    private fun archive(backup: PortableBackup) = archive(archiveFiles(backup))
    private fun archive(files: Map<String, String>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip -> files.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
        } }
        output.toByteArray()
    }

    private fun assertFailure(expected: BackupFailure, block: () -> Unit) {
        try { block() } catch (error: BackupException) { assertEquals(expected, error.failure); return }
        throw AssertionError("Expected BackupException($expected)")
    }
}
