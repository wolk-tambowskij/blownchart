package app.lawnchair.api.gh

import app.lawnchair.util.kotlinxJson
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubApi {
    @GET("repos/LawnchairLauncher/lawnchair/releases")
    suspend fun getReleases(): List<GitHubRelease>

    @GET("repos/{owner}/{repo}/events")
    suspend fun getRepositoryEvents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): List<GitHubEvent>
}

const val BASE_URL = "https://api.github.com/"

val retrofit: Retrofit by lazy {
    Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
        .build()
}

val api: GitHubApi by lazy {
    retrofit.create(GitHubApi::class.java)
}

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val assets: List<GitHubAsset>,
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
)

@Serializable
data class GitHubEvent(
    val type: String,
    val actor: Actor,
    val created_at: String,
)

@Serializable
data class Actor(
    val login: String,
)
