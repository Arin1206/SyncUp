package com.example.syncup.chat

import android.view.View
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
class ChatFragmentTest {

    @Test
    fun testChatFragment_VisibleComponents() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            // Tambahan waktu agar activity dan komponen UI sepenuhnya siap
            Thread.sleep(1000)

            // Klik navigasi ke fragment Chat
            onView(withId(R.id.chat)).perform(click())
            Thread.sleep(1000) // Tunggu ChatFragment muncul

            // Periksa semua komponen utama terlihat
            onView(withId(R.id.recyclerViewChats)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.search_input)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            onView(withId(R.id.arrow)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }


    @Test
    fun testSearchInput_EmptyQuery() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.chat)).perform(click())
            Thread.sleep(1000)

            onView(withId(R.id.search_input)).perform(clearText(), closeSoftKeyboard())
            Thread.sleep(500)
1
            onView(withId(R.id.recyclerViewChats)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

        }
    }

    @Test
    fun testSearchInput_NonMatchingKeyword() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {
            onView(withId(R.id.chat)).perform(click())
            Thread.sleep(1000)

            onView(withId(R.id.search_input)).perform(typeText("zzzzz"), closeSoftKeyboard())
            Thread.sleep(500)

            onView(withId(R.id.recyclerViewChats)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }

    @Test
    fun testSearchInput_CommonKeyword() {
        ActivityScenario.launch(MainPatientActivity::class.java).use {

            Thread.sleep(2000)
            onView(withId(R.id.chat)).perform(click())
            Thread.sleep(1000)

            onView(withId(R.id.search_input)).perform(typeText("message"), closeSoftKeyboard())
            Thread.sleep(500)

            // Karena Firestore tidak aktif, kamu hanya validasi komponen tetap tampil
            onView(withId(R.id.recyclerViewChats)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }

    // Optional helper jika adapter item banyak
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
