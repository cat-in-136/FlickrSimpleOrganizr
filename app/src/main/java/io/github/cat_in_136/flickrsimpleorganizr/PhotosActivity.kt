package io.github.cat_in_136.flickrsimpleorganizr

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AlertDialog
import android.util.Log
import com.github.scribejava.core.model.OAuth1AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.suspendCoroutine

class PhotosActivity : AppCompatActivity(), CoroutineScope {
    internal val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var flickrClient : FlickrClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photos)

        intent.getSerializableExtra(EXTRA_FLICKR_ACCESS_TOKEN).let {
            if (it is OAuth1AccessToken) {
                val (apiKey, sharedSecret) = PreferenceManager.getDefaultSharedPreferences(this@PhotosActivity).let {
                    Pair(
                        it.getString(SettingsActivity.AccountPreferenceFragment.KEY_OAUTH_API_KEY, ""),
                        it.getString(SettingsActivity.AccountPreferenceFragment.KEY_OAUTH_SHARED_SECRET, "")
                    )
                }
                flickrClient = FlickrClient(apiKey, sharedSecret, job)
                flickrClient?.accessToken = it
            }
        }

        if (flickrClient != null) {
            launch {
                checkAccessToken().await()
                loadPhotos().await()
            }
        }
    }

    suspend fun checkAccessToken() = async {
        assert(flickrClient?.accessToken != null)
        try {
            val testRequest = OAuthRequest(Verb.GET, "https://api.flickr.com/services/rest/")
            testRequest.addQuerystringParameter("method", "flickr.test.login");
            val (code, headers, body) = flickrClient!!.access(testRequest).await()
        } catch (e : Exception) {
            finishByException(e, "Test Access").await()
        }
    }

    suspend fun loadPhotos() = async {
        assert(flickrClient?.accessToken != null)

        try {
            val request = OAuthRequest(Verb.GET, "https://api.flickr.com/services/rest/")
            request.addQuerystringParameter("method", "flickr.people.getPhotos");
            request.addQuerystringParameter("user_id", "me")
            val (code, headers, body) = flickrClient!!.access(request).await()

            Log.d("Photos", "getPhotos ${body}")
        } catch (e : Exception) {
            finishByException(e, "Load Photos").await()
        }

    }

    private suspend fun finishByException(e : Throwable, msg : String) = async {
        Log.e("Photos", msg, e)

        suspendCoroutine<Nothing?> { continuation ->
            AlertDialog.Builder(this@PhotosActivity)
                    .setMessage("Test Access Error")
                    .setPositiveButton(android.R.string.ok, null)
                    .setOnDismissListener({ continuation.resume(null) })
                    .show()
        }

        this@PhotosActivity.finish()
    }

    companion object {
        val EXTRA_FLICKR_ACCESS_TOKEN = "io.github.cat_in_136.flickrsimpleorganizr.EXTRA_FLICKR_ACCESS_TOKEN"
    }
}
