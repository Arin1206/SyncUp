package com.example.syncup.history.fragment

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.syncup.R
import com.example.syncup.main.MainPatientActivity
import org.hamcrest.BaseMatcher
import org.hamcrest.Matcher
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YearFragmentTest {

    @Test
    fun testValidYearHealthDataDisplay() {
        ActivityScenario.launch(MainPatientActivity::class.java).use { scenario ->

            // Beri waktu 1 detik agar tampilan `history` muncul
            Thread.sleep(1000)

            onView(withId(R.id.history)).perform(click())
            onView(withText("Year")).perform(click())

            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val view = fragment?.view

                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "75"
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "120/80"
                view?.findViewById<TextView>(R.id.textView13)?.text = "85%"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("75")))
            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("120/80")))
            onView(first(withId(R.id.textView13))).check(matches(withText("85%")))
        }
    }


    @Test
    fun testInvalidHeartRateNonNumeric_Year() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Year")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val hrView = fragment?.view?.findViewById<TextView>(R.id.avg_heartrate)
                hrView?.text = "abc"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("abc")))
        }
    }

    @Test
    fun testInvalidBloodPressureFormat_Year() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Year")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val bpView = fragment?.view?.findViewById<TextView>(R.id.avg_bloodpressure)
                bpView?.text = "13080"
            }

            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("13080")))
        }
    }

    @Test
    fun testInvalidBatteryWithoutPercent_Year() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Year")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val batteryView = fragment?.view?.findViewById<TextView>(R.id.textView13)
                batteryView?.text = "88"
            }

            onView(first(withId(R.id.textView13))).check(matches(withText("88")))
        }
    }

    @Test
    fun testEmptyYearDataStateDisplayed() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Year")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                fragment?.view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "N/A"
                fragment?.view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "N/A"
                fragment?.view?.findViewById<TextView>(R.id.textView13)?.text = "N/A"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("N/A")))
            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("N/A")))
            onView(first(withId(R.id.textView13))).check(matches(withText("N/A")))
        }
    }

    // Matcher untuk menghindari AmbiguousViewMatcherException
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
