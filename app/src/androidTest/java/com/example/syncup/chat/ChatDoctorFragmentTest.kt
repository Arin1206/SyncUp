package com.example.syncup.chat

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.hasChildCount
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.syncup.R
import com.example.syncup.main.MainDoctorActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatDoctorFragmentTest{


    @Test
    fun testSearchQuery_invalid_shouldShowEmptyList() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            Thread.sleep(1000)
            onView(withId(R.id.chat)).perform(click())
            Thread.sleep(1000)

            onView(withId(R.id.search_input)).perform(typeText("zzzzinvalidtext"))
            Thread.sleep(1500)
            onView(withId(R.id.recyclerViewChats)).check(matches(hasChildCount(0)))
        }
    }

    @Test
    fun testSearchQuery_empty_shouldShowAll() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            Thread.sleep(1000)
            onView(withId(R.id.chat)).perform(click())
            Thread.sleep(1000)

            onView(withId(R.id.search_input)).perform(typeText(""))
            Thread.sleep(1500)
            onView(withId(R.id.recyclerViewChats)).check(matches(
                ViewMatchers.withEffectiveVisibility(
                    ViewMatchers.Visibility.VISIBLE
                )
            ))
        }
    }

    @Test
    fun testSearchQuery_specialCharacters_shouldHandleGracefully() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            Thread.sleep(1000)
            onView(withId(R.id.chat)).perform(click())
            Thread.sleep(1000)

            onView(withId(R.id.search_input)).perform(typeText("@#$%!"))
            Thread.sleep(1500)
            onView(withId(R.id.recyclerViewChats)).check(matches(
                ViewMatchers.withEffectiveVisibility(
                    ViewMatchers.Visibility.VISIBLE
                )
            ))
        }
    }

    @Test
    fun testSearchQuery_valid_shouldShowResults() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            Thread.sleep(1000)
            onView(withId(R.id.chat)).perform(click())
            Thread.sleep(1000)
            onView(withId(R.id.search_input)).perform(typeText("follow up"))
            Thread.sleep(1500) // tunggu proses search/filter
            onView(withId(R.id.recyclerViewChats)).check(matches(
                ViewMatchers.withEffectiveVisibility(
                    ViewMatchers.Visibility.VISIBLE
                )
            ))
        }
    }

}