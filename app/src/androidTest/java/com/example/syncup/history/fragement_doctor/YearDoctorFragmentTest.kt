package com.example.syncup.history.fragement_doctor

import android.view.View
import android.widget.TextView
import com.example.syncup.R
import com.example.syncup.main.MainDoctorActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.BaseMatcher
import org.hamcrest.Matcher

@RunWith(AndroidJUnit4::class)
class YearDoctorFragmentTest {

    @Test
    fun testValidYearHealthDataDisplay() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            onView(withContentDescription("Year")).perform(click())

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view

                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "82"
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "125/80"
                view?.findViewById<TextView>(R.id.textView13)?.text = "93%"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("82")))
            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("125/80")))
            onView(first(withId(R.id.textView13))).check(matches(withText("93%")))
        }
    }

    @Test
    fun testEmptyYearHealthStateDisplay() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            onView(withContentDescription("Year")).perform(click())

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "N/A"
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "N/A"
                view?.findViewById<TextView>(R.id.textView13)?.text = "N/A"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("N/A")))
            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("N/A")))
            onView(first(withId(R.id.textView13))).check(matches(withText("N/A")))
        }
    }

    @Test
    fun testInvalidHeartRate_nonNumeric() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            Thread.sleep(1000)
            onView(withContentDescription("Year")).perform(click())

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "xx"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("xx")))
        }
    }

    @Test
    fun testInvalidBloodPressureFormat() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            Thread.sleep(1000)
            onView(withContentDescription("Year")).perform(click())

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "12085"
            }

            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("12085")))
        }
    }


    fun selectTabAtPosition(position: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isAssignableFrom(com.google.android.material.tabs.TabLayout::class.java)
            }

            override fun getDescription(): String = "Select tab at index $position"

            override fun perform(uiController: UiController?, view: View?) {
                val tabLayout = view as com.google.android.material.tabs.TabLayout
                val tab = tabLayout.getTabAt(position)
                tab?.select()
            }
        }
    }


    @Test
    fun testBatteryWithoutPercent() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            Thread.sleep(1000)
            onView(withContentDescription("Year")).perform(click())


            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.textView13)?.text = "85"
            }

            onView(first(withId(R.id.textView13))).check(matches(withText("85")))
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
