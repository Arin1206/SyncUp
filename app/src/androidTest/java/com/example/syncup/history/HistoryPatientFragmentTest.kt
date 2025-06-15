package com.example.syncup.history

import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.action.ViewActions.click
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import com.example.syncup.R
import com.example.syncup.main.MainPatientActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class HistoryPatientFragmentTest {

    private lateinit var fragment: HistoryPatientFragment

    @Before
    fun setUp() {
        Intents.init()
        fragment = HistoryPatientFragment()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    // === 🧪 UI TEST ===

    @Test
    fun testHistoryTabTitlesDisplayedCorrectly() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            Espresso.onView(withId(R.id.history)).perform(click())
            Espresso.onView(withText("Date")).check(matches(isDisplayed()))
            Espresso.onView(withText("Week")).check(matches(isDisplayed()))
            Espresso.onView(withText("Month")).check(matches(isDisplayed()))
            Espresso.onView(withText("Year")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testBackButtonNavigatesToHome() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            Espresso.onView(withId(R.id.history)).perform(click())
            Espresso.onView(withId(R.id.arrow)).perform(click())
            Espresso.onView(withId(R.id.homepage)).check(matches(isDisplayed()))
        }
    }

    // === 🧪 LOGIC TEST WITH EQUIVALENCE PARTITIONING ===

    @Test
    fun testFormatText_lowercaseWord_returnsCapitalized() {
        val result = invokeFormatText("week")
        assertEquals("Week", result) // Lowercase group
    }

    @Test
    fun testFormatText_emptyString_returnsEmpty() {
        val result = invokeFormatText("")
        assertEquals("", result) // Empty input group
    }

    @Test
    fun testFormatText_capitalizedWord_returnsSame() {
        val result = invokeFormatText("Month")
        assertEquals("Month", result) // Already capitalized group
    }

    @Test
    fun testFormatText_numericString_returnsSame() {
        val result = invokeFormatText("2024")
        assertEquals("2024", result) // Numeric input group
    }

    @Test
    fun testFormatText_symbolicString_returnsSame() {
        val result = invokeFormatText("@tab")
        assertEquals("@tab", result) // Symbolic input group
    }

    // 🔍 Helper for invoking private method using reflection
    private fun invokeFormatText(input: String): String {
        val method = HistoryPatientFragment::class.java.getDeclaredMethod("formatText", String::class.java)
        method.isAccessible = true
        return method.invoke(fragment, input) as String
    }
}
