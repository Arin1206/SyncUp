package com.example.syncup

import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import com.example.syncup.R
import com.example.syncup.register.SignUpDoctorActivity
import com.example.syncup.main.MainDoctorActivity
import com.example.syncup.DoctorLoginFragment
import com.example.syncup.welcome.WelcomeActivity
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matcher

@RunWith(AndroidJUnit4::class)
class DoctorLoginFragmentTest {

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
    fun testGoogleSignIn() {
        // Launch the fragment using ActivityScenario
        ActivityScenario.launch(WelcomeActivity::class.java).use { scenario ->

            // Navigate to the "Doctor" tab
            Espresso.onView(ViewMatchers.withText("Doctor"))
                .perform(ViewActions.click()) // Simulate clicking the "Doctor" tab

            // Simulate clicking the Google Sign-In button
            Espresso.onView(ViewMatchers.withId(R.id.customgoogle))
                .perform(ViewActions.click())

            // Wait for the Google Sign-In intent to launch
            Intents.intended(IntentMatchers.hasComponent("com.google.android.gms.auth.api.signin.internal.SignInHubActivity"))

            // Sleep for a few seconds to let the Google Sign-In process complete
            SystemClock.sleep(3000) // Wait for 3 seconds (adjust the time as needed)

            // Optionally, verify additional actions after Google Sign-In process is completed, if needed
            // For example: checking if the user is navigated to the MainDoctorActivity after successful login
            // Espresso.onView(ViewMatchers.withId(R.id.someViewInMainActivity))
            //     .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

            // No need to finish the test prematurely, so you can end the test here
        }
    }


    @Test
    fun testEmptyPhoneNumber() {
        // Launch the WelcomeActivity containing the ViewPager
        ActivityScenario.launch(WelcomeActivity::class.java).use { scenario ->
            // Navigate to the "Doctor" tab
            Espresso.onView(ViewMatchers.withText("Doctor"))
                .perform(ViewActions.click()) // Simulate clicking the "Doctor" tab

            // Simulate empty phone number input in DoctorLoginFragment
            Espresso.onView(ViewMatchers.withId(R.id.ed_regis_phone))
                .perform(ViewActions.replaceText(""))

            // Simulate clicking the OTP button
            Espresso.onView(ViewMatchers.withId(R.id.customTextView2))
                .perform(ViewActions.click())

            // Verify that an error is shown for the phone number field
            Espresso.onView(ViewMatchers.withId(R.id.ed_regis_phone))
                .check(ViewAssertions.matches(ViewMatchers.hasErrorText("Please enter your phone number")))
        }
    }

    @Test
    fun testPhoneNumberNotRegisteredToast() {
        // Launch the WelcomeActivity containing the ViewPager
        ActivityScenario.launch(WelcomeActivity::class.java).use { scenario ->
            // Navigate to the "Doctor" tab
            Espresso.onView(ViewMatchers.withText("Doctor"))
                .perform(ViewActions.click()) // Simulate clicking the "Doctor" tab

            // Simulate entering a phone number that is not registered
            Espresso.onView(ViewMatchers.withId(R.id.ed_regis_phone))
                .perform(ViewActions.replaceText("0899999999"))  // Example unregistered phone number

            // Simulate clicking the OTP button
            Espresso.onView(ViewMatchers.withId(R.id.customTextView2))
                .perform(ViewActions.click())

        }
    }
    @Test
    fun testSignUpButtonNavigatesToSignUpDoctorActivity() {
        // Launch the WelcomeActivity containing the ViewPager
        ActivityScenario.launch(WelcomeActivity::class.java).use { scenario ->
            // Ensure the ViewPager is showing the "Doctor" tab
            Thread.sleep(1000)
            Espresso.onView(ViewMatchers.withText("Doctor"))
                .check(ViewAssertions.matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))  // Check if visible
                .perform(ViewActions.click()) // Simulate clicking on the "Doctor" tab

            // Now that we are on the DoctorLoginFragment, simulate scrolling to the sign-up button and clicking it
            Espresso.onView(ViewMatchers.withId(R.id.textView4))
                .perform(ViewActions.scrollTo())  // Scroll to the button if it's not visible
                .check(ViewAssertions.matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))  // Check if visible
                .perform(ViewActions.click())  // Perform the click

            // Verify that the SignUpDoctorActivity is launched
            Intents.intended(IntentMatchers.hasComponent(SignUpDoctorActivity::class.java.name))
        }
    }






}

