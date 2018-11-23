package io.github.cat_in_136.flickrsimpleorganizr

import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.EditText
import com.github.scribejava.core.model.OAuth1AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.async
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
        }
    }

    fun onAddTags(view: View) {
        val tags = findViewById<EditText>(R.id.photo_add_tags_text_edit).text.toString()
                .replace("\\n".toRegex(), " ").trim()

        assert(photos != null)

        async {
            val ret = photos!!.map {
                val request = OAuthRequest(Verb.GET, "https://api.flickr.com/services/rest/")
                request.addQuerystringParameter("method", "flickr.photos.addTags");
                request.addQuerystringParameter("photo_id", it["id"])
                request.addQuerystringParameter("tags", tags)
                val (code, headers, body) = flickrClient!!.access(request).await()

                return@map Pair(code, body);
            }

            if (ret!!.any { return@any it.first != 200 }) {
                val errorSummay = photos!!.mapIndexed { index, photo ->
                    if (ret!![index].first != 200) {
                        "${photo["title"]} ${photo["id"]} : NG"
                    } else {
                        null
                    }
                }.filterNotNull().joinToString("\n")

                alert(this@PhotoAddTagsActivity,
                        getResources().getString(R.string.photo_add_tags_failed_msg) + "\n\n" + errorSummay,
                        this@PhotoAddTagsActivity.getString(android.R.string.dialog_alert_title))
            } else {
                alert(this@PhotoAddTagsActivity, R.string.photo_add_tags_success_msg)
                this@PhotoAddTagsActivity.finish()
            }
        }
    }
}
