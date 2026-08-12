package app.blownchart.allapps

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.blownchart.data.folder.model.FolderViewModel
import app.blownchart.launcher
import app.blownchart.preferences.PreferenceManager
import app.blownchart.preferences2.PreferenceManager2
import app.blownchart.util.categorizeAppsWithSystemAndGoogle
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.PrivateProfileManager
import com.android.launcher3.allapps.WorkProfileManager
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext
import com.patrykmichalik.opto.core.onEach
import java.util.function.Predicate

@Suppress("SYNTHETIC_PROPERTY_WITHOUT_JAVA_ORIGIN")
class BlownChartAlphabeticalAppsList<T>(
    private val context: T,
    private val appsStore: AllAppsStore<T>,
    workProfileManager: WorkProfileManager?,
    privateProfileManager: PrivateProfileManager?,
) : AlphabeticalAppsList<T>(context, appsStore, workProfileManager, privateProfileManager),
    OnIDPChangeListener
    where T : Context, T : ActivityContext {

    private var hiddenApps: Set<String> = setOf()
    private val prefs2 = PreferenceManager2.getInstance(context)
    private val prefs = PreferenceManager.getInstance(context)

    private val viewModel: FolderViewModel by (context as ComponentActivity).viewModels()
    private var folderList = mutableListOf<FolderInfo>()

    // A Set gives O(1) membership checks; this is rebuilt on every apps update and checked
    // once per installed app, so a List here would make the filter below quadratic.
    private val filteredSet = mutableSetOf<AppInfo>()

    init {
        context.launcher.deviceProfile.inv.addOnChangeListener(this)
        try {
            prefs2.hiddenApps.onEach(launchIn = context.launcher.lifecycleScope) {
                hiddenApps = it
                onAppsUpdated()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize hidden apps", t)
        }
        observeFolders()
    }

    private fun observeFolders() {
        viewModel.foldersLiveData.observe(context as LifecycleOwner) { folders ->
            // FolderService.getFoldersFlow() already emits folders in the right order for the
            // current folderManualOrder setting (by rank, or alphabetically) - re-sorting here
            // unconditionally used to silently discard manual order and explains why dragging
            // folders in settings never changed anything in the live drawer.
            folderList = folders.toMutableList()
            updateAdapterItems()
        }
    }

    override fun updateItemFilter(itemFilter: Predicate<ItemInfo>?) {
        mItemFilter = Predicate { info ->
            require(info is AppInfo) { "`info` must be an instance of `AppInfo`." }
            val componentKey = info.toComponentKey().toString()
            (itemFilter?.test(info) != false) && !hiddenApps.contains(componentKey)
        }
        onAppsUpdated()
    }

    override fun addAppsWithSections(appList: List<AppInfo?>?, startPosition: Int): Int {
        if (appList.isNullOrEmpty()) return startPosition
        val drawerListDefault = prefs.drawerList.get()
        filteredSet.clear()
        var position = startPosition

        // Show app drawer folders only on main profile, to prevent state complexity
        if (isWorkOrPrivateSpace(appList)) return super.addAppsWithSections(appList, position)

        if (!drawerListDefault) {
            val validApps = appList.mapNotNull { it }
            val finalCategorizedApps = categorizeAppsWithSystemAndGoogle(validApps, context)

            finalCategorizedApps.forEach { (category, apps) ->
                if (apps.size == 1) {
                    mAdapterItems.add(AdapterItem.asApp(apps.first()))
                } else {
                    val folderInfo = FolderInfo().apply {
                        title = category
                        apps.forEach { add(it) }
                    }
                    mAdapterItems.add(AdapterItem.asFolder(folderInfo))
                }
                position++
            }
        } else {
            folderList.forEach { folder ->
                // Minimum to show as a folder chip is two *direct* items - two shortcuts, two
                // subfolders, or one of each - not two flattened apps. A folder whose only direct
                // content is a single subfolder is still just one item, no matter how many apps
                // live inside that subfolder, so it must not pass this check even though its
                // flattened app count could be well over one; below this size, its own apps fall
                // through unfiltered (filteredSet stays empty for it) and show up as plain,
                // ungrouped entries instead of vanishing.
                if (folder.getContents().size >= 2) {
                    val folderInfo = FolderInfo()
                    folderInfo.title = folder.title
                    mAdapterItems.add(AdapterItem.asFolder(folderInfo))
                    // Folder.bind() re-sorts a folder's contents by rank (falling back to
                    // cellY/cellX) every time it's opened, ignoring list-insertion order - and the
                    // apps re-resolved below are long-lived, shared AllAppsStore instances that
                    // may carry a stale rank left over from a previous bind of this or any other
                    // folder containing the same app. Stamping a fresh, strictly increasing rank
                    // here, in the exact order we want (subfolder first, then apps), guarantees
                    // that resort always reproduces this order instead of occasionally reshuffling
                    // around leftover state on a shared item.
                    var rank = 0
                    folder.getContents().forEach { item ->
                        if (item is FolderInfo) {
                            // Rebuild the nested subfolder the same way as the top-level folder
                            // above, re-resolving its own apps against the live AllAppsStore
                            // instance - otherwise they'd keep FolderService's own AppInfo objects
                            // (built straight from LauncherApps, with no icon/label populated)
                            // forever, since nothing else in the app ever refreshes them the way
                            // this re-resolution keeps a direct folder item current.
                            val subfolderInfo = FolderInfo()
                            subfolderInfo.id = item.id
                            subfolderInfo.title = item.title
                            var subRank = 0
                            item.getContents().forEach { subItem ->
                                (appsStore.getApp(subItem.componentKey) as? AppInfo)?.let {
                                    it.rank = subRank++
                                    subfolderInfo.add(it)
                                    if (prefs.folderApps.get()) filteredSet.add(it)
                                }
                            }
                            subfolderInfo.rank = rank++
                            folderInfo.add(subfolderInfo)
                        } else {
                            (appsStore.getApp(item.componentKey) as? AppInfo)?.let {
                                it.rank = rank++
                                folderInfo.add(it)
                                if (prefs.folderApps.get()) filteredSet.add(it)
                            }
                        }
                    }
                }
                position++
            }
            val remainingApps = appList.filterNot { app -> filteredSet.contains(app) && prefs.folderApps.get() }
            position = super.addAppsWithSections(remainingApps, position)
        }

        return position
    }

    override fun onIdpChanged(modelPropertiesChanged: Boolean) {
        onAppsUpdated()
    }
}
