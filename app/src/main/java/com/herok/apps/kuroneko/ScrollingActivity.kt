package com.herok.apps.kuroneko

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class ScrollingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scrolling)
        setSupportActionBar(findViewById(R.id.toolbar))
        findViewById<CollapsingToolbarLayout>(R.id.toolbar_layout).title = title

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.main_preference_fragment, SettingsFragment())
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_scrolling, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            preferenceScreen.findPreference<EditTextPreference>(PreferenceKeys.KEY_BG_COLOR)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    return@OnPreferenceChangeListener checkColor(newValue.toString())
                }
            preferenceScreen.findPreference<EditTextPreference>(PreferenceKeys.KEY_EYES_COLOR)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    return@OnPreferenceChangeListener checkColor(newValue.toString())
                }
            preferenceScreen.findPreference<EditTextPreference>(PreferenceKeys.KEY_TEXT_COLOR)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    return@OnPreferenceChangeListener checkColor(newValue.toString())
                }
            preferenceScreen.findPreference<EditTextPreference>(PreferenceKeys.KEY_TEXT_SIZE)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    return@OnPreferenceChangeListener checkFloat(newValue.toString())
                }

            preferenceScreen.findPreference<Preference>(PreferenceKeys.KEY_SET_BG)?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    intent.putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(requireContext(), KuroNekoService::class.java)
                    )
                    startActivity(intent)
                    return@OnPreferenceClickListener true
                }

        }

        private fun checkColor(newValue: String): Boolean{
            try{
                val newColor = Color.parseColor(newValue)
            }catch(e: Exception){
                showErrorMessage()
                return false
            }
            return true
        }

        private fun checkFloat(newValue: String): Boolean {
            try{
                val newFloat = newValue.toFloat()
            }catch(e: Exception){
                showErrorMessage()
                return false
            }
            return true
        }

        private fun showErrorMessage(){
            view?.let { Snackbar.make(it, R.string.value_error, Snackbar.LENGTH_LONG).show() }
        }

    }

}