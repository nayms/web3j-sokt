/*
 * Copyright 2020 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.web3j.sokt

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.zafarkhaja.semver.Version
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.list
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

class VersionResolver(private val directoryPath: String = ".web3j") {

    private val mapper = jacksonObjectMapper()

    operator fun get(uri: String): String {
        val con = URL(uri).openConnection() as HttpsURLConnection
        con.connectTimeout = TimeUnit.MILLISECONDS.toMillis(200).toInt()
        con.readTimeout = TimeUnit.SECONDS.toMillis(1).toInt()
        con.requestMethod = "GET"
        con.setRequestProperty("Content-Type", "application/json")
        con.setRequestProperty("Accept", "application/json")
        con.doOutput = true
        val reader = BufferedReader(
            InputStreamReader(con.inputStream)
        )
        var inputLine: String?
        val response = StringBuffer()
        while (reader.readLine().also { inputLine = it } != null) {
            response.append(inputLine)
        }
        reader.close()
        return response.toString()
    }

    private fun parseSolcReleaseInfo(json: String) : List<SolcRelease> {
        val releases: List<SolcReleaseInfo> = mapper.readValue(json)
        val startsWithDigitRegex = Regex("^\\d")
        return releases.map { it.toSolcRelease() }
            .filter { startsWithDigitRegex.containsMatchIn(it.version) }
            .sortedBy { it.version }
    }

    fun getSolcReleases(): List<SolcRelease> {
        val versionsFile = Paths.get(System.getProperty("user.home"), directoryPath, "solc", "releases.json").toFile()
        try {
            val releasesResponse = get("https://api.github.com/repos/ethereum/solidity/releases")
            val parsedReleases = parseSolcReleaseInfo(releasesResponse)
            versionsFile.parentFile.mkdirs()
            versionsFile.writeText(mapper.writeValueAsString(parsedReleases))
            return parsedReleases
        } catch (e: Exception) {
            return if (versionsFile.exists()) {
                Json(JsonConfiguration.Stable).parse(SolcRelease.serializer().list, versionsFile.readText())
            } else {
                var defaultReleases = ClassLoader.getSystemResource("releases.json").readText()
                Json(JsonConfiguration.Stable).parse(SolcRelease.serializer().list, defaultReleases)
            }
        }
    }

    fun versionsFromString(input: String): List<String> {
        return Regex("\\s*[\\^<>=~!]{0,3}\\s*(\\d*(\\.?)\\s*){1,3}").findAll(input)
            .filter { it.groupValues[0].isNotBlank() }.map { it.groupValues[0].trim().replace("\\s".toRegex(), "") }
            .toList()
    }

    fun getCompatibleVersions(pragmaRequirement: String, releases: List<SolcRelease>): List<SolcRelease> {
        val requiredVersions = versionsFromString(pragmaRequirement)
        return releases.filter {
            requiredVersions.all(fun(nr: String): Boolean {
                return Version.valueOf(it.version).satisfies(nr) && it.isCompatibleWithOs()
            })
        }
    }

    fun getLatestCompatibleVersion(pragmaRequirement: String?): SolcRelease? {
        val solcReleases = getSolcReleases()
        return if (pragmaRequirement != null) {
            getCompatibleVersions(pragmaRequirement, solcReleases).lastOrNull()
        } else {
            solcReleases.last()
        }
    }
}
