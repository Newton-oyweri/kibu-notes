package com.kibu.notes.data

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

object CloudflareR2 {

    const val ACCOUNT_ID = "024325b360fb284b50c4f97652d48f78"
    const val ACCESS_KEY_ID = "ad6bfa668cde09ca1f0e797803f869b0"
    const val SECRET_ACCESS_KEY = "094e611c544c51a0b289b81b74fa623c467611c2d01a06a9b21bd56519a2443c"
    const val BUCKET_NAME = "kibu-notes"
    const val ENDPOINT = "https://024325b360fb284b50c4f97652d48f78.r2.cloudflarestorage.com"
    const val PUBLIC_URL = "https://pub-937848871caa438bb7b53c74c18ed261.r2.dev"

    suspend fun uploadFile(
        objectKey: String,
        fileBytes: ByteArray,
        contentType: String = "application/pdf"
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = objectKey.removePrefix("/")
        val urlStr = "$ENDPOINT/$BUCKET_NAME/$cleanKey"
        val host = "$ACCOUNT_ID.r2.cloudflarestorage.com"

        val now = Date()
        val isoFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val amzDate = isoFormat.format(now)
        val dateStamp = dateFormat.format(now)
        val region = "auto"
        val service = "s3"

        val contentSha256 = sha256Hex(fileBytes)

        val canonicalRequest = listOf(
            "PUT",
            "/$BUCKET_NAME/$cleanKey",
            "",
            "content-type:$contentType",
            "host:$host",
            "x-amz-content-sha256:$contentSha256",
            "x-amz-date:$amzDate",
            "",
            "content-type;host;x-amz-content-sha256;x-amz-date",
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

        val authHeader = "AWS4-HMAC-SHA256 Credential=$ACCESS_KEY_ID/$credentialScope, SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date, Signature=$signature"

        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 60000
            setRequestProperty("Host", host)
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("x-amz-date", amzDate)
            setRequestProperty("x-amz-content-sha256", contentSha256)
            setRequestProperty("Authorization", authHeader)
            setRequestProperty("Content-Length", fileBytes.size.toString())
        }

        conn.outputStream.use { it.write(fileBytes) }

        val code = conn.responseCode
        if (code in 200..299) {
            "$PUBLIC_URL/$cleanKey"
        } else {
            val errText = conn.errorStream?.use { it.bufferedReader().readText() }.orEmpty()
            throw IOException("Cloudflare R2 upload failed with HTTP $code: ${conn.responseMessage} ($errText)")
        }
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return bytesToHex(hash)
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
