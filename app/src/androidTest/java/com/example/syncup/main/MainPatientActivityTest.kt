package com.example.syncup.main

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.syncup.R
import com.example.syncup.home.HomeFragment
import com.example.syncup.register.SignUpPatientActivity
import com.example.syncup.welcome.WelcomeActivity
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainPatientActivityTest {

    @Before
    fun setUp() {
        Intents.init()
        scanResults = mutableListOf()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun testNavigateToHomeFragment() {
        // Launch the MainPatientActivity
        ActivityScenario.launch(MainPatientActivity::class.java).use { scenario ->
            // Simulate clicking the "Home" button (make sure the ID is correct)
            Espresso.onView(ViewMatchers.withId(R.id.homepage)) // Replace with the actual ID of the "Home" button
                .perform(ViewActions.click()) // Perform click action

            // After the click, check if HomeFragment's unique element is displayed
            Espresso.onView(ViewMatchers.withId(R.id.recycler_view_news)) // Replace with a unique ID from HomeFragment
                .check(matches(ViewMatchers.isDisplayed())) // Ensure the element is displayed, indicating HomeFragment is visible
        }
    }

    @Test
    fun testAgeAlertDialog() {
        // Launch the MainPatientActivity
        ActivityScenario.launch(MainPatientActivity::class.java).use { scenario ->

            // Simulate that the age data is missing by mocking the response in the method `checkUserAgeBeforeScan`
            // For this test, we're assuming that the age is not available, so the alert dialog should show up

            // Trigger the scan button click (which will check the age)
            Espresso.onView(ViewMatchers.withId(R.id.scanButtonContainer)) // Replace with the correct ID for the scan button
                .perform(ViewActions.click())

            // Verify that the alert dialog is shown
            Espresso.onView(ViewMatchers.withText("Lengkapi Profil")) // Verify the dialog title
                .check(matches(ViewMatchers.isDisplayed())) // Ensure the alert dialog is displayed

            // Verify that the dialog has the correct message
            Espresso.onView(ViewMatchers.withText("Silakan lengkapi umur Anda di halaman profil sebelum melanjutkan."))
                .check(matches(ViewMatchers.isDisplayed())) // Ensure the message is correct

            // Simulate clicking the "Isi Sekarang" button in the dialog
            Espresso.onView(ViewMatchers.withText("Isi Sekarang"))
                .perform(ViewActions.click()) // This should navigate to the profile fragment

            // Verify that the ProfilePatientFragment is shown
            Espresso.onView(ViewMatchers.withId(R.id.textView22)) // Replace with a unique ID in the ProfilePatientFragment
                .check(matches(ViewMatchers.isDisplayed())) // Ensure that the profile fragment is visible
        }
    }

    @Test
    fun testNavigateToHistoryFragment() {
        // Trigger the navigation to HistoryFragment
        ActivityScenario.launch(MainPatientActivity::class.java).use { scenario ->
            Espresso.onView(withId(R.id.history))
                .perform(ViewActions.click()) // Assuming "history" button ID
            // Verify that HistoryFragment is displayed by checking a unique UI element from HistoryFragment
            Espresso.onView(withId(R.id.tabLayout)) // Replace with actual unique element from HistoryFragment
                .check(matches(isDisplayed())) // Ensure the unique element is displayed
        }
    }

    @Test
    fun testNavigateToFAQFragment() {
        // Trigger the navigation to FAQFragment
        ActivityScenario.launch(MainPatientActivity::class.java).use { scenario ->
            Espresso.onView(withId(R.id.faq))
                .perform(ViewActions.click()) // Assuming "faq" button ID
            // Verify that FAQFragment is displayed by checking a unique UI element from FAQFragment
            Espresso.onView(withId(R.id.ed_regis_fullname)) // Replace with actual unique element from FAQFragment
                .check(matches(isDisplayed())) // Ensure the unique element is displayed
        }
    }

    @Test
    fun testNavigateToProfileFragment() {
        // Trigger the navigation to ProfilePatientFragment
        ActivityScenario.launch(MainPatientActivity::class.java).use { scenario ->
            Espresso.onView(withId(R.id.profile)).perform(ViewActions.click()) // Assuming "profile" button ID
            // Verify that ProfileFragment is displayed by checking a unique UI element from ProfileFragment
            Espresso.onView(withId(R.id.textView22)) // Replace with actual unique element from ProfileFragment
                .check(matches(isDisplayed())) // Ensure the unique element is displayed
        }
    }



    private lateinit var scanResults: MutableList<Map<String, String>>

    private fun updateDeviceList(deviceName: String, deviceAddress: String) {
        val deviceData = mapOf("A" to deviceName, "B" to deviceAddress)
        scanResults.add(deviceData)
    }



    @Test
    fun testValidDeviceData_addedToList() {
        updateDeviceList("Device1", "00:11:22:33:44:55")
        assertEquals(1, scanResults.size)
        assertEquals("Device1", scanResults[0]["A"])
    }

    @Test
    fun testEmptyDeviceName_stillAdded() {
        updateDeviceList("", "11:22:33:44:55:66")
        assertEquals(1, scanResults.size)
        assertEquals("", scanResults[0]["A"])
    }

    @Test
    fun testEmptyDeviceAddress_stillAdded() {
        updateDeviceList("Device2", "")
        assertEquals(1, scanResults.size)
        assertEquals("", scanResults[0]["B"])
    }

    @Test
    fun testEmptyDeviceNameAndAddress_addedAsEmpty() {
        updateDeviceList("", "")
        assertEquals(1, scanResults.size)
        assertEquals("", scanResults[0]["A"])
        assertEquals("", scanResults[0]["B"])
    }
}

