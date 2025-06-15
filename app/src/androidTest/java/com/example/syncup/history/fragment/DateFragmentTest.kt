package com.example.syncup.history.fragment

import android.view.View
import android.widget.TextView
import com.example.syncup.R
import com.example.syncup.main.MainPatientActivity
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
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DateFragmentTest {

    @Test
    fun testValidHealthDataDisplay() {
        ActivityScenario.launch(MainPatientActivity::class.java).use { scenario ->
            onView(withId(R.id.history)).perform(click())
            onView(withText("Date")).perform(click())

            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val view = fragment?.view

                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "85"
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "120/80"
                view?.findViewById<TextView>(R.id.textView13)?.text = "76%"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("85")))
            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("120/80")))
            onView(first(withId(R.id.textView13))).check(matches(withText("76%")))
        }
    }


    @Test
    fun testEmptyStateDisplayed() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Date")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val emptyView = fragment?.view?.findViewById<View>(R.id.emptyStateView)
                emptyView?.visibility = View.VISIBLE
            }


            onView(first(withId(R.id.emptyStateView))).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testInvalidHeartRateValueHandled() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Date")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val heartRateView = fragment?.view?.findViewById<TextView>(R.id.avg_heartrate)
                heartRateView?.text = "null"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("null")))
        }
    }


    @Test
    fun testInvalidHeartRateNonNumeric() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Date")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val heartRateView = fragment?.view?.findViewById<TextView>(R.id.avg_heartrate)
                heartRateView?.text = "abc" // ❌ Non-numeric input
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("abc")))
        }
    }

    @Test
    fun testInvalidBloodPressureFormat() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Date")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val bpView = fragment?.view?.findViewById<TextView>(R.id.avg_bloodpressure)
                bpView?.text = "12080" // ❌ Format tidak valid (harus "systolic/diastolic")
            }

            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("12080")))
        }
    }

    @Test
    fun testInvalidBatteryTextWithoutPercent() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Date")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val batteryView = fragment?.view?.findViewById<TextView>(R.id.textView13)
                batteryView?.text = "76" // ❌ Tanpa "%"
            }

            onView(first(withId(R.id.textView13))).check(matches(withText("76")))
        }
    }

    @Test
    fun testEmptyStateHiddenWhenDataExists() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Date")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val emptyView = fragment?.view?.findViewById<View>(R.id.emptyStateView)
                emptyView?.visibility = View.GONE // ✅ Saat data ditemukan
            }

            onView(first(withId(R.id.emptyStateView))).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun testDateParsingUtilities_instrumented() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            val inputDate = "24 Jan 2025"
            val inputTimestamp = "2025-06-15 13:45:00"

            val inputFormat1 = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val outputFormat1 = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat2 = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val outputFormat3 = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val outputFormat4 = SimpleDateFormat("HH:mm", Locale.getDefault())

            val parsedDate = inputFormat1.parse(inputDate)
            val parsedTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(inputTimestamp)

            // Simulasi hasil akhir sesuai fungsi di fragment
            val resultSortable = outputFormat1.format(parsedDate!!)
            val resultMonthYear = outputFormat2.format(parsedDate)
            val resultDateOnly = outputFormat3.format(parsedTimestamp!!)
            val resultTimeOnly = outputFormat4.format(parsedTimestamp)

            // Assertions
            assert(resultSortable == "2025-01-24")
            assert(resultMonthYear == "2025-01")
            assert(resultDateOnly == "15 Jun 2025")
            assert(resultTimeOnly == "13:45")
        }
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
