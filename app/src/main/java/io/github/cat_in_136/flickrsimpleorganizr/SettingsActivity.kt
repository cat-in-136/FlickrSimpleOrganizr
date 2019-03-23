package io.github.cat_in_136.flickrsimpleorganizr

import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.view.MenuItem

class SettingsActivity : AppCompatPreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    override fun onBuildHeaders(target: List<PreferenceActivity.Header>) {
        loadHeadersFromResource(R.xml.pref_headers, target)
    }

    override fun isValidFragment(fragmentName: String): Boolean {
        return PreferenceFragment::class.java.name == fragmentName
                || AccountPreferenceFragment::class.java.name == fragmentName
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    class AccountPreferenceFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_account)
            setHasOptionsMenu(true)

            letValueShownInSummary(findPreference(KEY_OAUTH_API_KEY))
            letValueShownInSummary(findPreference(KEY_OAUTH_SHARED_SECRET))

            findPreference(KEY_LOGOUT).let {
                it.isEnabled = (retrieveFlickrAccessToken(activity) != null)
                it.setOnPreferenceClickListener {
                    clearFlickrAccessToken(activity)
                    it.isEnabled = false
                    return@setOnPreferenceClickListener true
                }
            }
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            val id = item.itemId
            if (id == android.R.id.home) {
                startActivity(Intent(activity, SettingsActivity::class.java))
                return true
            }
            return super.onOptionsItemSelected(item)
        }

        companion object {
            const val KEY_LOGOUT = "preference_account_logout"
            const val KEY_OAUTH_API_KEY = "preference_account_oauth_api_key"
            const val KEY_OAUTH_SHARED_SECRET = "preference_account_oauth_shared_secret"
        }
    }
}

private fun letValueShownInSummary(pref: Preference) {
    pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, value ->
        pref.summary = value as CharSequence
        true
    }
    pref.summary = pref.sharedPreferences.getString(pref.key, "")
}
