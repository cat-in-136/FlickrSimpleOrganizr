package io.github.cat_in_136.flickrsimpleorganizr

import android.app.DatePickerDialog
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.*
import com.github.scribejava.core.model.OAuth1AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.async
import kotlin.coroutines.experimental.CoroutineContext
import com.ikovac.timepickerwithseconds.MyTimePickerDialog
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.text.SimpleDateFormat
import java.util.*


class EditDateActivity : AppCompatActivity(), CoroutineScope {
    internal val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var flickrClient : FlickrClient? = null

    private var photos : Array<HashMap<String, String>>? = null

    private val datePostedCalendar = Calendar.getInstance()

    private val dateDakenCalender = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_date)

        findViewById<CheckBox>(R.id.date_posted_checkbox).setOnCheckedChangeListener { _, _ -> updateDateTimeFields() }
        findViewById<EditText>(R.id.date_posted_date).setOnClickListener {
            DatePickerDialog(this@EditDateActivity,
                    DatePickerDialog.OnDateSetListener { view, year, monthOfYear, dayOfMonth ->
                        datePostedCalendar.set(Calendar.YEAR, year)
                        datePostedCalendar.set(Calendar.MONTH, monthOfYear)
                        datePostedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        updateDateTimeFields()
                    }, datePostedCalendar.get(Calendar.YEAR),
                    datePostedCalendar.get(Calendar.MONTH),
                    datePostedCalendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        findViewById<EditText>(R.id.date_posted_time).setOnClickListener {
            MyTimePickerDialog(this@EditDateActivity, MyTimePickerDialog.OnTimeSetListener { view, hourOfDay, minute, seconds->
                datePostedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                datePostedCalendar.set(Calendar.MINUTE, minute)
                datePostedCalendar.set(Calendar.SECOND, seconds)
                updateDateTimeFields()
            }, datePostedCalendar.get(Calendar.HOUR_OF_DAY),
                    datePostedCalendar.get(Calendar.MINUTE),
                    datePostedCalendar.get(Calendar.SECOND),
                    true).show()
        }

        findViewById<CheckBox>(R.id.date_taken_checkbox).setOnCheckedChangeListener { _, _ -> updateDateTimeFields() }
        findViewById<EditText>(R.id.date_taken_date).setOnClickListener {
            DatePickerDialog(this@EditDateActivity,
                    DatePickerDialog.OnDateSetListener { view, year, monthOfYear, dayOfMonth ->
                        dateDakenCalender.set(Calendar.YEAR, year)
                        dateDakenCalender.set(Calendar.MONTH, monthOfYear)
                        dateDakenCalender.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        updateDateTimeFields()
            }, dateDakenCalender.get(Calendar.YEAR),
                    dateDakenCalender.get(Calendar.MONTH),
                    dateDakenCalender.get(Calendar.DAY_OF_MONTH)).show()
        }
        findViewById<EditText>(R.id.date_taken_time).setOnClickListener {
            MyTimePickerDialog(this@EditDateActivity, MyTimePickerDialog.OnTimeSetListener { view, hourOfDay, minute, seconds->
                dateDakenCalender.set(Calendar.HOUR_OF_DAY, hourOfDay)
                dateDakenCalender.set(Calendar.MINUTE, minute)
                dateDakenCalender.set(Calendar.SECOND, seconds)
                updateDateTimeFields()
            }, dateDakenCalender.get(Calendar.HOUR_OF_DAY),
                    dateDakenCalender.get(Calendar.MINUTE),
                    dateDakenCalender.get(Calendar.SECOND),
                    true).show()
        }
        findViewById<Spinner>(R.id.date_taken_granularity_spinner).onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>) {
            }
            override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
                val (isEnabledForDate, isEnabledForTime) = when (pos) {
                    DATE_GRANULARITY_SPINNER_POS_MONTH -> Pair(true, false)
                    DATE_GRANULARITY_SPINNER_POS_YEAR -> Pair(true, false)
                    DATE_GRANULARITY_SPINNER_POS_CIRCA -> Pair(false, false)
                    else -> Pair(true, true)
                }
                findViewById<EditText>(R.id.date_taken_date).isEnabled = isEnabledForDate
                findViewById<EditText>(R.id.date_taken_time).isEnabled = isEnabledForTime
            }
        }

        intent.getSerializableExtra(PhotosActivity.EXTRA_FLICKR_ACCESS_TOKEN).let {
            if (it is OAuth1AccessToken) {
                val (apiKey, sharedSecret) = PreferenceManager.getDefaultSharedPreferences(this@EditDateActivity).let {
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

        photos?.first().let {
            retrieveDatesFromPhoto(it!!)
        }
        updateDateTimeFields()
    }

    fun onChangeDates(view: View) {
        findViewById<Button>(R.id.change_dates_button).isEnabled = false

        val date_posted = datePostedCalendar.time.time / 1000
        val date_taken = "%1\$tF %1\$tT".format(dateDakenCalender)
        val date_taken_granularity = DATE_GRANULARITY_SPINNER_POS_TO_VALUE[findViewById<Spinner>(R.id.date_taken_granularity_spinner).selectedItemPosition]

        assert(photos != null)

        async {
            val ret = photos!!.map {
                val request = OAuthRequest(Verb.GET, "https://api.flickr.com/services/rest/")
                request.addQuerystringParameter("method", "flickr.photos.setDates")
                request.addQuerystringParameter("photo_id", it["id"])
                request.addQuerystringParameter("date_posted", date_posted.toString(10))
                request.addQuerystringParameter("date_taken", date_taken)
                request.addQuerystringParameter("date_taken_granularity", date_taken_granularity.toString(10))
                val (code, _, body) = flickrClient!!.access(request).await()

                return@map Pair(code, body)
            }

            if (ret.any { return@any it.first != 200 }) {
                val errorSummay = photos!!.mapIndexed { index, photo ->
                    if (ret[index].first != 200) {
                        "${photo["title"]} ${photo["id"]} : NG"
                    } else {
                        null
                    }
                }.filterNotNull().joinToString("\n")

                alert(this@EditDateActivity,
                        getResources().getString(R.string.change_dates_failed_msg) + "\n\n" + errorSummay,
                        this@EditDateActivity.getString(android.R.string.dialog_alert_title))
            } else {
                alert(this@EditDateActivity, R.string.change_dates_success_msg)
                findViewById<Button>(R.id.change_dates_button).isEnabled = true
                this@EditDateActivity.finish()
            }
            findViewById<Button>(R.id.change_dates_button).isEnabled = true
        }
    }

    private fun retrieveDatesFromPhoto(photo: HashMap<String, String>) {
        async {
            val request = OAuthRequest(Verb.GET, "https://api.flickr.com/services/rest/")
            request.addQuerystringParameter("method", "flickr.photos.getInfo")
            request.addQuerystringParameter("photo_id", photo["id"])
            val (code, _, body) = flickrClient!!.access(request).await()

            if (code == 200) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

                XmlPullParserFactory.newInstance().newPullParser().let {
                    it.setInput(body.reader())
                    try {
                        var eventType = it.eventType
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) {
                                if (it.name == "dates") {
                                    val posted = it.getAttributeValue(null, "posted")
                                    datePostedCalendar.time = Date(posted.toLong(10) * 1000)

                                    val taken = it.getAttributeValue(null, "taken")
                                    dateDakenCalender.time = dateFormat.parse(taken)

                                    updateDateTimeFields()

                                    val takengranularity = it.getAttributeValue(null, "takengranularity")
                                    findViewById<Spinner>(R.id.date_taken_granularity_spinner).setSelection(
                                            getDateGranularityPosFromValue(takengranularity.toInt(10)))
                                }
                            }
                            eventType = it.next()
                        }
                    } catch (ex : XmlPullParserException) {
                        Log.e("Photos", "getInfo", ex)
                    }
                }
            }
        }
    }

    private fun updateDateTimeFields() {
        findViewById<CheckBox>(R.id.date_posted_checkbox).isChecked.let { checked ->
            findViewById<EditText>(R.id.date_posted_date).isEnabled = checked
            findViewById<EditText>(R.id.date_posted_time).isEnabled = checked
        }
        findViewById<EditText>(R.id.date_posted_date)
                .setText("%1\$tF".format(datePostedCalendar), TextView.BufferType.NORMAL)
        findViewById<EditText>(R.id.date_posted_time)
                .setText("%1\$tT".format(datePostedCalendar), TextView.BufferType.NORMAL)

        findViewById<CheckBox>(R.id.date_taken_checkbox).isChecked.let { checked ->
            findViewById<EditText>(R.id.date_taken_date).isEnabled = checked
            findViewById<EditText>(R.id.date_taken_time).isEnabled = checked
            findViewById<Spinner>(R.id.date_taken_granularity_spinner).isEnabled = checked
        }
        findViewById<EditText>(R.id.date_taken_date)
                .setText("%1\$tF".format(dateDakenCalender), TextView.BufferType.NORMAL)
        findViewById<EditText>(R.id.date_taken_time)
                .setText("%1\$tT".format(dateDakenCalender), TextView.BufferType.NORMAL)
    }

    private fun getDateGranularityPosFromValue(granularityValue: Int): Int {
        return when (granularityValue) {
            DATE_GRANULARITY_TIME -> DATE_GRANULARITY_SPINNER_POS_TIME
            DATE_GRANULARITY_MONTH -> DATE_GRANULARITY_SPINNER_POS_MONTH
            DATE_GRANULARITY_YEAR -> DATE_GRANULARITY_SPINNER_POS_YEAR
            DATE_GRANULARITY_CIRCA -> DATE_GRANULARITY_SPINNER_POS_CIRCA
            else -> -1
        }
    }

    companion object {
        private const val DATE_GRANULARITY_TIME = 0
        private const val DATE_GRANULARITY_MONTH = 4
        private const val DATE_GRANULARITY_YEAR = 6
        private const val DATE_GRANULARITY_CIRCA = 8

        private const val DATE_GRANULARITY_SPINNER_POS_TIME = 0
        private const val DATE_GRANULARITY_SPINNER_POS_MONTH = 1
        private const val DATE_GRANULARITY_SPINNER_POS_YEAR = 2
        private const val DATE_GRANULARITY_SPINNER_POS_CIRCA = 3

        private val DATE_GRANULARITY_SPINNER_POS_TO_VALUE = arrayOf(
                DATE_GRANULARITY_TIME, DATE_GRANULARITY_MONTH, DATE_GRANULARITY_YEAR, DATE_GRANULARITY_CIRCA)
    }
}
