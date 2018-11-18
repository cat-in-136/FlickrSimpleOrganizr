package io.github.cat_in_136.flickrsimpleorganizr

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
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

        findViewById<GridView>(R.id.photo_grid_view).apply {
            registerForContextMenu(this)
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

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, view, menuInfo)
        menuInflater.inflate(R.menu.photo_gridview_popup, menu)

        menu.findItem(R.id.photo_gridview_popup_item_add_tags).setVisible(
                if (photoGridViewAdapter.getCheckedItemCount() > 0) {
                    true
                } else {
                    false
                })
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.photo_gridview_popup_item_add_tags -> {
                val intent = Intent(this, PhotoAddTagsActivity::class.java)
                intent.putExtra(PhotosActivity.EXTRA_FLICKR_ACCESS_TOKEN, flickrClient?.accessToken)
                intent.putExtra(PhotosActivity.EXTRA_FLICKR_PHOTOS, photoGridViewAdapter.getCheckedItems())
                startActivity(intent)
                return true
            }
            else -> return super.onContextItemSelected(item)
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

                    photoGridViewAdapter = PhotoGridViewAdapter(this@PhotosActivity, data.toTypedArray())
                } catch (ex : XmlPullParserException) {
                    Log.e("Photos", "getPhotos", ex)
                }
            }
        } catch (e : Exception) {
            finishByException(e, "Load Photos").await()
        }

    }

    private var photoGridViewAdapter: PhotoGridViewAdapter
            get() = findViewById<GridView>(R.id.photo_grid_view).adapter as PhotoGridViewAdapter
            set(value) {
                findViewById<GridView>(R.id.photo_grid_view).adapter = value
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

    private class PhotoGridViewAdapter(context: Context, var dataSet: Array<HashMap<String, String>>) : ArrayAdapter<HashMap<String, String>>(context, R.id.photo_grid_view, dataSet) {

        private val mSelection = mutableSetOf<Int>()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val gridItemView = if (convertView == null) {
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.photo_gridview, parent, false).apply {
                            setOnLongClickListener {
                                return@setOnLongClickListener parent.performLongClick()
                            }
                            findViewById<CheckBox>(R.id.photo_gridview_check_box).apply {
                                tag = position
                                isChecked = this@PhotoGridViewAdapter.isPositionChecked(position)

                                setOnClickListener { view ->
                                    (tag as? Int)?.let {
                                        this@PhotoGridViewAdapter.setNewSelection(it, isChecked)
                                    }
                                }
                            }
                        }
            } else {
                convertView.apply {
                    findViewById<CheckBox>(R.id.photo_gridview_check_box).apply {
                        tag = position
                        isChecked = isPositionChecked(position)
                    }
                }
            }

            val photo = getItem(position)
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

            gridItemView.findViewById<ImageView>(R.id.photo_gridview_lockicon).apply {
                visibility = if (photo["ispublic"] == "1") View.GONE else View.VISIBLE
            }

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

        fun getCheckedItems(): Array<HashMap<String, String>> {
            return dataSet.filterIndexed {
                index, _ -> isPositionChecked(index)
            }.toTypedArray()
        }

        fun clearSelection() {
            mSelection.clear()
            notifyDataSetChanged()
        }
    }

    companion object {
        val EXTRA_FLICKR_ACCESS_TOKEN = "io.github.cat_in_136.flickrsimpleorganizr.EXTRA_FLICKR_ACCESS_TOKEN"
        val EXTRA_FLICKR_PHOTOS = "io.github.cat_in_136.flickrsimpleorganizr.EXTRA_FLICKR_PHOTOS"
    }
}
