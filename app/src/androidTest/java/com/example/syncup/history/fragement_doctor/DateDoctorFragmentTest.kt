package com.example.syncup.history.fragement_doctor

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.syncup.R
import com.example.syncup.main.MainDoctorActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.BaseMatcher
import org.hamcrest.Matcher
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class DateDoctorFragmentTest {

    @Test
    fun testValidHealthDataDisplay_doctor() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            onView(withId(R.id.scanButton)).perform(click())
            onView(withText("Date")).perform(click())

            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val view = fragment?.view

                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "88"
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "120/80"
                view?.findViewById<TextView>(R.id.textView13)?.text = "75%"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("88")))
            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("120/80")))
            onView(first(withId(R.id.textView13))).check(matches(withText("75%")))
        }
    }


    @Test
    fun testInvalidHeartRateHandled_doctor() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            onView(withId(R.id.scanButton)).perform(click())
            onView(withText("Date")).perform(click())

            it.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "error"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("error")))
        }
    }

    @Test
    fun testInvalidBloodPressureFormat_doctor() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            onView(withId(R.id.scanButton)).perform(click())
            onView(withText("Date")).perform(click())

            it.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "12080" // tanpa slash
            }

            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("12080")))
        }
    }

    @Test
    fun testInvalidBatteryTextWithoutPercent_doctor() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            onView(withId(R.id.scanButton)).perform(click())

            onView(withText("Date")).perform(click())

            it.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.textView13)?.text = "82" // tanpa "%"
            }

            onView(first(withId(R.id.textView13))).check(matches(withText("82")))
        }
    }

    @Test
    fun testDateParsingUtilities_doctor() {
        val inputDate = "03 Feb 2025"
        val inputTimestamp = "2025-06-15 17:30:00"

        val inputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputMonthYear = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val outputDateOnly = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val outputTime = SimpleDateFormat("HH:mm", Locale.getDefault())

        val parsedDate = inputFormat.parse(inputDate)
        val parsedTimestamp = timestampFormat.parse(inputTimestamp)

        assert(outputFormat.format(parsedDate!!) == "2025-02-03")
        assert(outputMonthYear.format(parsedDate) == "2025-02")
        assert(outputDateOnly.format(parsedTimestamp!!) == "15 Jun 2025")
        assert(outputTime.format(parsedTimestamp) == "17:30")
    }

    fun first(matcher: Matcher<View>): Matcher<View> {
        return object : BaseMatcher<View>() {
            var isFirst = true
            override fun matches(item: Any?): Boolean {
                if (isFirst && matcher.matches(item)) {
                    isFirst = false
                    return true
                }
                return false
            }

            override fun describeTo(description: org.hamcrest.Description?) {
                description?.appendText("should return first matching item")
            }
        }
    }
}
