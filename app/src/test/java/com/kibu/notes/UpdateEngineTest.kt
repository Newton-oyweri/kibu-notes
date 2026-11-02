package com.kibu.notes

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.security.MessageDigest

class UpdateEngineTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseVersionNumberTests() {
        assertThat(UpdateEngine.parseVersionNumber("V1")).isEqualTo(1.0)
        assertThat(UpdateEngine.parseVersionNumber("V2")).isEqualTo(2.0)
        assertThat(UpdateEngine.parseVersionNumber("V5")).isEqualTo(5.0)
        assertThat(UpdateEngine.parseVersionNumber("5.0")).isEqualTo(5.0)
        assertThat(UpdateEngine.parseVersionNumber("V5.1")).isEqualTo(5.1)
        assertThat(UpdateEngine.parseVersionNumber("V6")).isEqualTo(6.0)

        assertThat(UpdateEngine.parseVersionNumber("V5") > UpdateEngine.parseVersionNumber("5.0")).isFalse()
        assertThat(UpdateEngine.parseVersionNumber("V6") > UpdateEngine.parseVersionNumber("5.0")).isTrue()
        assertThat(UpdateEngine.parseVersionNumber("V5") > UpdateEngine.parseVersionNumber("2.0")).isTrue()
    }

    @Test
    fun parseVersionCodeTests() {
        assertThat(UpdateEngine.parseVersionCode("V1")).isEqualTo(1)
        assertThat(UpdateEngine.parseVersionCode("V21")).isEqualTo(21)
        assertThat(UpdateEngine.parseVersionCode("v30")).isEqualTo(30)
        assertThat(UpdateEngine.parseVersionCode("V2.1.0")).isEqualTo(210)
        assertThat(UpdateEngine.parseVersionCode("release-v5")).isEqualTo(5)
        assertThat(UpdateEngine.parseVersionCode("invalid")).isEqualTo(0)
    }

    @Test
    fun gitHubReleaseJsonDeserialization() {
        val sampleJson = """
            {
              "tag_name": "V21",
              "name": "Kibu Notes v2.1 Update",
              "body": "Bug fixes and UI improvements",
              "published_at": "2026-03-01T12:00:00Z",
              "assets": [
                {
                  "name": "KibuNotes.apk",
                  "browser_download_url": "https://github.com/Newton-oyweri/kibu-notes/releases/download/V21/KibuNotes.apk",
                  "size": 6405734,
                  "digest": "89082818e032ed7a57a5ea520a987d617410d815c6f7aacf6f9d00b2376eaeab"
                }
              ]
            }
        """.trimIndent()

        val release = json.decodeFromString<GitHubRelease>(sampleJson)
        assertThat(release.tagName).isEqualTo("V21")
        assertThat(release.name).isEqualTo("Kibu Notes v2.1 Update")
        assertThat(release.body).isEqualTo("Bug fixes and UI improvements")
        assertThat(release.assets).hasSize(1)

        val asset = release.assets.first()
        assertThat(asset.name).isEqualTo("KibuNotes.apk")
        assertThat(asset.browserDownloadUrl).contains("KibuNotes.apk")
        assertThat(asset.size).isEqualTo(6405734L)
        assertThat(asset.digest).isEqualTo("89082818e032ed7a57a5ea520a987d617410d815c6f7aacf6f9d00b2376eaeab")
    }

    @Test
    fun sha256ChecksumVerification() {
        val sampleBytes = "KibuNotesAppSampleContent".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(sampleBytes).joinToString("") { "%02x".format(it) }

        assertThat(hash).isNotEmpty()
        assertThat(hash).hasLength(64)
    }
}
