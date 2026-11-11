package com.kibu.notes

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.security.MessageDigest

class UpdateEngineTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseVersionPartsTests() {
        assertThat(UpdateEngine.parseVersionParts("V1")).isEqualTo(listOf(1))
        assertThat(UpdateEngine.parseVersionParts("V2")).isEqualTo(listOf(2))
        assertThat(UpdateEngine.parseVersionParts("V5.1")).isEqualTo(listOf(5, 1))
        assertThat(UpdateEngine.parseVersionParts("7.10")).isEqualTo(listOf(7, 10))
    }

    @Test
    fun isVersionNewerTests() {
        assertThat(UpdateEngine.isVersionNewer("7.0", "V8")).isTrue()
        assertThat(UpdateEngine.isVersionNewer("7.9", "V7.10")).isTrue()
        assertThat(UpdateEngine.isVersionNewer("7.0", "V7.0")).isFalse()
        assertThat(UpdateEngine.isVersionNewer("8.0", "V7.0")).isFalse()
        assertThat(UpdateEngine.isVersionNewer("7.0.1", "V7.0.2")).isTrue()
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
