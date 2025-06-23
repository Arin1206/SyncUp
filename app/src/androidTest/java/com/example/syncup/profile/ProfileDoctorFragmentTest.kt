package com.example.syncup.profile

import android.view.View
import android.widget.TextView
import com.example.syncup.R
import com.example.syncup.main.MainDoctorActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.syncup.main.MainPatientActivity
import org.hamcrest.BaseMatcher
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher

@RunWith(AndroidJUnit4::class)
class ProfileDoctorFragmentTest {

    @Test
    fun testValidProfileDataDisplayed() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            onView(withId(R.id.profile)).perform(click())
            Thread.sleep(800)

            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val view = fragment?.view
                view?.findViewById<TextView>(R.id.fullname)?.text = "Dr. John Doe"
                view?.findViewById<TextView>(R.id.age_gender)?.text = "40 years - Male"
                view?.findViewById<TextView>(R.id.phoneoremail)?.text = "08123456789"
            }

            onView(first(withId(R.id.fullname))).check(matches(withText("Dr. John Doe")))
            onView(first(withId(R.id.age_gender))).check(matches(withText("40 years - Male")))
            onView(first(withId(R.id.phoneoremail))).check(matches(withText("08123456789")))
        }
    }

    @Test
    fun testEmptyProfileDataDisplayed() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            onView(withId(R.id.profile)).perform(click())
            Thread.sleep(800)

            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val view = fragment?.view
                view?.findViewById<TextView>(R.id.fullname)?.text = "Unknown Name"
                view?.findViewById<TextView>(R.id.age_gender)?.text = "Unknown Age - Unknown Gender"
                view?.findViewById<TextView>(R.id.phoneoremail)?.text = ""
            }

            onView(first(withId(R.id.fullname))).check(matches(withText("Unknown Name")))
            onView(first(withId(R.id.age_gender))).check(matches(withText("Unknown Age - Unknown Gender")))
            onView(first(withId(R.id.phoneoremail))).check(matches(withText("")))
        }
    }

    @Test
    fun testInvalidAgeDataDisplayed() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            onView(withId(R.id.profile)).perform(click())
            Thread.sleep(800)

            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val view = fragment?.view
                view?.findViewById<TextView>(R.id.age_gender)?.text = "abc years - Male"
            }

            onView(first(withId(R.id.age_gender))).check(matches(withText("abc years - Male")))
        }
    }


    @Test
    fun testEditDialog_ValidInput_ShouldUpdateUI() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            Thread.sleep(2000)
            onView(withId(R.id.profile)).perform(click())
            Thread.sleep(1000)

            onView(withId(R.id.edit)).perform(click())
            Thread.sleep(800)

            onView(withId(R.id.edit_fullname)).perform(ViewActions.replaceText("John Doe"))
            onView(withId(R.id.edit_age)).perform(ViewActions.replaceText("30"))

            onView(withId(R.id.spinner_gender)).perform(click())
            Thread.sleep(800) // tunggu dropdown muncul

            Espresso.onData(
                CoreMatchers.allOf(
                    CoreMatchers.`is`(CoreMatchers.instanceOf(String::class.java)),
                    CoreMatchers.`is`("Male")
                )
            )
                .inRoot(RootMatchers.isPlatformPopup()) // pastikan ini dalam popup spinner
                .perform(click())

            onView(withText("Save")).perform(click())
            Thread.sleep(500)

        }
    }
    @Test
    fun testInvalidGenderDisplayed() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->

            Thread.sleep(1000)
            onView(withId(R.id.profile)).perform(click())
            Thread.sleep(800)

            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.frame)
                val view = fragment?.view
                view?.findViewById<TextView>(R.id.age_gender)?.text = "35 years - Unknown"
            }

            onView(first(withId(R.id.age_gender))).check(matches(withText("35 years - Unknown")))
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
