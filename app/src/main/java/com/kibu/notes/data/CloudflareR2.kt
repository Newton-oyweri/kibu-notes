package com.kibu.notes.data

import android.util.Log
import com.kibu.notes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Cloudflare R2 uploader (S3-compatible, SigV4).
 * All config comes from local.properties -> BuildConfig. Nothing is hardcoded here.
 */
object CloudflareR2 {

    private const val ACCOUNT_ID = BuildConfig.R2_ACCOUNT_ID
    private const val ACCESS_KEY_ID = BuildConfig.R2_ACCESS_KEY_ID
    private const val SECRET_ACCESS_KEY = BuildConfig.R2_SECRET_ACCESS_KEY
    private const val BUCKET_NAME = BuildConfig.R2_BUCKET

    const val PUBLIC_URL = BuildConfig.R2_PUBLIC_URL

    private const val HOST = "$ACCOUNT_ID.r2.cloudflarestorage.com"
    private const val ENDPOINT = "https://$HOST"

    suspend fun uploadFile(
        objectKey: String,
        fileBytes: ByteArray,
        contentType: String = "application/pdf"
    ): String = withContext(Dispatchers.IO) {
        check(
            ACCOUNT_ID.isNotBlank() && ACCESS_KEY_ID.isNotBlank() &&
                    SECRET_ACCESS_KEY.isNotBlank() && BUCKET_NAME.isNotBlank()
        ) { "R2 config missing: set r2.* values in local.properties" }

        // Every path segment must be URI-encoded (spaces, brackets, etc.),
        // and the SAME encoded path is used for signing and for the request.
        val encodedKey = objectKey.removePrefix("/").split("/").joinToString("/") { uriEncode(it) }
        val canonicalUri = "/$BUCKET_NAME/$encodedKey"
        val urlStr = "$ENDPOINT$canonicalUri"

        val now = Date()
        val amzDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now)
        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now)
        val region = "auto"
        val service = "s3"

        val contentSha256 = sha256Hex(fileBytes)
        val signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date"

        val canonicalRequest = listOf(
            "PUT",
            canonicalUri,
            "",
            "content-type:$contentType",
            "host:$HOST",
            "x-amz-content-sha256:$contentSha256",
            "x-amz-date:$amzDate",
            "",
            signedHeaders,
            contentSha256
        ).joinToString("\n")

        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))
        ).joinToString("\n")

        val kSecret = ("AWS4" + SECRET_ACCESS_KEY).toByteArray(Charsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        val kSigning = hmacSha256(kService, "aws4_request")
        val signature = bytesToHex(hmacSha256(kSigning, stringToSign))

        val authHeader = "AWS4-HMAC-SHA256 Credential=$ACCESS_KEY_ID/$credentialScope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature"

        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 60000
            setFixedLengthStreamingMode(fileBytes.size)
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("x-amz-date", amzDate)
            setRequestProperty("x-amz-content-sha256", contentSha256)
            setRequestProperty("Authorization", authHeader)
        }

        try {
            conn.outputStream.use { it.write(fileBytes) }

            val code = conn.responseCode
            if (code in 200..299) {
                "$PUBLIC_URL/$encodedKey"
            } else {
                val errText = conn.errorStream?.use { it.bufferedReader().readText() }.orEmpty()
                runCatching {
                    Log.e("CloudflareR2", "Upload failed with HTTP $code: ${conn.responseMessage}\n$errText")
                }.onFailure {
                    println("CloudflareR2: Upload failed with HTTP $code: ${conn.responseMessage}\n$errText")
                }
                throw IOException("Cloudflare R2 upload failed with HTTP $code: ${conn.responseMessage} ($errText)")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** RFC 3986 encoding as required by SigV4 (space -> %20, keeps A-Z a-z 0-9 - _ . ~). */
    private fun uriEncode(value: String): String = buildString {
        for (b in value.toByteArray(Charsets.UTF_8)) {
            val i = b.toInt() and 0xff
            val ch = i.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                ch == '-' || ch == '_' || ch == '.' || ch == '~'
            ) {
                append(ch)
            } else {
                append("%%%02X".format(i))
            }
        }
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return bytesToHex(digest.digest(data))
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}