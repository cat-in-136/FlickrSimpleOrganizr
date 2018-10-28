package io.github.cat_in_136.flickrsimpleorganizr

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.scribejava.core.model.OAuth1AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
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

        findViewById<RecyclerView>(R.id.photo_recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(this@PhotosActivity, 5)
            adapter = PhotoCyclerViewAdapter(arrayOf())
        }

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

            XmlPullParserFactory.newInstance().newPullParser().let {
                it.setInput(body.reader())
                try {
                    val data = mutableListOf<HashMap<String, String>>()

                    var eventType = it.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            if (it.name == "photo") {
                                data += HashMap<String, String>().apply {
                                    for (i in 0 .. (it.attributeCount - 1)) {
                                        this[it.getAttributeName(i)] = it.getAttributeValue(i)
                                    }
                                }
                            }
                        }
                        eventType = it.next()
                    }

                    findViewById<RecyclerView>(R.id.photo_recycler_view).apply {
                        adapter = PhotoCyclerViewAdapter(data.toTypedArray())
                    }
                } catch (ex : XmlPullParserException) {
                    Log.e("Photos", "getPhotos", ex)
                }
            }
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

    class PhotoCyclerViewAdapter(private val myDataset: Array<HashMap<String, String>>) :
            RecyclerView.Adapter<PhotoCyclerViewAdapter.ViewHolder>() {
        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
                ViewHolder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.photo_cycler_view, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val photo = myDataset[position]
            holder.view.findViewById<TextView>(R.id.photo_cycler_text_view).text = photo["title"]
        }

        override fun getItemCount(): Int = myDataset.size
    }

    companion object {
        val EXTRA_FLICKR_ACCESS_TOKEN = "io.github.cat_in_136.flickrsimpleorganizr.EXTRA_FLICKR_ACCESS_TOKEN"
    }
}
