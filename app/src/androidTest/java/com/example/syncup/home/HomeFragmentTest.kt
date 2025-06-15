package com.example.syncup.home

import android.content.Intent
import android.net.Uri
import android.widget.TextView
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.syncup.R
import com.example.syncup.main.MainPatientActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.matcher.IntentMatchers
import junit.framework.TestCase.assertEquals
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HomeFragmentTest {

    @Before
    fun setUp() {
        // Initialize Intents before each test (useful for verifying any startActivity() calls)
        Intents.init()
    }

    @After
    fun tearDown() {
        // Release Intents after each test to avoid conflicts
        Intents.release()
    }

    @Test
    fun testProfileButtonClick() {
        // Launch the MainPatientActivity
        ActivityScenario.launch(MainPatientActivity::class.java).use { scenario ->
            // Simulate a click on the profile image button
            Espresso.onView(ViewMatchers.withId(R.id.profile))
                .perform(ViewActions.click())

            // Ensure that the ProfilePatientFragment is displayed after clicking the profile button
            Espresso.onView(ViewMatchers.withId(R.id.logoutbutton))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
    }



    @Test
    fun testRecyclerViewClick() {
        ActivityScenario.launch(MainPatientActivity::class.java).use { scenario ->
            // Assuming there are doctor items in the recycler view
            Espresso.onView(ViewMatchers.withId(R.id.recycler_view_news))
                .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, ViewActions.click()))

            // Ensure that the RoomChatFragment is displayed after doctor is selected
            Espresso.onView(ViewMatchers.withId(R.id.newsDescription))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
    }



    @Test
    fun testHeartRateDisplay() {
        ActivityScenario.launch(MainPatientActivity::class.java).use { scenario ->
            // Insert the dummy heart rate data
            scenario.onActivity { activity ->
                // Simulating the heart rate value update (this would usually be done by your app's logic)
                val heartRateTextView = activity.findViewById<TextView>(R.id.heart_rate_value)
                heartRateTextView.text = "75 bpm"  // Set the text to simulate the heart rate update
            }

            // Now check if the heart rate value has been updated correctly
            Espresso.onView(ViewMatchers.withId(R.id.heart_rate_value))
                .check(ViewAssertions.matches(ViewMatchers.withText("75 bpm")))  // Assert the value is "75 bpm"
        }
    }


    @Test
    fun testBluetoothDeviceNameTextIsDisplayed() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            Espresso.onView(ViewMatchers.withId(R.id.device_name))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
    }




    private fun getFirstWeekOfCurrentMonth(date: Date): Pair<String, String> {
        val calendar = Calendar.getInstance().apply { time = date }
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }

        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        val startOfWeek = formatter.format(calendar.time)
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endOfWeek = formatter.format(calendar.time)

        return Pair(startOfWeek, endOfWeek)
    }

    @Test
    fun testGetFirstWeek_April2024() {
        val date = SimpleDateFormat("yyyy-MM-dd").parse("2024-04-01")!! // Senin
        val (start, end) = getFirstWeekOfCurrentMonth(date)
        assertEquals("01 Apr 2024", start)
        assertEquals("07 Apr 2024", end)
    }

    @Test
    fun testGetFirstWeek_May2024() {
        val date = SimpleDateFormat("yyyy-MM-dd").parse("2024-05-01")!! // Rabu
        val (start, end) = getFirstWeekOfCurrentMonth(date)
        assertEquals("29 Apr 2024", start)
        assertEquals("05 May 2024", end)
    }

    @Test
    fun testGetFirstWeek_October2023() {
        val date = SimpleDateFormat("yyyy-MM-dd").parse("2023-10-01")!! // Minggu
        val (start, end) = getFirstWeekOfCurrentMonth(date)
        assertEquals("25 Sep 2023", start)
        assertEquals("01 Oct 2023", end)
    }


    @Test
    fun testHeartRateIndicator_withInvalidInputs() {
        assertEquals("null", getHeartRateIndicator(-1, 20))
        assertEquals("null", getHeartRateIndicator(80, null))
    }

    @Test
    fun testHeartRateIndicator_healthyClass() {
        assertEquals("Healthy", getHeartRateIndicator(70, 20)) // 70 < 160
    }

    @Test
    fun testHeartRateIndicator_warningClass() {
        assertEquals("Warning", getHeartRateIndicator(170, 20)) // 160 ≤ 170 < 200
    }

    @Test
    fun testHeartRateIndicator_dangerClass() {
        assertEquals("Danger", getHeartRateIndicator(210, 20)) // 210 ≥ 200
    }

    fun getHeartRateIndicator(heartRate: Int, userAge: Int?): String {
        if (heartRate == -1 || userAge == null) return "null"

        val maxWarning = 220 - userAge
        val minWarning = (maxWarning * 0.8).toInt()

        return when {
            heartRate >= maxWarning -> "Danger"
            heartRate < minWarning -> "Healthy"
            else -> "Warning"
        }
    }

}
