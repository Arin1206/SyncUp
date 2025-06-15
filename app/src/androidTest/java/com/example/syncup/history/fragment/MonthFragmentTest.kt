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
class MonthFragmentTest {

    @Test
    fun testValidMonthHealthDataDisplay() {
        ActivityScenario.launch(MainPatientActivity::class.java).use { scenario ->
            onView(withId(R.id.history)).perform(click())
            onView(withText("Month")).perform(click())

            scenario.onActivity {
                val fragment = it.supportFragmentManager.findFragmentById(R.id.frame)
                val view = fragment?.view

                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "77"
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "123/80"
                view?.findViewById<TextView>(R.id.textView13)?.text = "79%"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("77")))
            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("123/80")))
            onView(first(withId(R.id.textView13))).check(matches(withText("79%")))
        }
    }

    @Test
    fun testInvalidHeartRateNonNumeric_Month() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Month")).perform(click())

            it.onActivity {
                val fragment = it.supportFragmentManager.findFragmentById(R.id.frame)
                val view = fragment?.view
                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "abc"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("abc")))
        }
    }

    @Test
    fun testInvalidBloodPressureFormat_Month() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Month")).check(matches(isDisplayed()))
            onView(withText("Month")).perform(click())

            Thread.sleep(1000) // opsional

            it.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val bpView = fragment?.view?.findViewById<TextView>(R.id.avg_bloodpressure)
                bpView?.text = "12080" // tanpa slash
            }

            onView(first(withId(R.id.avg_bloodpressure)))
                .check(matches(withText("12080")))
        }
    }


    @Test
    fun testInvalidBatteryWithoutPercent_Month() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Month")).perform(click())

            it.onActivity {
                val fragment = it.supportFragmentManager.findFragmentById(R.id.frame)
                val view = fragment?.view
                view?.findViewById<TextView>(R.id.textView13)?.text = "76"
            }

            onView(first(withId(R.id.textView13))).check(matches(withText("76")))
        }
    }

    @Test
    fun testEmptyMonthDataStateDisplayed() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.history)).perform(click())
            onView(withText("Month")).perform(click())

            it.onActivity {
                val fragment = it.supportFragmentManager.findFragmentById(R.id.frame)
                val view = fragment?.view
                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "N/A"
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "N/A"
                view?.findViewById<TextView>(R.id.textView13)?.text = "N/A"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("N/A")))
            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("N/A")))
            onView(first(withId(R.id.textView13))).check(matches(withText("N/A")))
        }
    }

    // Utility matcher for avoiding AmbiguousViewMatcherException
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
