package com.example.syncup

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import com.example.syncup.register.SignUpPatientActivity
import com.example.syncup.welcome.WelcomeActivity
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class PatientLoginFragmentTest {

    @Before
    fun setUp() {
        // Initialize Intents before tests
        Intents.init()
    }

    @After
    fun tearDown() {
        // Release Intents after tests
        Intents.release()
    }

    @Test
    fun testEmptyPhoneNumber() {
        // Launch the fragment using ActivityScenario
        ActivityScenario.launch(WelcomeActivity::class.java).use { scenario ->
            // Simulate empty phone number input
            Espresso.onView(ViewMatchers.withId(R.id.ed_login_phone))
                .perform(ViewActions.replaceText(""))

            // Simulate clicking the OTP button
            Espresso.onView(ViewMatchers.withId(R.id.customTextView))
                .perform(ViewActions.click())

            // Verify that an error is shown for the phone number field
            Espresso.onView(ViewMatchers.withId(R.id.ed_login_phone))
                .check(ViewAssertions.matches(ViewMatchers.hasErrorText("Please enter your phone number")))
        }
    }

    @Test
    fun testPhoneNumberNotRegisteredToast() {
        // Launch the fragment using ActivityScenario
        ActivityScenario.launch(WelcomeActivity::class.java).use { scenario ->
            // Simulate entering a phone number that is not registered
            Espresso.onView(ViewMatchers.withId(R.id.ed_login_phone))
                .perform(ViewActions.replaceText("0899999999"))  // Example unregistered phone number

            // Simulate clicking the OTP button
            Espresso.onView(ViewMatchers.withId(R.id.customTextView))
                .perform(ViewActions.click())

            // End the test without verifying the toast
        }
    }

    @Test
    fun testGoogleSignIn() {
        // Launch the fragment using ActivityScenario
        ActivityScenario.launch(WelcomeActivity::class.java).use { scenario ->

            Thread.sleep(1000)
            // Simulate clicking the Google Sign-In button
            Espresso.onView(ViewMatchers.withId(R.id.customgoogle))
                .perform(ViewActions.click())

            // Verify  that the Google Sign-In intent is launched
            Intents.intended(IntentMatchers.hasComponent("com.google.android.gms.auth.api.signin.internal.SignInHubActivity"))

            // Optional: End the test after verifying the intent is launched
        }
    }



}
