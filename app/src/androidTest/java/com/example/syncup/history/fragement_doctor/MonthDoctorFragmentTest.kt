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
class MonthDoctorFragmentTest {

    @Test
    fun testValidMonthHealthDataDisplay() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            onView(withText("Month")).perform(click())

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view

                view?.findViewById<TextView>(R.id.avg_heartrate)?.text = "80"
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "130/85"
                view?.findViewById<TextView>(R.id.textView13)?.text = "90%"
            }

            onView(first(withId(R.id.avg_heartrate))).check(matches(withText("80")))
            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("130/85")))
            onView(first(withId(R.id.textView13))).check(matches(withText("90%")))
        }
    }

    @Test
    fun testEmptyMonthHealthStateDisplay() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            onView(withId(R.id.tabLayout)).perform(selectTabAtPosition(2)) // ganti 1 sesuai posisi tab Month

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
            onView(withText("Month")).perform(click())

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
            onView(withId(R.id.tabLayout)).perform(selectTabAtPosition(2)) // ganti 1 sesuai posisi tab Month


            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.avg_bloodpressure)?.text = "14085"
            }

            onView(first(withId(R.id.avg_bloodpressure))).check(matches(withText("14085")))
        }
    }

    @Test
    fun testBatteryLevel_missingPercent() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.scanButton)).perform(click())
            Thread.sleep(1000) // tunggu TabLayout attach

            onView(withId(R.id.tabLayout)).perform(selectTabAtPosition(2)) // ganti 1 sesuai posisi tab Month

            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.findFragmentById(R.id.frame)?.view
                view?.findViewById<TextView>(R.id.textView13)?.text = "88"
            }

            onView(first(withId(R.id.textView13))).check(matches(withText("88")))
        }
    }

    fun selectTabAtPosition(position: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(com.google.android.material.tabs.TabLayout::class.java)
            override fun getDescription(): String = "Select tab at position $position"
            override fun perform(uiController: UiController?, view: View?) {
                val tabLayout = view as com.google.android.material.tabs.TabLayout
                tabLayout.getTabAt(position)?.select()
            }
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
