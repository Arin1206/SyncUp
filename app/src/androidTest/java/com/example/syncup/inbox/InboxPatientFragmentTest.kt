package com.example.syncup.inbox

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.syncup.R
import com.example.syncup.main.MainPatientActivity
import org.hamcrest.Matcher
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxPatientFragmentTest {

    @Test
    fun testInboxFragment_VisibleElements() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            Thread.sleep(1000)
            onView(withId(R.id.profile)).perform(click()) // navigasi ke ProfileFragment
            Thread.sleep(500)

            onView(withId(R.id.imageView6)).perform(click()) // klik tombol inbox di dalam ProfileFragment
            Thread.sleep(500)

            onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
            onView(withId(R.id.arrow)).check(matches(isDisplayed()))
        }
    }


    @Test
    fun testBackButtonNavigatesToHome() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            Thread.sleep(1000)
            onView(withId(R.id.profile)).perform(click())
            Thread.sleep(500)

            onView(withId(R.id.imageView6)).perform(click())
            Thread.sleep(500)

            onView(withId(R.id.arrow)).perform(click())
            Thread.sleep(300)

            onView(withId(R.id.homepage)) // pastikan ini ID yang unik di HomeFragment
                .check(matches(isDisplayed()))
        }
    }


    @Test
    fun testEmptyNotificationList_HandledGracefully() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {

            Thread.sleep(1000)
            onView(withId(R.id.profile)).perform(click())
            Thread.sleep(500)

            onView(withId(R.id.imageView6)).perform(click())
            Thread.sleep(500)

            onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
        }
    }

    fun waitFor(delay: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription(): String = "Wait for $delay milliseconds"
            override fun perform(uiController: UiController, view: View?) {
                uiController.loopMainThreadForAtLeast(delay)
            }
        }
    }



}
