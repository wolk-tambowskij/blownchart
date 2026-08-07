package app.blownchart.ui.preferences.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import retrofit2.create

class AboutViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val api: GitHubService = gitHubApiRetrofit.create()

    private val nightlyBuildsRepository = NightlyBuildsRepository(
        applicationContext = application,
        api = api,
    )

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState = _uiState.asStateFlow()
    val updateState = nightlyBuildsRepository.updateState

    init {
        _uiState.update {
            it.copy(
                versionName = BuildConfig.VERSION_NAME,
                commitHash = BuildConfig.COMMIT_HASH,
                topLinks = topLinks,
            )
        }

        // Deliberately not wired up to nightlyBuildsRepository.checkForUpdate(): that checks
        // upstream Lawnchair's own GitHub releases, which isn't this fork's update feed.
    }

    fun downloadUpdate() {
        nightlyBuildsRepository.downloadUpdate()
    }

    fun installUpdate(file: File) {
        nightlyBuildsRepository.installUpdate(file)
    }

    companion object {
        // BlownChart is a personal fork of Lawnchair; this points back to the upstream project
        // rather than to Lawnchair's own community/support channels, which don't apply here.
        private val topLinks = listOf(
            Link(
                iconResId = R.drawable.ic_github,
                labelResId = R.string.github,
                url = "https://github.com/BlownChartLauncher/lawnchair",
            ),
        )
    }
}
