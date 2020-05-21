package me.devsaki.hentoid.fragments.preferences

import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.disposables.Disposables
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.PinPreferenceActivity
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.enums.Theme
import me.devsaki.hentoid.services.ImportService
import me.devsaki.hentoid.services.UpdateCheckService
import me.devsaki.hentoid.services.UpdateDownloadService
import me.devsaki.hentoid.util.*


class PreferenceFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val KEY_ROOT = "root"

        fun newInstance(rootKey: String?): PreferenceFragment {
            val fragment = PreferenceFragment()
            if (rootKey != null) {
                val args = Bundle()
                args.putCharSequence(KEY_ROOT, rootKey)
                fragment.arguments = args
            }
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = arguments
        if (arguments != null && arguments.containsKey(KEY_ROOT)) {
            val root = arguments.getCharSequence(KEY_ROOT)
            if (root != null) preferenceScreen = findPreference(root)
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences
                .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceScreen.sharedPreferences
                .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        onFolderChanged()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean =
            when (preference.key) {
                Preferences.Key.PREF_ADD_NO_MEDIA_FILE -> {
                    if (FileHelper.createNoMedia(requireContext()))
                        ToastUtil.toast(R.string.nomedia_file_created)
                    else
                        ToastUtil.toast(R.string.nomedia_file_failed)
                    true
                }
                Preferences.Key.PREF_CHECK_UPDATE_MANUAL -> {
                    onCheckUpdatePrefClick()
                    true
                }
                Preferences.Key.PREF_REFRESH_LIBRARY -> {
                    if (ImportService.isRunning()) {
                        ToastUtil.toast("Import is already running")
                    } else {
                        LibRefreshDialogFragment.invoke(parentFragmentManager, true, false)
                    }
                    true
                }
                Preferences.Key.DELETE_ALL_EXCEPT_FAVS -> {
                    onDeleteAllExceptFavourites()
                    true
                }
                Preferences.Key.EXPORT_LIBRARY -> {
                    LibExportDialogFragment.invoke(parentFragmentManager)
                    true
                }
                Preferences.Key.IMPORT_LIBRARY -> {
                    LibImportDialogFragment.invoke(parentFragmentManager)
                    true
                }
                Preferences.Key.PREF_SETTINGS_FOLDER -> {
                    if (ImportService.isRunning()) {
                        ToastUtil.toast("Import is already running")
                    } else {
                        LibRefreshDialogFragment.invoke(parentFragmentManager, false, true)
                    }
                    true
                }
                Preferences.Key.PREF_APP_LOCK -> {
                    requireContext().startLocalActivity<PinPreferenceActivity>()
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }

    override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
        val preferenceFragment = PreferenceFragment().withArguments {
            putString(ARG_PREFERENCE_ROOT, preferenceScreen.key)
        }

        parentFragmentManager.commit(true) {
            replace(android.R.id.content, preferenceFragment)
            addToBackStack(null) // This triggers a memory leak in LeakCanary but is _not_ a leak : see https://stackoverflow.com/questions/27913009/memory-leak-in-fragmentmanager
        }
    }

    private fun onCheckUpdatePrefClick() {
        if (!UpdateDownloadService.isRunning()) {
            val intent = UpdateCheckService.makeIntent(requireContext(), true)
            requireContext().startService(intent)
        }
    }

    private fun onPrefRequiringRestartChanged() {
        ToastUtil.toast(R.string.restart_needed)
    }

    private fun onFolderChanged() {
        val storageFolderPref: Preference? = findPreference(Preferences.Key.PREF_SETTINGS_FOLDER) as Preference?
        storageFolderPref?.summary = Preferences.getStorageUri()
    }

    private fun onPrefColorThemeChanged() {
        ThemeHelper.applyTheme(requireActivity() as AppCompatActivity, Theme.searchById(Preferences.getColorTheme()))
    }

    private fun onDeleteAllExceptFavourites() {
        val dao = ObjectBoxDAO(activity)
        var searchDisposable = Disposables.empty()

        searchDisposable = dao.getStoredBookIds(true, false).subscribe { list ->
            MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                    .setIcon(R.drawable.ic_warning)
                    .setCancelable(false)
                    .setTitle(R.string.app_name)
                    .setMessage(getString(R.string.pref_ask_delete_all_except_favs, list.size))
                    .setPositiveButton(R.string.yes
                    ) { dialog1: DialogInterface, _: Int ->
                        dialog1.dismiss()
                        searchDisposable.dispose()
                        LibDeleteFragment.invoke(parentFragmentManager, list)
                    }
                    .setNegativeButton(R.string.no
                    ) { dialog12: DialogInterface, _: Int -> dialog12.dismiss() }
                    .create()
                    .show()
        };
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            Preferences.Key.PREF_COLOR_THEME -> onPrefColorThemeChanged()
            Preferences.Key.PREF_DL_THREADS_QUANTITY_LISTS,
            Preferences.Key.PREF_APP_PREVIEW,
            Preferences.Key.PREF_ANALYTICS_PREFERENCE -> onPrefRequiringRestartChanged()
            Preferences.Key.PREF_SETTINGS_FOLDER,
            Preferences.Key.PREF_SD_STORAGE_URI -> onFolderChanged()
        }
    }
}