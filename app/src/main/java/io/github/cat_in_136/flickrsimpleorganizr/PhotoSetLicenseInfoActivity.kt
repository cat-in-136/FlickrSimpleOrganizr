package io.github.cat_in_136.flickrsimpleorganizr

import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.*
import com.github.scribejava.core.model.OAuth1AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.android.UI
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext


class PhotoSetLicenseInfoActivity : AppCompatActivity(), CoroutineScope {
    internal val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var flickrClient: FlickrClient? = null

    private var photos: Array<HashMap<String, String>>? = null

    private val licenseDataRadioButton = mutableListOf<RadioButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_set_license_info)

        intent.getSerializableExtra(PhotosActivity.EXTRA_FLICKR_ACCESS_TOKEN).let {
            if (it is OAuth1AccessToken) {
                val (apiKey, sharedSecret) = PreferenceManager.getDefaultSharedPreferences(this@PhotoSetLicenseInfoActivity).let {
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

        val radioGroup = findViewById<RadioGroup>(R.id.photo_set_license_radio_group)
        val progressBar = findViewById<ProgressBar>(R.id.photo_set_license_progress_bar)
        licenseDataRadioButton.clear()
        radioGroup.removeAllViews()
        launch {
            val licenses = fetchListOfAvailablePhotoLicenses().await()
            licenses.forEach {
                val radioButton = RadioButton(this@PhotoSetLicenseInfoActivity)
                radioButton.tag = it
                radioButton.text = it.name
                radioButton.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                radioGroup.addView(radioButton)
                licenseDataRadioButton.add(radioButton)
            }
            progressBar.visibility = View.GONE
            radioGroup.visibility = View.VISIBLE
        }
    }

    private fun fetchListOfAvailablePhotoLicenses() = async(Dispatchers.Default) {
        val licenseDataList = mutableListOf<LicenseData>()

        val request = OAuthRequest(Verb.GET, "https://api.flickr.com/services/rest/")
        request.addQuerystringParameter("method", "flickr.photos.licenses.getInfo")
        val (code, _, body) = flickrClient!!.access(request).await()

        if (code == 200) {
            XmlPullParserFactory.newInstance().newPullParser().let {
                it.setInput(body.reader())
                try {
                    var eventType = it.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            if (it.name == "license") {
                                val id = it.getAttributeValue(null, "id").toInt(10)
                                val name = it.getAttributeValue(null, "name")

                                // Note : as of this writing the "no known copyright restrictions" license (7) is not a valid argument.
                                if (id != 7) {
                                    licenseDataList.add(LicenseData(id, name))
                                }
                            }
                        }
                        eventType = it.next()
                    }
                } catch (ex: XmlPullParserException) {
                    Log.e("Photos", "fetchListOfAvailablePhotoLicenses", ex)
                }
            }
        }

        licenseDataList.toMutableList()
    }

    fun onSetLicense(view: View) {
        findViewById<Button>(R.id.photo_set_license_button).isEnabled = false

        assert(photos != null)

        val licenseData = licenseDataRadioButton.firstOrNull { it.isChecked }.let {
            if (it != null) {
                it.tag as LicenseData
            } else {
                findViewById<Button>(R.id.photo_set_license_button).isEnabled = true
                return
            }
        }

        async {
            val ret = photos!!.map {
                val request = OAuthRequest(Verb.GET, "https://api.flickr.com/services/rest/")
                request.addQuerystringParameter("method", "flickr.photos.licenses.setLicense")
                request.addQuerystringParameter("photo_id", it["id"])
                request.addQuerystringParameter("license_id", licenseData.id.toString())
                val (code, headers, body) = flickrClient!!.access(request).await()

                return@map Pair(code, body)
            }

            if (ret!!.any { return@any it.first != 200 }) {
                val errorSummay = photos!!.mapIndexed { index, photo ->
                    if (ret!![index].first != 200) {
                        "${photo["title"]} ${photo["id"]} : NG"
                    } else {
                        null
                    }
                }.filterNotNull().joinToString("\n")

                alert(this@PhotoSetLicenseInfoActivity,
                        getResources().getString(R.string.photo_set_license_failed_msg) + "\n\n" + errorSummay,
                        this@PhotoSetLicenseInfoActivity.getString(android.R.string.dialog_alert_title))
            } else {
                alert(this@PhotoSetLicenseInfoActivity, R.string.photo_set_license_success_msg)
                findViewById<Button>(R.id.photo_set_license_button).isEnabled = true
                this@PhotoSetLicenseInfoActivity.finish()
            }
            findViewById<Button>(R.id.photo_set_license_button).isEnabled = true
        }
    }

    private data class LicenseData(val id: Int, val name: String) {
        override fun toString(): String {
            return "${this.javaClass.name}[id=${id},name=\"${name}\"]"
        }
    }
}

