package com.example.syncup

import android.view.View
import android.widget.EditText
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import androidx.test.espresso.matcher.BoundedMatcher
import org.hamcrest.Matcher

fun hasNoErrorText(): Matcher<View> {
    return object : BoundedMatcher<View, EditText>(EditText::class.java) {
        override fun describeTo(description: Description) {
            description.appendText("has no error text")
        }

        override fun matchesSafely(editText: EditText): Boolean {
            return editText.error == null
        }
    }
}
