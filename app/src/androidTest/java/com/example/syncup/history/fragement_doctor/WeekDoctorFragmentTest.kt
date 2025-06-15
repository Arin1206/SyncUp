package com.example.syncup.history.fragement_doctor

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
class WeekDoctorFragmentTest {

    @Test
    fun testValidWeekHealthDataDisplay() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            onView(withText("Week")).perform(click())

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view

                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "78"
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "120/80"
                view?.findViewById<TextView>(R.id.textView13)?.text = "87%"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("78")))
            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("120/80")))
            onView(first(withId(R.id.textView13))).check(matches(withText("87%")))
        }
    }

    @Test
    fun testEmptyWeekHealthStateDisplay() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            Thread.sleep(1000) // tunggu TabLayout attach

            onView(withText("Week")).perform(click())

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "-- BPM"
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "--/-- mmHg"
                view?.findViewById<TextView>(R.id.textView13)?.text = "--%"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("-- BPM")))
            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("--/-- mmHg")))
            onView(first(withId(R.id.textView13))).check(matches(withText("--%")))
        }
    }

    @Test
    fun testInvalidHeartRate_nonNumeric() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            Thread.sleep(1000) // tunggu TabLayout attach

            onView(withText("Week")).perform(click())

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "abc"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("abc")))
        }
    }

    @Test
    fun testInvalidBloodPressure_noSlash() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            onView(withText("Week")).perform(click())

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "12080"
            }

            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("12080")))
        }
    }

    @Test
    fun testBatteryLevel_missingPercent() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            Thread.sleep(1000) // tunggu TabLayout attach

            onView(withText("Week")).perform(click())

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.textView13)?.text = "50" // Seharusnya "50%"
            }

            onView(first(withId(R.id.textView13))).check(matches(withText("50")))
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
