package com.kibu.notes

import com.kibu.notes.data.CloudflareR2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareR2Test {

    @Test
    fun testCloudflareUpload() {
        runBlocking {
            val testBytes = "Hello R2 Debug".toByteArray(Charsets.UTF_8)
            val objectKey = "test/debug_${System.currentTimeMillis()}.pdf"
            val url = CloudflareR2.uploadFile(objectKey, testBytes, "application/pdf")
            assertTrue(url.startsWith(CloudflareR2.PUBLIC_URL))
        }
    }
}
