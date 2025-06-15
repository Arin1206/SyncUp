package com.example.syncup.inbox

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.main.MainDoctorActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.BaseMatcher
import org.hamcrest.Matcher
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxDoctorFragmentTest {

    @Test
    fun testNotificationDisplayed_whenAvailable() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            // Masuk ke fragment inbox secara langsung
            scenario.onActivity { activity ->
                val fragment = InboxDoctorFragment()
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.frame, fragment)
                    .commitNow()

                // Simulasikan notifikasi tersedia
                val view = fragment.view
                val recyclerView = view?.findViewById<RecyclerView>(R.id.recyclerView)
                val dummyNotification = Notification(
                    title = "Test Title",
                    message = "This is a test message"
                )
                val adapter = NotificationAdapter(mutableListOf(dummyNotification))
                recyclerView?.adapter = adapter
                adapter.notifyDataSetChanged()
            }

            // Cek apakah recyclerView tampil
            onView(first(withId(R.id.recyclerView))).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testNoNotificationDisplayed_whenEmpty() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = InboxDoctorFragment()
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.frame, fragment)
                    .commitNow()

                // Simulasikan tidak ada notifikasi
                val recyclerView = fragment.view?.findViewById<RecyclerView>(R.id.recyclerView)
                val adapter = NotificationAdapter(mutableListOf())
                recyclerView?.adapter = adapter
                adapter.notifyDataSetChanged()
            }

            // Cek tetap tampil meskipun kosong
            onView(first(withId(R.id.recyclerView))).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testArrowBackNavigation_toHomeDoctorFragment() {
        ActivityScenario.launch(MainDoctorActivity::class.java).use { scenario ->
            onView(withId(R.id.profile)).perform(click())
            Thread.sleep(500)

            scenario.onActivity { activity ->
                val fragment = InboxDoctorFragment()
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.frame, fragment)
                    .commitNow()
            }

            // Simulasikan klik tombol arrow
            onView(withId(R.id.arrow)).perform(click())
            Thread.sleep(500)

            // Cek apakah navigasi kembali terjadi (bisa diganti dengan pengecekan ID dari HomeDoctorFragment)
            onView(withId(R.id.tabLayout)).check(matches(isDisplayed())) // tabLayout ada di HomeDoctorFragment
        }
    }

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
