package com.example.syncup.main

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.syncup.R
import com.example.syncup.home.HomeDoctorFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainDoctorActivityTest{
    @Test
    fun testInitialFragment_ShouldBeHomeDoctorFragment() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            Thread.sleep(500)
            onView(withId(R.id.homepage)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testNavigationToInboxDoctorFragment() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            Thread.sleep(1000)
            onView(withId(R.id.inbox)).perform(click())
            Thread.sleep(500)

            onView(withId(R.id.recyclerView)).check(matches(isDisplayed())) // Asumsikan ada recyclerView di Inbox
        }
    }

    @Test
    fun testScanButtonOpensAlertFragment() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            onView(withId(R.id.scanButton)).perform(click())
            Thread.sleep(500)

            onView(withText("Alert")) // atau komponen spesifik lain di AlertDoctorFragment
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testBackPressedTwice_ExitsApp() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            var activity: MainDoctorActivity? = null

            scenario.onActivity {
                activity = it
                it.replaceFragment(HomeDoctorFragment())
            }

            Thread.sleep(500)

            activity?.runOnUiThread {
                activity?.onBackPressedDispatcher?.onBackPressed() // tekan pertama
            }

            Thread.sleep(1000)

            activity?.runOnUiThread {
                activity?.onBackPressedDispatcher?.onBackPressed() // tekan kedua
            }
        }


    }


}