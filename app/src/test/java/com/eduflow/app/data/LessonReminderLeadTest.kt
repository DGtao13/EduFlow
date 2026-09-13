package com.eduflow.app.data

import androidx.datastore.preferences.core.*
import org.junit.Assert.*
import org.junit.Test

class LessonReminderLeadTest {
    @Test fun legacySharedLeadInitializesBothCategoriesWithoutEnablingThem() {
        listOf(10,15,30,60).forEach { lead ->
            val settings=NotificationSettingsRepository.decode(mutablePreferencesOf(intPreferencesKey("lesson_lead_minutes") to lead))
            assertEquals(lead,settings.schoolLeadMinutes);assertEquals(lead,settings.privateLeadMinutes)
            assertFalse(settings.schoolEnabled);assertFalse(settings.privateEnabled)
        }
        val settings = NotificationSettingsRepository.decode(mutablePreferencesOf(intPreferencesKey("lesson_lead_minutes") to 30,
            intPreferencesKey("school_lead_minutes_v1") to 25,booleanPreferencesKey("school_enabled") to true))
        assertEquals(25,settings.schoolLeadMinutes);assertEquals(30,settings.privateLeadMinutes);assertTrue(settings.schoolEnabled)
    }
    @Test fun independentCustomValuesRoundTripAndInvalidInputCannotPersist() {
        val settings=NotificationSettings(schoolLeadMinutes=25,privateLeadMinutes=180)
        val prefs=mutablePreferencesOf();NotificationSettingsRepository.encode(prefs,settings)
        assertEquals(settings,NotificationSettingsRepository.decode(prefs))
        assertEquals(25,LessonReminderLead.parse("25",false));assertEquals(90,LessonReminderLead.parse("90",false));assertEquals(180,LessonReminderLead.parse("3",true))
        listOf("0","-1","","abc","9999999999999999999999","1441").forEach { assertNull(LessonReminderLead.parse(it,false)) }
        assertNull(LessonReminderLead.parse("25",true));assertEquals(1440,LessonReminderLead.parse("24",true))
        assertEquals("25 минути",LessonReminderLead.label(25));assertEquals("3 часа",LessonReminderLead.label(180));assertEquals("1 час",LessonReminderLead.label(60))
        assertEquals(listOf(5,10,15,20,30,45,60,120),LessonReminderLead.presets)
        try {NotificationSettingsRepository.encode(prefs,settings.copy(schoolLeadMinutes=0));fail()}catch(_:IllegalArgumentException){}
    }
}
