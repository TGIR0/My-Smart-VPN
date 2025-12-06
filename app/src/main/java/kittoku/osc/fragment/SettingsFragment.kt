package kittoku.osc.fragment

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import kittoku.osc.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.home, rootKey)
    }
}
