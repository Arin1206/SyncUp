package com.example.syncup.history

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.syncup.R
import com.example.syncup.main.MainDoctorActivity
import com.google.android.material.tabs.TabLayout
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertDoctorFragmentTest{
    @Test
    fun testAlertFragment_TabsDisplayed() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            Thread.sleep(2000)
            onView(withId(R.id.scanButton)).perform(click())
            Thread.sleep(800)

            onView(withContentDescription("Date")).check(matches(isDisplayed()))
            onView(withContentDescription("Week")).check(matches(isDisplayed()))
            onView(withContentDescription("Month")).check(matches(isDisplayed()))
            onView(withContentDescription("Year")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testAlertFragment_ViewPagerIsVisible() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            onView(withId(R.id.scanButton)).perform(click())
            Thread.sleep(800)

            onView(withId(R.id.viewPager)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
        }
    }

    @Test
    fun testAlertFragment_SwitchTabs() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use {
            onView(withId(R.id.scanButton)).perform(click())
            Thread.sleep(1000) // tunggu TabLayout attach

            onView(withId(R.id.tabLayout)).perform(selectTabAtPosition(1)) // Week
            onView(withId(R.id.tabLayout)).perform(selectTabAtPosition(2)) // Month
            onView(withId(R.id.tabLayout)).perform(selectTabAtPosition(3)) // Year
            onView(withId(R.id.tabLayout)).perform(selectTabAtPosition(0)) // Date
        }
    }


    fun selectTabAtPosition(position: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(TabLayout::class.java)
            override fun getDescription(): String = "Click on tab at index $position"
            override fun perform(uiController: UiController, view: View) {
                val tabLayout = view as TabLayout
                val tab = tabLayout.getTabAt(position)
                tab?.select()
            }
        }
    }







}