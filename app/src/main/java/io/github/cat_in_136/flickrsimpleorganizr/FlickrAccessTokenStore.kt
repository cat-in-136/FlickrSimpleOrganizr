package io.github.cat_in_136.flickrsimpleorganizr

import android.content.Context
import com.github.scribejava.core.model.OAuth1AccessToken

fun storeFlickrAccessToken(context: Context, accessToken: OAuth1AccessToken) {
    context.getSharedPreferences("DataSave", Context.MODE_PRIVATE).edit()
            .putString(PREF_KEY_FLICKR_ACCESS_TOKEN, accessToken.token)
            .putString(PREF_KEY_FLICKR_ACCESS_TOKEN_SECRET, accessToken.tokenSecret)
            .apply()
}

fun clearFlickrAccessToken(context: Context) {
    context.getSharedPreferences("DataSave", Context.MODE_PRIVATE).edit()
            .remove(PREF_KEY_FLICKR_ACCESS_TOKEN)
            .remove(PREF_KEY_FLICKR_ACCESS_TOKEN_SECRET)
            .apply()
}

fun retrieveFlickrAccessToken(context: Context) : OAuth1AccessToken? {
    val (token, tokenSecret) = context.getSharedPreferences("DataSave", Context.MODE_PRIVATE).let {
        Pair(it.getString(PREF_KEY_FLICKR_ACCESS_TOKEN, null),
                it.getString(PREF_KEY_FLICKR_ACCESS_TOKEN_SECRET, null))
    }

    return if (token != null && tokenSecret != null) {
        OAuth1AccessToken(token, tokenSecret)
    } else {
        null
    }
}

private const val PREF_KEY_FLICKR_ACCESS_TOKEN = "accessToken.token"
private const val PREF_KEY_FLICKR_ACCESS_TOKEN_SECRET = "accessToken.tokenSecret"
