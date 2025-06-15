package com.example.syncup.home

import android.view.View
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

@RunWith(AndroidJUnit4::class)
class HomeDoctorFragmentTest {

    @Test
    fun testValidSummaryDisplay_today() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view

                view?.findViewById<TextView>(R.id.heart_rate_value)?.text = "75"
                view?.findViewById<TextView>(R.id.bp_value)?.text = "120/80"
                view?.findViewById<TextView>(R.id.battery_value)?.text = "85%"
                view?.findViewById<TextView>(R.id.indicator_value)?.text = "Health"
            }

            onView(first(withId(R.id.heart_rate_value))).check(matches(withText("75")))
            onView(first(withId(R.id.bp_value))).check(matches(withText("120/80")))
            onView(first(withId(R.id.battery_value))).check(matches(withText("85%")))
            onView(first(withId(R.id.indicator_value))).check(matches(withText("Health")))
        }
    }

    @Test
    fun testEmptySummaryDisplay_today() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view

                view?.findViewById<TextView>(R.id.heart_rate_value)?.text = "null"
                view?.findViewById<TextView>(R.id.bp_value)?.text = "null"
                view?.findViewById<TextView>(R.id.battery_value)?.text = "null"
                view?.findViewById<TextView>(R.id.indicator_value)?.text = "null"
            }

            onView(first(withId(R.id.heart_rate_value))).check(matches(withText("null")))
            onView(first(withId(R.id.bp_value))).check(matches(withText("null")))
            onView(first(withId(R.id.battery_value))).check(matches(withText("null")))
            onView(first(withId(R.id.indicator_value))).check(matches(withText("null")))
        }
    }

    @Test
    fun testInvalidHeartRate_nonNumeric() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.heart_rate_value)?.text = "xx"
            }

            onView(first(withId(R.id.heart_rate_value))).check(matches(withText("xx")))
        }
    }

    @Test
    fun testInvalidBloodPressureFormat() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.bp_value)?.text = "12080"
            }

            onView(first(withId(R.id.bp_value))).check(matches(withText("12080")))
        }
    }

    @Test
    fun testBatteryWithoutPercent() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->


            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.battery_value)?.text = "90"
            }

            onView(first(withId(R.id.battery_value))).check(matches(withText("90")))
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
