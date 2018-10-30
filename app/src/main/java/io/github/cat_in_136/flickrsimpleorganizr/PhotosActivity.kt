package io.github.cat_in_136.flickrsimpleorganizr

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.*
import android.widget.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
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

                    findViewById<GridView>(R.id.photo_grid_view).apply {
                        adapter = PhotoGridViewAdapter(this, data.toTypedArray())
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

    class PhotoGridViewAdapter(private val gridView: GridView, private val dataset: Array<HashMap<String, String>>) : BaseAdapter() {

        private val mSelection = mutableSetOf<Int>()

        override fun getCount(): Int = dataset.size
        override fun getItem(position: Int): Any? = dataset[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val gridItemView = if (convertView == null) {
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.photo_gridview, parent, false)
            } else {
                convertView
            }

            gridItemView.findViewById<CheckBox>(R.id.photo_gridview_check_box).apply {
                setOnCheckedChangeListener(null)
                isChecked = isPositionChecked(position)
                setOnCheckedChangeListener { btn, isChecked ->
                    setNewSelection(position, isChecked)
                }
            }

            val photo = dataset[position]
            gridItemView.findViewById<TextView>(R.id.photo_gridview_text_view).text = photo["title"]
            Glide.with(gridItemView)
                    .load("https://farm1.staticflickr.com/${photo["server"]}/${photo["id"]}_${photo["secret"]}_t.jpg")
                    .apply(RequestOptions()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(ColorDrawable(Color.LTGRAY))
                            .error(ColorDrawable(Color.RED))
                            .centerCrop())
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(gridItemView.findViewById<ImageView>(R.id.photo_gridview_image_view))
            gridItemView.findViewById<CheckBox>(R.id.photo_gridview_check_box)
                    .isChecked = isPositionChecked(position)

            return gridItemView
        }

        fun setNewSelection(position: Int, value: Boolean) {
            if (value) {
                mSelection.add(position)
            } else {
                mSelection.remove(position)
            }
            notifyDataSetChanged()
        }

        fun isPositionChecked(position: Int): Boolean = mSelection.contains(position)

        fun getCheckedItemCount(): Int = mSelection.size

        fun clearSelection() {
            mSelection.clear()
            notifyDataSetChanged()
        }
    }

    companion object {
        val EXTRA_FLICKR_ACCESS_TOKEN = "io.github.cat_in_136.flickrsimpleorganizr.EXTRA_FLICKR_ACCESS_TOKEN"
    }
}
