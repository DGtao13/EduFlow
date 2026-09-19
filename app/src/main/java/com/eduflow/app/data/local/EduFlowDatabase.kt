package com.eduflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Subject::class,
        ScheduleTemplate::class,
        CycleConfiguration::class,
        CycleEntry::class,
        ScheduleSlot::class,
        RecurringPrivateLesson::class,
        RecurringPrivateLessonWeekday::class,
        LessonInstance::class,
        DayException::class,
        TimetableEvent::class,
        Task::class,
        TaskChecklistItem::class,
        TaskReminder::class,
        ImportedTaskShare::class
    ],
    version = EduFlowDatabase.SCHEMA_VERSION,
    exportSchema = true
)
@TypeConverters(EduFlowConverters::class)
abstract class EduFlowDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun scheduleTemplateDao(): ScheduleTemplateDao
    abstract fun cycleDao(): CycleDao
    abstract fun scheduleSlotDao(): ScheduleSlotDao
    abstract fun recurringPrivateLessonDao(): RecurringPrivateLessonDao
    abstract fun recurringPrivateLessonWeekdayDao(): RecurringPrivateLessonWeekdayDao
    abstract fun lessonInstanceDao(): LessonInstanceDao
    abstract fun dayExceptionDao(): DayExceptionDao
    abstract fun timetableEventDao(): TimetableEventDao
    abstract fun taskDao(): TaskDao
    abstract fun taskChecklistDao(): TaskChecklistDao
    abstract fun taskReminderDao(): TaskReminderDao
    abstract fun importedTaskShareDao(): ImportedTaskShareDao

    companion object {
        const val SCHEMA_VERSION = 12
        @Volatile private var instance: EduFlowDatabase? = null

        fun getInstance(context: Context): EduFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EduFlowDatabase::class.java,
                    "eduflow.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12).build().also { instance = it }
            }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `recurring_private_lesson_weekdays` (`recurringPrivateLessonId` INTEGER NOT NULL, `weekday` INTEGER NOT NULL, PRIMARY KEY(`recurringPrivateLessonId`, `weekday`), FOREIGN KEY(`recurringPrivateLessonId`) REFERENCES `recurring_private_lessons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_private_lesson_weekdays_weekday` ON `recurring_private_lesson_weekdays` (`weekday`)")
                // The old scalar remains a compatibility snapshot for older backup/UI paths;
                // membership in this child table is authoritative from v12 onward.
                database.execSQL("INSERT OR IGNORE INTO `recurring_private_lesson_weekdays` (`recurringPrivateLessonId`, `weekday`) SELECT `id`, `weekday` FROM `recurring_private_lessons` WHERE `weekday` BETWEEN 1 AND 7")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `imported_task_shares` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `shareId` TEXT NOT NULL, `taskId` INTEGER NOT NULL, `importedAt` TEXT NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_imported_task_shares_shareId` ON `imported_task_shares` (`shareId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_imported_task_shares_taskId` ON `imported_task_shares` (`taskId`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val slots = database.query("""
                    SELECT s.id, s.scheduleTemplateId, s.weekday, s.lessonIndex, s.subjectId,
                           COALESCE(NULLIF(TRIM(s.teacherOverride), ''), sub.defaultTeacher) AS effectiveTeacher,
                           COALESCE(NULLIF(TRIM(s.roomOverride), ''), sub.defaultRoom) AS effectiveRoom,
                           s.groupInfo, s.logicalBlockId
                    FROM schedule_slots s LEFT JOIN subjects sub ON sub.id = s.subjectId
                    ORDER BY s.scheduleTemplateId, s.weekday, s.lessonIndex, s.id
                """.trimIndent()).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(
                            LegacySchoolBlockSlot(
                                id = cursor.getLong(0), templateId = cursor.getLong(1), weekday = cursor.getInt(2), lessonIndex = cursor.getInt(3),
                                subjectId = cursor.getLong(4).takeIf { !cursor.isNull(4) }, effectiveTeacher = cursor.getString(5), effectiveRoom = cursor.getString(6),
                                groupInfo = cursor.getString(7), logicalBlockId = cursor.getString(8)
                            )
                        )
                    }
                }
                SchoolBlockNormalization.legacyAssignments(slots).forEach { (slotId, blockId) ->
                    database.execSQL("UPDATE schedule_slots SET logicalBlockId = ? WHERE id = ?", arrayOf(blockId, slotId))
                }
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE schedule_slots ADD COLUMN logicalBlockId TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN intendedDueLessonInstanceId INTEGER REFERENCES lesson_instances(id) ON UPDATE NO ACTION ON DELETE SET NULL")
                database.execSQL("CREATE INDEX index_tasks_intendedDueLessonInstanceId ON tasks(intendedDueLessonInstanceId)")
                database.execSQL("UPDATE tasks SET intendedDueLessonInstanceId = dueLessonInstanceId WHERE dueLessonInstanceId IS NOT NULL AND dueLessonInstanceId IN (SELECT id FROM lesson_instances WHERE kind = 'SCHOOL')")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `cycle_configuration` (`id` INTEGER NOT NULL, `anchorMonday` TEXT NOT NULL, `anchorTemplateId` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `cycle_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `position` INTEGER NOT NULL, `templateId` INTEGER NOT NULL, FOREIGN KEY(`templateId`) REFERENCES `schedule_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cycle_entries_position` ON `cycle_entries` (`position`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_cycle_entries_templateId` ON `cycle_entries` (`templateId`)")
                // Keep every historical occurrence if an earlier build allowed duplicate source/date pairs.
                database.execSQL("UPDATE `lesson_instances` SET `sourceScheduleSlotId` = NULL WHERE `sourceScheduleSlotId` IS NOT NULL AND `id` NOT IN (SELECT MIN(`id`) FROM `lesson_instances` WHERE `sourceScheduleSlotId` IS NOT NULL GROUP BY `sourceScheduleSlotId`, `actualDate`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_instances_sourceScheduleSlotId_actualDate` ON `lesson_instances` (`sourceScheduleSlotId`, `actualDate`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `tasks_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `subjectId` INTEGER, `originatingLessonInstanceId` INTEGER, `dueLessonInstanceId` INTEGER, `type` TEXT NOT NULL, `priority` TEXT NOT NULL, `status` TEXT NOT NULL, `dueAt` TEXT, `completedAt` TEXT, `createdAt` TEXT NOT NULL, FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`originatingLessonInstanceId`) REFERENCES `lesson_instances`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`dueLessonInstanceId`) REFERENCES `lesson_instances`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                database.execSQL("INSERT INTO `tasks_new` (`id`, `title`, `description`, `subjectId`, `originatingLessonInstanceId`, `type`, `priority`, `status`, `dueAt`, `completedAt`, `createdAt`) SELECT `id`, `title`, `description`, `subjectId`, `originatingLessonInstanceId`, `type`, `priority`, `status`, `dueAt`, `completedAt`, `createdAt` FROM `tasks`")
                database.execSQL("DROP TABLE `tasks`")
                database.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_subjectId` ON `tasks` (`subjectId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_originatingLessonInstanceId` ON `tasks` (`originatingLessonInstanceId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_dueLessonInstanceId` ON `tasks` (`dueLessonInstanceId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_status` ON `tasks` (`status`)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `task_checklist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, `text` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_checklist_items_taskId` ON `task_checklist_items` (`taskId`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_task_checklist_items_taskId_position` ON `task_checklist_items` (`taskId`, `position`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `recurring_private_lessons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `subjectId` INTEGER, `weekday` INTEGER NOT NULL, `startTime` TEXT NOT NULL, `endTime` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `intervalWeeks` INTEGER NOT NULL, `teacherOverride` TEXT, `roomOverride` TEXT, `label` TEXT, `enabled` INTEGER NOT NULL, FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_private_lessons_subjectId` ON `recurring_private_lessons` (`subjectId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_private_lessons_enabled` ON `recurring_private_lessons` (`enabled`)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `lesson_instances_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `actualDate` TEXT NOT NULL, `actualStartTime` TEXT NOT NULL, `actualEndTime` TEXT NOT NULL, `subjectId` INTEGER, `sourceScheduleSlotId` INTEGER, `sourcePrivateLessonId` INTEGER, `kind` TEXT NOT NULL, `cancellationState` TEXT NOT NULL, `actualTeacher` TEXT, `actualRoom` TEXT, `topic` TEXT, `notes` TEXT, FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`sourceScheduleSlotId`) REFERENCES `schedule_slots`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`sourcePrivateLessonId`) REFERENCES `recurring_private_lessons`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                database.execSQL("INSERT INTO `lesson_instances_new` (`id`, `actualDate`, `actualStartTime`, `actualEndTime`, `subjectId`, `sourceScheduleSlotId`, `kind`, `cancellationState`, `actualTeacher`, `actualRoom`, `topic`, `notes`) SELECT `id`, `actualDate`, `actualStartTime`, `actualEndTime`, `subjectId`, `sourceScheduleSlotId`, `kind`, `cancellationState`, `actualTeacher`, `actualRoom`, `topic`, `notes` FROM `lesson_instances`")
                database.execSQL("DROP TABLE `lesson_instances`")
                database.execSQL("ALTER TABLE `lesson_instances_new` RENAME TO `lesson_instances`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_instances_actualDate` ON `lesson_instances` (`actualDate`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_instances_subjectId` ON `lesson_instances` (`subjectId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_instances_sourceScheduleSlotId` ON `lesson_instances` (`sourceScheduleSlotId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_instances_sourcePrivateLessonId` ON `lesson_instances` (`sourcePrivateLessonId`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_instances_sourceScheduleSlotId_actualDate` ON `lesson_instances` (`sourceScheduleSlotId`, `actualDate`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_instances_sourcePrivateLessonId_actualDate` ON `lesson_instances` (`sourcePrivateLessonId`, `actualDate`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `task_reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `customTriggerAt` TEXT, `enabled` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_reminders_taskId` ON `task_reminders` (`taskId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_reminders_enabled` ON `task_reminders` (`enabled`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `lesson_instances` ADD COLUMN `privateLessonName` TEXT")
                database.execSQL("ALTER TABLE `lesson_instances` ADD COLUMN `privateLocationKind` TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
                database.execSQL("UPDATE `lesson_instances` SET `privateLessonName` = (SELECT `name` FROM `subjects` WHERE `subjects`.`id` = `lesson_instances`.`subjectId`) WHERE `kind` = 'PRIVATE' AND `privateLessonName` IS NULL AND `subjectId` IS NOT NULL")
                database.execSQL("UPDATE `lesson_instances` SET `privateLocationKind` = 'IN_PERSON' WHERE `kind` = 'PRIVATE' AND `actualRoom` IS NOT NULL AND TRIM(`actualRoom`) != ''")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `recurring_private_lessons` ADD COLUMN `privateLocationKind` TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
                database.execSQL("UPDATE `recurring_private_lessons` SET `label` = (SELECT `name` FROM `subjects` WHERE `subjects`.`id` = `recurring_private_lessons`.`subjectId`) WHERE `label` IS NULL AND `subjectId` IS NOT NULL")
                database.execSQL("UPDATE `recurring_private_lessons` SET `privateLocationKind` = 'IN_PERSON' WHERE `roomOverride` IS NOT NULL AND TRIM(`roomOverride`) != ''")
            }
        }
    }
}
