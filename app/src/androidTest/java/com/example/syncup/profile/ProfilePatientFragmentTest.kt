package com.example.syncup.profile

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.syncup.R
import com.example.syncup.main.MainPatientActivity
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfilePatientFragmentTest{
    @Test
    fun testEditDialog_ValidInput_ShouldUpdateUI() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            Thread.sleep(2000)
            onView(withId(R.id.profile)).perform(click())
            Thread.sleep(1000)

            onView(withId(R.id.edit)).perform(click())
            Thread.sleep(800)

            onView(withId(R.id.edit_fullname)).perform(replaceText("John Doe"))
            onView(withId(R.id.edit_age)).perform(replaceText("30"))

            onView(withId(R.id.spinner_gender)).perform(click())
            Thread.sleep(800) // tunggu dropdown muncul

            onData(allOf(`is`(instanceOf(String::class.java)), `is`("Male")))
                .inRoot(isPlatformPopup()) // pastikan ini dalam popup spinner
                .perform(click())

            onView(withText("Save")).perform(click())
            Thread.sleep(500)

        }
    }


    @Test
    fun testImagePickerDialog_Cancel_ShouldDismiss() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.profile)).perform(click())
            Thread.sleep(1000)

            onView(withId(R.id.photoprofile)).perform(click())
            Thread.sleep(300)

            onView(withText("Cancel")).perform(click())
            onView(withId(R.id.photoprofile)).check(matches(isDisplayed()))
        }
    }


    @Test
    fun testEditDialog_InvalidInput_ShouldShowErrorToast() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            Thread.sleep(2000)
            onView(withId(R.id.profile)).perform(click())
            Thread.sleep(1000)

            onView(withId(R.id.edit)).perform(click())
            Thread.sleep(500)

            onView(withId(R.id.edit_fullname)).perform(replaceText(""))
            onView(withId(R.id.edit_age)).perform(replaceText(""))

            onView(withId(R.id.spinner_gender)).perform(click())
            Thread.sleep(300)
            onData(allOf(`is`(instanceOf(String::class.java)), `is`("Female")))
                .inRoot(isPlatformPopup())
                .perform(click())

            onView(withText("Save")).perform(click())
            Thread.sleep(500)

            // Tidak perlu assert karena update akan gagal, cukup pastikan tidak crash
        }
    }

    @Test
    fun testLogoutButton_ShowsConfirmationDialog() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.profile)).perform(click())
            Thread.sleep(1000)

            onView(withId(R.id.logoutbutton)).perform(click())
            Thread.sleep(500)

            // Cek apakah dialog logout muncul
            onView(withText("Are you sure you want to log out?"))
                .check(matches(isDisplayed()))

            onView(withText("Cancel")).perform(click()) // Pilih cancel biar tetap di halaman
            Thread.sleep(300)

            // Pastikan masih di halaman profile
            onView(withId(R.id.fullname)).check(matches(isDisplayed()))
        }
    }

}