package io.github.cat_in_136.flickrsimpleorganizr

import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceManager
import android.support.v7.app.AlertDialog
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.EditText
import com.github.scribejava.core.model.OAuth1AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import org.w3c.dom.Text
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.suspendCoroutine

class MainActivity : AppCompatActivity(), CoroutineScope {
    internal val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    fun startLoginToFlickr(view : View) {
        async {
            val (apiKey, sharedSecret) = PreferenceManager.getDefaultSharedPreferences(this@MainActivity).let {
                Pair(
                    it.getString(SettingsActivity.AccountPreferenceFragment.KEY_OAUTH_API_KEY, ""),
                    it.getString(SettingsActivity.AccountPreferenceFragment.KEY_OAUTH_SHARED_SECRET, "")
                )
            }
            if (TextUtils.isEmpty(apiKey) || TextUtils.isEmpty(sharedSecret)) {
                suspendCoroutine<Nothing?> { continuation ->
                    AlertDialog.Builder(this@MainActivity)
                            .setMessage(R.string.login_oauth_setting_missing_err_msg)
                            .setPositiveButton(android.R.string.ok, null)
                            .setOnDismissListener({ continuation.resume(null) })
                            .show()
                }
                launch { startSettingActivity(view) }
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
                            .setPositiveButton(android.R.string.ok, { dialogInterface, _ ->
                                continuation.resume(verifyTextEditView.text.toString())
                                dialogInterface.dismiss()
                            })
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

                        suspendCoroutine<Nothing?> { continuation ->
                            AlertDialog.Builder(this@MainActivity)
                                    .setMessage(body)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .setOnDismissListener({ continuation.resume(null) })
                                    .show()
                        }
                    } catch (e : Exception) {
                        Log.e("Login", "Test Access Error", e)
                    }
                } else {
                    suspendCoroutine<Nothing?> { continuation ->
                        AlertDialog.Builder(this@MainActivity)
                                .setTitle(android.R.string.dialog_alert_title)
                                .setMessage(R.string.login_verify_key_err_msg)
                                .setPositiveButton(android.R.string.ok, null)
                                .setOnDismissListener({ continuation.resume(null) })
                                .show()
                    }
                }
            }
        }
    }

    fun startSettingActivity(view: View) {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
}
