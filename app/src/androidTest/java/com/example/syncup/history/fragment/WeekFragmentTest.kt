package com.example.syncup.history.fragment

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.syncup.R
import com.example.syncup.main.MainPatientActivity
import org.hamcrest.BaseMatcher
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeekFragmentTest{





@Test
fun testValidWeekHealthDataDisplay() {
    ActivityScenario.launch(MainPatientActivity::class.java).use { scenario ->
        onView(withId(R.id.history)).perform(click())
        onView(withText("Week")).perform(click())

        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
            val view = fragment?.view

            view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "82"
            view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "125/83"
            view?.findViewById<TextView>(R.id.textView13)?.text = "80%"
        }

        onView(first(withId(R.id.avg_heartrate))).check(matches(withText("82")))
        onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("125/83")))
        onView(first(withId(R.id.textView13))).check(matches(withText("80%")))
    }
}


    @Test
    fun testInvalidHeartRateNonNumeric_Week() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Week")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val hrView = fragment?.view?.findViewById<TextView>(R.id.avg_heartrate)
                hrView?.text = "abc"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("abc")))
        }
    }

    @Test
    fun testInvalidBloodPressureFormat_Week() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Week")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val bpView = fragment?.view?.findViewById<TextView>(R.id.avg_bloodpressure)
                bpView?.text = "12080"
            }

            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("12080")))
        }
    }


    @Test
    fun testInvalidBatteryWithoutPercent_Week() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Week")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val batteryView = fragment?.view?.findViewById<TextView>(R.id.textView13)
                batteryView?.text = "76"
            }

            onView(first(withId(R.id.textView13))).check(matches(withText("76")))
        }
    }

    @Test
    fun testEmptyWeekDataStateDisplayed() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Week")).perform(click())

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val hr = fragment?.view?.findViewById<TextView>(R.id.avg_heartrate)
                val bp = fragment?.view?.findViewById<TextView>(R.id.avg_bloodpressure)
                val batt = fragment?.view?.findViewById<TextView>(R.id.textView13)

                hr?.text = "-- BPM"
                bp?.text = "--/-- mmHg"
                batt?.text = "--%"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("-- BPM")))
            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("--/-- mmHg")))
            onView(first(withId(R.id.textView13))).check(matches(withText("--%")))
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