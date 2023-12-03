package com.arn.scrobble.utils

import com.arn.scrobble.App
import com.arn.scrobble.R

object AcceptableTags {

    private val tagFragments by lazy {
        App.context.resources
            .openRawResource(R.raw.everynoise_genres)
            .bufferedReader()
            .readLines()
            .toSet()
    }

    fun isAcceptable(lastfmTag: String): Boolean {
        val lastfmTagLower = lastfmTag.lowercase()
        return lastfmTagLower.isNotEmpty() &&
                lastfmTagLower.split(" ", "-").any { it in tagFragments } &&
                lastfmTagLower !in App.prefs.hiddenTags
    }
}

