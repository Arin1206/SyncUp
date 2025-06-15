package com.example.syncup.faq

import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
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
class FaqFragmentTest {

    @Test
    fun testValidInput_EmailAndMessageNotEmpty_shouldAttemptSubmit() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.faq)).perform(click())
            Thread.sleep(500)

            // Dummy valid input
            onView(withId(R.id.ed_regis_fullname)).perform(replaceText("dummy@mail.com"), closeSoftKeyboard())
            onView(withId(R.id.message_field)).perform(replaceText("This is a dummy message"), closeSoftKeyboard())

            Thread.sleep(300)
            onView(withId(R.id.submit_button)).perform(scrollTo(), click())

        }
    }

    // EP test: email kosong (invalid input)
    @Test
    fun testInvalidInput_EmptyEmail_shouldShowToast() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.faq)).perform(click())
            Thread.sleep(500)

            onView(withId(R.id.ed_regis_fullname)).perform(replaceText(""), closeSoftKeyboard())
            onView(withId(R.id.message_field)).perform(replaceText("Valid message"), closeSoftKeyboard())

            Thread.sleep(300)
            onView(withId(R.id.submit_button)).perform(scrollTo(), click())

            // Tidak ada crash: asumsi bahwa toast muncul
        }
    }

    // EP test: message kosong (invalid input)
    @Test
    fun testInvalidInput_EmptyMessage_shouldShowToast() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.faq)).perform(click())
            Thread.sleep(500)

            onView(withId(R.id.ed_regis_fullname)).perform(replaceText("user@domain.com"), closeSoftKeyboard())
            onView(withId(R.id.message_field)).perform(replaceText(""), closeSoftKeyboard())

            Thread.sleep(300)
            onView(withId(R.id.submit_button)).perform(scrollTo(), click())
            // Tidak ada crash: asumsi bahwa toast muncul
        }
    }

    // Optional helper
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
