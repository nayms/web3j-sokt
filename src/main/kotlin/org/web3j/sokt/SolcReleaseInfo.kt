package org.web3j.sokt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SolcReleaseAsset (
    @JsonProperty("browser_download_url")
    var browserDownloadUrl: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SolcReleaseInfo(
    @JsonProperty("tag_name")
    var tagName: String,
    var assets: List<SolcReleaseAsset>
) {
    fun toSolcRelease(): SolcRelease {
        val macUrl: String = assets.find { asset -> asset.browserDownloadUrl.contains("macos") }?.browserDownloadUrl ?: ""
        val linuxUrl: String = assets.find { asset -> asset.browserDownloadUrl.contains("linux") }?.browserDownloadUrl ?: ""
        val windowsUrl: String = assets.find { asset -> asset.browserDownloadUrl.contains("windows") }?.browserDownloadUrl ?: ""
        return SolcRelease(
            version = tagName.removePrefix("v"),
            windowsUrl = windowsUrl,
            linuxUrl = linuxUrl,
            macUrl = macUrl
        )
    }
}
