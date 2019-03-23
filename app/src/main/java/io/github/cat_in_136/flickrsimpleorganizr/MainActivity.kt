package io.github.cat_in_136.flickrsimpleorganizr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.github.scribejava.core.model.OAuth1AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : AppCompatActivity(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    fun startFlickr(view: View) {
        val accessToken = retrieveFlickrAccessToken(this)
        if (accessToken != null) {
            val intent = Intent(this, PhotosActivity::class.java)
            intent.putExtra(PhotosActivity.EXTRA_FLICKR_ACCESS_TOKEN, accessToken)
            startActivity(intent)
        } else {
            startLoginToFlickr(view)
        }
    }

    private fun startLoginToFlickr(view : View) {
        async {
            findViewById<Button>(R.id.login_button).isEnabled = false

            val (apiKey, sharedSecret) = PreferenceManager.getDefaultSharedPreferences(this@MainActivity).let {
                Pair(
                    it.getString(SettingsActivity.AccountPreferenceFragment.KEY_OAUTH_API_KEY, ""),
                    it.getString(SettingsActivity.AccountPreferenceFragment.KEY_OAUTH_SHARED_SECRET, "")
                )
            }
            if (TextUtils.isEmpty(apiKey) || TextUtils.isEmpty(sharedSecret)) {
                alert(this@MainActivity, R.string.login_oauth_setting_missing_err_msg);
                launch { startSettingActivity(view) }
                findViewById<Button>(R.id.login_button).isEnabled = true
                return@async
            }
            val flickrClient = FlickrClient(apiKey, sharedSecret, job)

            val (requestToken, authUrl) = flickrClient.userAuthStep1To2().await()
            val authViewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            startActivity(authViewIntent)

            var accessToken: OAuth1AccessToken? = null
            while (accessToken == null) {
                val verifyCode = suspendCoroutine<String> { continuation ->
                    val verifyTextEditView = EditText(this@MainActivity)
                    verifyTextEditView.setHint(R.string.login_verify_key_hint)

                    AlertDialog.Builder(this@MainActivity)
                            .setTitle(R.string.login_verify_key_label)
                            .setView(verifyTextEditView)
                            .setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                                continuation.resume(verifyTextEditView.text.toString())
                                dialogInterface.dismiss()
                            }
                            .setNegativeButton(android.R.string.cancel, { dialogInterface, _ ->
                                dialogInterface.cancel()
                            })
                            .setOnCancelListener({ dialogInterface ->
                                continuation.resume("")
                                dialogInterface.dismiss()
                            })
                            .show()
                }

                if (TextUtils.isEmpty(verifyCode)) {
                    findViewById<Button>(R.id.login_button).isEnabled = true
                    return@async
                } else {
                    try {
                        accessToken = flickrClient.userAuthStep3(requestToken, verifyCode).await()
                    } catch (e : Exception) {
                        Log.e("Login", "Step3 Error", e)
                    }
                }

                if (accessToken is OAuth1AccessToken) {
                    assert(accessToken == flickrClient.accessToken)
                    try {
                        val testRequest = OAuthRequest(Verb.GET, "https://api.flickr.com/services/rest/")
                        testRequest.addQuerystringParameter("method", "flickr.test.login");
                        val (code, headers, body) = flickrClient.access(testRequest).await()

                        storeFlickrAccessToken(this@MainActivity, accessToken)
                        startFlickr(view)
                    } catch (e : Exception) {
                        Log.e("Login", "Test Access Error", e)
                    }
                } else {
                    alert(this@MainActivity, R.string.login_verify_key_err_msg, android.R.string.dialog_alert_title)
                }
            }
        }

        findViewById<Button>(R.id.login_button).isEnabled = true
    }

    fun startSettingActivity(view: View) {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
}
