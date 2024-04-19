/*
 * SPDX-FileCopyrightText: 2023 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.location.ui

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.microg.gms.location.core.R
import org.microg.gms.location.manager.LocationAllAppsDatabase
import org.microg.gms.ui.AppIconPreference
import org.microg.gms.ui.getApplicationInfoIfExists
import java.util.HashMap
import java.util.Locale


class AllAppsLocationPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var progress: Preference
    private lateinit var locationApps: PreferenceCategory
    private lateinit var database: LocationAllAppsDatabase
    private val permissions: HashMap<String, Array<Boolean>> = HashMap<String, Array<Boolean>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progress = preferenceScreen.findPreference("pref_location_apps_all_progress") ?: progress
        database = LocationAllAppsDatabase(requireContext())
        val initializeJob: Job = lifecycleScope.launch {
            val packageManager: PackageManager = requireContext().packageManager;
            val mApps = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (info in mApps) {
                if (info.requestedPermissions != null) {
                    var finePermGranted = false
                    var coarsePermGranted = false
                    for ((reqIndex, reqPerm) in info.requestedPermissions.withIndex()) {
                        var locationRequested = false
                        if(reqPerm.equals("android.permission.ACCESS_FINE_LOCATION")) {
                            locationRequested = true
                            finePermGranted = (info.requestedPermissionsFlags[reqIndex] and
                                (PackageInfo.REQUESTED_PERMISSION_GRANTED or PackageInfo.REQUESTED_PERMISSION_IMPLICIT)) > 0
                        }
                        if(reqPerm.equals("android.permission.ACCESS_COARSE_LOCATION")) {
                            locationRequested = true
                            coarsePermGranted = (info.requestedPermissionsFlags[reqIndex] and
                                    (PackageInfo.REQUESTED_PERMISSION_GRANTED or PackageInfo.REQUESTED_PERMISSION_IMPLICIT)) > 0
                        }
                        if(locationRequested) {
                            database.insertIfMissing(
                                info.packageName,
                                false,
                                LocationAllAppsDatabase.FIELD_TYPE to LocationAllAppsDatabase.TYPE_NO_LOCATION
                            )
                        }
                    }
                    permissions.put(info.packageName, arrayOf(coarsePermGranted, finePermGranted))
                }
            }
            database.close()

        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                progress.isVisible = true
                initializeJob.join()
                val context = requireContext()
                val apps = withContext(Dispatchers.IO) {
                    val res = database.listAppsByPackageName().map { app ->
                        app to context.packageManager.getApplicationInfoIfExists(app.first)
                    }.map { (app, applicationInfo) ->
                        val pref = AppIconPreference(context)
                        var permInfo = permissions.get(app.first)
                        pref.title = applicationInfo?.loadLabel(context.packageManager) ?: app.first
                        pref.summary = "Type: ${LocationAllAppsDatabase.TYPE_LABELS[app.second]} Coarse Perm: ${permInfo?.get(0)} Fine Perm: ${permInfo?.get(1)}"
                        pref.icon = applicationInfo?.loadIcon(context.packageManager) ?: AppCompatResources.getDrawable(context, android.R.mipmap.sym_def_app_icon)
//                        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
//                            findNavController().navigate(requireContext(), R.id.openLocationAppDetailsFromAll, bundleOf("package" to app.first))
//                            true
//                        }
                        pref.key = "pref_location_app_" + app.first
                        pref
                    }.sortedBy {
                        it.title.toString().lowercase(Locale.ROOT)
                    }.mapIndexed { idx, pair ->
                        pair.order = idx
                        pair
                    }
                    res
                }
                locationApps.removeAll()
                locationApps.isVisible = true
                for (app in apps) {
                    locationApps.addPreference(app)
                }
                progress.isVisible = false
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_location_all_apps)
        locationApps = preferenceScreen.findPreference("prefcat_location_apps") ?: locationApps
    }
}