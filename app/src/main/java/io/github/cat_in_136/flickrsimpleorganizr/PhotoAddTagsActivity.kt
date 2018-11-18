package io.github.cat_in_136.flickrsimpleorganizr

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.util.Log
import com.github.scribejava.core.model.OAuth1AccessToken
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.Main
import kotlin.coroutines.experimental.CoroutineContext

class PhotoAddTagsActivity : AppCompatActivity(), CoroutineScope {
    internal val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var flickrClient : FlickrClient? = null

    private var photos : Array<HashMap<String, String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_add_tags)

        intent.getSerializableExtra(PhotosActivity.EXTRA_FLICKR_ACCESS_TOKEN).let {
            if (it is OAuth1AccessToken) {
                val (apiKey, sharedSecret) = PreferenceManager.getDefaultSharedPreferences(this@PhotoAddTagsActivity).let {
                    Pair(
                            it.getString(SettingsActivity.AccountPreferenceFragment.KEY_OAUTH_API_KEY, ""),
                            it.getString(SettingsActivity.AccountPreferenceFragment.KEY_OAUTH_SHARED_SECRET, "")
                    )
                }
                flickrClient = FlickrClient(apiKey, sharedSecret, job)
                flickrClient?.accessToken = it
            }
        }
        intent.getSerializableExtra(PhotosActivity.EXTRA_FLICKR_PHOTOS).let {
            photos = it as Array<HashMap<String, String>>
            Log.d("PhotoAddTagsActivity", "${photos}")
        }
    }

    fun onAddTags(view: View) {
        Log.d("onAddTags", "${photos}")
        // TODO Impl
    }
}
