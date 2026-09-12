package com.eduflow.app.data

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CsvExport {
    fun field(value: String?): String = "\"" + (value ?: "").replace("\"", "\"\"") + "\""
    fun row(values: List<String?>): String = values.joinToString(",") { field(it) } + "\n"
}
class DataExportService(private val repository: BackupRepository) {
    suspend fun write(output: OutputStream) {
        val backup = repository.snapshot(); val subjects=backup.data.subjects.associateBy{it.id}; val lessons=backup.data.lessons.associateBy{it.id}; val checklist=backup.data.checklist.groupBy{it.taskId}
        ZipOutputStream(output.buffered()).use { zip ->
            fun file(name:String, content:String) { zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            file("subjects.csv", CsvExport.row(listOf("Име","Кратко име","Учител","Стая","Цвят")) + backup.data.subjects.joinToString("") { CsvExport.row(listOf(it.name,it.shortName,it.teacher,it.room,"#%06X".format(it.color and 0xFFFFFF))) })
            file("lessons.csv", CsvExport.row(listOf("Дата","Начало","Край","Име на частен урок","Свързан предмет","Вид","Преподавател","Тип място","Място","Отменен","Какво правихме","Бележки")) + backup.data.lessons.joinToString("") { CsvExport.row(listOf(it.date,it.start,it.end,it.privateName,subjects[it.subjectId]?.name,if(it.kind=="PRIVATE") "private" else "school",it.teacher,it.privateLocationKind,it.room,it.cancellation,it.topic,it.notes)) })
            file("tasks.csv", CsvExport.row(listOf("Заглавие","Описание","Предмет","Тип","Приоритет","Статус","Срок","Завършена","Час на задаване","Час за срок","Стъпки")) + backup.data.tasks.joinToString("") { t -> CsvExport.row(listOf(t.title,t.description,subjects[t.subjectId]?.name,t.type,t.priority,t.status,t.dueAt,t.completedAt,t.originId?.let{lessons[it]?.date},t.dueLessonId?.let{lessons[it]?.date},checklist[t.id]?.joinToString(" | ") { c -> "${if(c.completed) "✓" else "○"} ${c.text}" })) })
            file("private_lessons.csv", CsvExport.row(listOf("Име на частен урок","Свързан предмет","Ден","Начало","Край","Начална дата","Крайна дата","Интервал","Преподавател","Тип място","Място","Активен")) + backup.data.privateLessons.joinToString("") { CsvExport.row(listOf(it.privateName ?: it.label,subjects[it.subjectId]?.name,it.weekday.toString(),it.start,it.end,it.startDate,it.endDate,it.interval.toString(),it.teacher,it.privateLocationKind,it.room,it.enabled.toString())) })
        }
    }
}
