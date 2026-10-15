package com.optiscan.app

import com.google.gson.Gson
import com.optiscan.app.data.models.AnalysisResponse
import com.optiscan.app.data.models.HealthResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that Android client data models exactly match the backend JSON contract.
 */
class ModelContractTest {

    private val gson = Gson()

    @Test
    fun testHealthResponseContract() {
        val json = """
            {
              "status": "ok",
              "service": "optiscan-backend"
            }
        """.trimIndent()

        val response = gson.fromJson(json, HealthResponse::class.java)
        assertEquals("ok", response.status)
        assertEquals("optiscan-backend", response.service)
    }

    @Test
    fun testAnalyzeSuccessContract() {
        val json = """
            {
              "status": "success",
              "task": "describe_image",
              "answer": "A person is standing near a doorway.",
              "should_recapture": false,
              "reason": null
            }
        """.trimIndent()

        val response = gson.fromJson(json, AnalysisResponse::class.java)
        assertEquals("success", response.status)
        assertEquals("describe_image", response.task)
        assertEquals("A person is standing near a doorway.", response.answer)
        assertFalse(response.shouldRecapture)
        assertNull(response.reason)
    }

    @Test
    fun testAnalyzeRecaptureContract() {
        val json = """
            {
              "status": "recapture",
              "task": "describe_image",
              "answer": null,
              "should_recapture": true,
              "reason": "Image is too blurry. Please hold steady and recapture."
            }
        """.trimIndent()

        val response = gson.fromJson(json, AnalysisResponse::class.java)
        assertEquals("recapture", response.status)
        assertEquals("describe_image", response.task)
        assertNull(response.answer)
        assertTrue(response.shouldRecapture)
        assertEquals("Image is too blurry. Please hold steady and recapture.", response.reason)
    }

    @Test
    fun testAnalyzeHindiResponseWithMedicationSafetyContract() {
        val json = """
            {
              "status": "success",
              "task": "read_text",
              "answer": "दवा का नाम Paracetamol 500mg है। खुराक: 2 tablets हर 6 घंटे में।",
              "should_recapture": false,
              "reason": null
            }
        """.trimIndent()

        val response = gson.fromJson(json, AnalysisResponse::class.java)
        assertEquals("success", response.status)
        assertEquals("read_text", response.task)
        assertEquals("दवा का नाम Paracetamol 500mg है। खुराक: 2 tablets हर 6 घंटे में।", response.answer)
        assertFalse(response.shouldRecapture)
        assertNull(response.reason)
    }

    @Test
    fun testNetworkConfigUrlValidation() {
        assertTrue(com.optiscan.app.utils.NetworkConfig.isValidUrl("https://api.optiscan.ai/"))
        assertTrue(com.optiscan.app.utils.NetworkConfig.isValidUrl("http://192.168.1.15:8000/"))
        assertFalse(com.optiscan.app.utils.NetworkConfig.isValidUrl(""))
        assertFalse(com.optiscan.app.utils.NetworkConfig.isValidUrl("ftp://invalid"))
        assertFalse(com.optiscan.app.utils.NetworkConfig.isValidUrl("just-text"))
    }

    @Test
    fun testNetworkConfigHostedUrlRejectsLoopbacks() {
        assertTrue(com.optiscan.app.utils.NetworkConfig.isValidHostedUrl("https://api.optiscan.ai/"))
        assertTrue(com.optiscan.app.utils.NetworkConfig.isValidHostedUrl("https://my-backend.onrender.com/"))
        assertFalse(com.optiscan.app.utils.NetworkConfig.isValidHostedUrl("http://127.0.0.1:8000/"))
        assertFalse(com.optiscan.app.utils.NetworkConfig.isValidHostedUrl("http://localhost:8000/"))
        assertFalse(com.optiscan.app.utils.NetworkConfig.isValidHostedUrl("http://10.0.2.2:8000/"))
    }

    @Test
    fun testNetworkConfigUrlNormalization() {
        assertEquals("https://api.optiscan.ai/", com.optiscan.app.utils.NetworkConfig.normalizeUrl("https://api.optiscan.ai"))
        assertEquals("https://api.optiscan.ai/", com.optiscan.app.utils.NetworkConfig.normalizeUrl("https://api.optiscan.ai/"))
        assertEquals("", com.optiscan.app.utils.NetworkConfig.normalizeUrl("   "))
    }

    @Test
    fun testNetworkConfigLocalAddressDetection() {
        assertTrue(com.optiscan.app.utils.NetworkConfig.isLocalAddress("http://10.0.2.2:8000/"))
        assertTrue(com.optiscan.app.utils.NetworkConfig.isLocalAddress("http://127.0.0.1:8000/"))
        assertTrue(com.optiscan.app.utils.NetworkConfig.isLocalAddress("http://localhost:8000/"))
        assertTrue(com.optiscan.app.utils.NetworkConfig.isLocalAddress("http://192.168.1.50:8000/"))
        assertTrue(com.optiscan.app.utils.NetworkConfig.isLocalAddress("http://10.0.0.12:8000/"))
        assertFalse(com.optiscan.app.utils.NetworkConfig.isLocalAddress("https://api.optiscan.ai/"))
    }

    @Test
    fun testNetworkConfigHostedUrlRequiresHttps() {
        // Hosted production URLs must require HTTPS and not be localhost/loopback
        assertTrue(com.optiscan.app.utils.NetworkConfig.isValidHostedUrl("https://api.optiscan.ai/"))
        assertFalse(com.optiscan.app.utils.NetworkConfig.isValidHostedUrl("http://unencrypted-domain.com/"))
        assertFalse(com.optiscan.app.utils.NetworkConfig.isValidHostedUrl("http://192.168.1.100:8000/"))
        assertFalse(com.optiscan.app.utils.NetworkConfig.isValidHostedUrl("http://10.0.2.2:8000/"))
        assertFalse(com.optiscan.app.utils.NetworkConfig.isValidHostedUrl(""))
    }

    @Test
    fun testMockDataSimulatedMarkersPresent() {
        assertTrue(com.optiscan.app.data.mock.MockData.SAMPLE_PRODUCT_LABEL.startsWith("[Simulated Label - Mock Mode]"))
        assertTrue(com.optiscan.app.data.mock.MockData.SAMPLE_SCENE_DESCRIPTION.startsWith("[Simulated Description - Mock Mode]"))
        val enAnswer = com.optiscan.app.data.mock.MockData.answerQuestion("where is the door", language = "en")
        assertTrue(enAnswer.startsWith("[Simulated Answer - Mock Mode]"))
        val hiAnswer = com.optiscan.app.data.mock.MockData.answerQuestion("दरवाजा कहाँ है", language = "hi")
        assertTrue(hiAnswer.startsWith("[अनुकरण उत्तर - मॉक मोड]"))
    }
}
