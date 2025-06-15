package com.example.syncup.register

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.intent.matcher.IntentMatchers
import com.example.syncup.R
import com.example.syncup.register.SignUpPatientActivity
import com.example.syncup.welcome.WelcomeActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.syncup.PatientLoginFragmentTest
import com.example.syncup.hasNoErrorText
import org.junit.After

@RunWith(AndroidJUnit4::class)
class SignUpPatientActivityTest {

    @Before
    fun setUp() {
        // Initialize necessary setup before tests
        Intents.init()  // Initialize Intents before using them in the test
    }

    @After
    fun tearDown() {
        // Release Intents after every test to avoid the IllegalStateException
        Intents.release()  // Clean up the Intents state after each test
    }
    @Test
    fun registerWithFirestore_shouldRejectEmptyPhoneNumber() {
        // Launch the activity using ActivityScenario
        ActivityScenario.launch(SignUpPatientActivity::class.java).use { scenario ->

            scenario.onActivity { activity ->
                // Ensure the binding is properly initialized
                assertNotNull(activity.binding)

                // Simulate entering only the Full Name and leaving the Phone Number empty
                activity.binding.edRegisFullname.setText("John Doe")
                activity.binding.edRegisPhone.setText("") // Phone number is empty
                activity.binding.edRegisAge.setText("25") // Age is filled

                // Call the registerWithFirestore method to validate the inputs
                activity.registerWithFirestore()
            }

            // Verify that the Phone Number field shows an error indicating it's required
            Espresso.onView(ViewMatchers.withId(R.id.ed_regis_phone))
                .check(ViewAssertions.matches(ViewMatchers.hasErrorText("Phone Number is required")))
        }
    }

    @Test
    fun registerWithFirestore_shouldRejectEmptyFullName() {
        // Launch the activity using ActivityScenario
        ActivityScenario.launch(SignUpPatientActivity::class.java).use { scenario ->

            scenario.onActivity { activity ->
                // Ensure the binding is properly initialized
                assertNotNull(activity.binding)

                // Simulate entering only the Phone Number and leaving the Full Name empty
                activity.binding.edRegisFullname.setText("")  // Full Name is empty
                activity.binding.edRegisPhone.setText("08988888")  // Phone number is filled
                activity.binding.edRegisAge.setText("25")  // Age is filled

                // Call the registerWithFirestore method to validate the inputs
                activity.registerWithFirestore()
            }

            // Verify that the Full Name field shows an error indicating it's required
            Espresso.onView(ViewMatchers.withId(R.id.ed_regis_fullname))
                .check(ViewAssertions.matches(ViewMatchers.hasErrorText("Full Name is required")))
        }
    }

    @Test
    fun registerWithFirestore_shouldRejectEmptyAgeWhenOtherFieldsAreFilled() {
        // Launch the activity using ActivityScenario
        ActivityScenario.launch(SignUpPatientActivity::class.java).use { scenario ->

            scenario.onActivity { activity ->
                // Ensure the binding is properly initialized
                assertNotNull(activity.binding)

                // Simulate entering Full Name and Phone Number while leaving Age empty
                activity.binding.edRegisFullname.setText("John Doe")  // Full Name is filled
                activity.binding.edRegisPhone.setText("08988888")  // Phone number is filled
                activity.binding.edRegisAge.setText("")  // Age is empty

                // Call the registerWithFirestore method to validate the inputs
                activity.registerWithFirestore()
            }

            // Verify that the Age field shows an error indicating it's required
            Espresso.onView(ViewMatchers.withId(R.id.ed_regis_age))
                .check(ViewAssertions.matches(ViewMatchers.hasErrorText("Age is required")))
        }
    }

    @Test
    fun registerWithFirestore_shouldAcceptValidInputs() {
        // Launch the activity using ActivityScenario
        ActivityScenario.launch(SignUpPatientActivity::class.java).use { scenario ->

            scenario.onActivity { activity ->
                // Ensure the binding is properly initialized
                assertNotNull(activity.binding)

                // Simulate entering all fields correctly
                activity.binding.edRegisFullname.setText("John Doe")
                activity.binding.edRegisPhone.setText("08988888")  // Phone number is valid
                activity.binding.edRegisAge.setText("25")  // Age is valid

                // Call the registerWithFirestore method to validate the inputs
                activity.registerWithFirestore()
            }

            // Verify that no errors are displayed for any field
            Espresso.onView(ViewMatchers.withId(R.id.ed_regis_phone))
                .check(ViewAssertions.matches(hasNoErrorText()))

            Espresso.onView(ViewMatchers.withId(R.id.ed_regis_fullname))
                .check(ViewAssertions.matches(hasNoErrorText()))

            Espresso.onView(ViewMatchers.withId(R.id.ed_regis_age))
                .check(ViewAssertions.matches(hasNoErrorText()))

            // After successful registration, navigate to WelcomeActivity
            Espresso.onView(ViewMatchers.withId(R.id.login)) // This could be your "login" button or the next step to WelcomeActivity
                .perform(ViewActions.click())

            // Now, we can proceed with PatientLoginFragmentTest
            PatientLoginFragmentTest().testPhoneNumberNotRegisteredToast()
        }
    }


    @Test
    fun loginButton_navigatesToWelcomeActivity() {
        // Launch the activity using ActivityScenario
        ActivityScenario.launch(SignUpPatientActivity::class.java).use { scenario ->

            // Simulate clicking the login button (which navigates to WelcomeActivity)
            Espresso.onView(ViewMatchers.withId(R.id.login))
                .perform(ViewActions.click())

            // Verify that the WelcomeActivity is launched
            Intents.intended(IntentMatchers.hasComponent(WelcomeActivity::class.java.name))
        }
    }
}
