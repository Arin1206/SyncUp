package com.example.syncup.faq

import android.view.View
import android.widget.EditText
import com.example.syncup.R
import com.example.syncup.main.MainDoctorActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.BaseMatcher
import org.hamcrest.Matcher
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class FaqDoctorFragmentTest {

    @Test
    fun testValidInput_shouldProceed() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            onView(withId(R.id.faq)).perform(click())
            Thread.sleep(500)

            onView(withId(R.id.ed_regis_fullname)).perform(replaceText("doctor@mail.com"))
            onView(withId(R.id.message_field)).perform(replaceText("This is a test message."))

            onView(withId(R.id.submit_button)).perform(scrollTo(), click())
        }
    }

    @Test
    fun testEmptyEmail_shouldShowToast() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            onView(withId(R.id.faq)).perform(click())

            onView(withId(R.id.submit_button)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

            onView(withId(R.id.ed_regis_fullname)).perform(replaceText(""))
            onView(withId(R.id.message_field)).perform(replaceText("This is a test message."))
            Thread.sleep(500)
            onView(withId(R.id.submit_button)).perform(scrollTo(), click())
        }
    }


    @Test
    fun testEmptyMessage_shouldShowToast() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            onView(withId(R.id.faq)).perform(click())
            Thread.sleep(500)

            onView(withId(R.id.ed_regis_fullname)).perform(replaceText("doctor@mail.com"))
            onView(withId(R.id.message_field)).perform(replaceText(""))

            onView(withId(R.id.submit_button)).perform(scrollTo(), click())
        }
    }

    @Test
    fun testEmptyBothFields_shouldShowToast() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            onView(withId(R.id.faq)).perform(click())
            Thread.sleep(500)

            onView(withId(R.id.ed_regis_fullname)).perform(replaceText(""))
            onView(withId(R.id.message_field)).perform(replaceText(""))

            onView(withId(R.id.submit_button)).perform(scrollTo(), click())
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
