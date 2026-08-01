/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.util

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Handler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.launcher3.AppFilter
import com.android.launcher3.LauncherAppState
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.IconRequestInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import java.util.Comparator.comparing
import java.util.Locale

@Composable
fun appsState(
    filter: AppFilter = AppFilter(LocalContext.current),
    comparator: Comparator<App> = appComparator,
): State<List<App>> {
    val context = LocalContext.current
    val appsState = remember { mutableStateOf(emptyList<App>()) }
    DisposableEffect(Unit) {
        Utilities.postAsyncCallback(Handler(MODEL_EXECUTOR.looper)) {
            val launcherApps = context.getSystemService(LauncherApps::class.java)

            if (launcherApps != null) {
                val activityInfos = UserCache.INSTANCE.get(context).userProfiles.asSequence()
                    .flatMap { launcherApps.getActivityList(null, it) }
                    .filter { filter.shouldShowApp(it.componentName) }
                    .toList()
                // Load every icon with a single grouped sql query instead of one query per app -
                // the per-app path this used to take (via App's own init block) meant e.g. the
                // folder app-picker took tens of seconds to open on a device with ~1800 apps.
                val appInfos = activityInfos.map { AppInfo(context, it, it.user) }
                val iconRequestInfos = activityInfos.indices.map { i ->
                    // launcherActivityInfo is also the fallback source getTitlesAndIconsInBulk()
                    // uses to generate an icon on the spot for an app that isn't cached yet (e.g.
                    // just installed) - must not be null, unlike the itemInfo it's paired with.
                    IconRequestInfo(appInfos[i], activityInfos[i], false)
                }
                LauncherAppState.getInstance(context).iconCache.getTitlesAndIconsInBulk(iconRequestInfos)
                appsState.value = activityInfos.indices
                    .map { i -> App(activityInfos[i], appInfos[i]) }
                    .sortedWith(comparator)
            }
        }
        onDispose { }
    }
    return appsState
}

class App(private val info: LauncherActivityInfo, appInfo: AppInfo) {

    val label get() = info.label.toString()
    val icon: Bitmap = appInfo.bitmap.icon
    val key = ComponentKey(info.componentName, info.user)

    fun toAppInfo(context: Context): AppInfo {
        return AppInfo(context, info, info.user)
    }
}

val appComparator: Comparator<App> = comparing { it.label.lowercase(Locale.getDefault()) }
