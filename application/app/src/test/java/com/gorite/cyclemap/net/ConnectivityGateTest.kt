package com.gorite.cyclemap.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityGateTest {

    @Test
    fun defaultsToDisabledEvenIfOnline() {
        val linkFlow = MutableStateFlow<Link>(Link.OnlineUnmetered)
        val gate = OnlineFeatureGate(linkFlow) // default config: useOnlineAssistance = false

        assertFalse(gate.isOnlineAssistanceEnabled)

        val result = runBlocking {
            gate.execute { "some data" }
        }
        assertTrue(result is FeatureResult.Unavailable)
    }

    @Test
    fun executesWhenEnabledAndUnmetered() {
        val linkFlow = MutableStateFlow<Link>(Link.OnlineUnmetered)
        val gate = OnlineFeatureGate(linkFlow, OnlineFeatureConfig(useOnlineAssistance = true))

        assertTrue(gate.isOnlineAssistanceEnabled)

        val result = runBlocking {
            gate.execute { "traffic-delay:1.1" }
        }
        assertTrue(result is FeatureResult.Success)
        assertEquals("traffic-delay:1.1", (result as FeatureResult.Success).data)
    }

    @Test
    fun blocksMeteredByDefaultWhenMeteredNotAllowed() {
        val linkFlow = MutableStateFlow<Link>(Link.OnlineMetered)
        val gate = OnlineFeatureGate(
            linkFlow,
            OnlineFeatureConfig(useOnlineAssistance = true, allowMeteredConnection = false)
        )

        assertFalse(gate.isOnlineAssistanceEnabled)

        val result = runBlocking {
            gate.execute { "traffic" }
        }
        assertTrue(result is FeatureResult.Unavailable)
    }

    @Test
    fun allowsMeteredWhenExplicitlyPermitted() {
        val linkFlow = MutableStateFlow<Link>(Link.OnlineMetered)
        val gate = OnlineFeatureGate(
            linkFlow,
            OnlineFeatureConfig(useOnlineAssistance = true, allowMeteredConnection = true)
        )

        assertTrue(gate.isOnlineAssistanceEnabled)

        val result = runBlocking {
            gate.execute { "traffic" }
        }
        assertTrue(result is FeatureResult.Success)
        assertEquals("traffic", (result as FeatureResult.Success).data)
    }

    @Test
    fun fallsBackOnTimeout() {
        val linkFlow = MutableStateFlow<Link>(Link.OnlineUnmetered)
        val gate = OnlineFeatureGate(linkFlow, OnlineFeatureConfig(useOnlineAssistance = true))

        val result = runBlocking {
            gate.execute(timeoutMs = 50L) {
                kotlinx.coroutines.delay(200L)
                "delayed response"
            }
        }
        assertTrue(result is FeatureResult.Unavailable)
        assertTrue((result as FeatureResult.Unavailable).reason.contains("timed out"))
    }

    @Test
    fun handlesExceptionGracefullyWithoutCrashing() {
        val linkFlow = MutableStateFlow<Link>(Link.OnlineUnmetered)
        val gate = OnlineFeatureGate(linkFlow, OnlineFeatureConfig(useOnlineAssistance = true))

        val result = runBlocking {
            gate.execute<String> {
                throw java.io.IOException("Network unreachable")
            }
        }
        assertTrue(result is FeatureResult.Unavailable)
        assertTrue((result as FeatureResult.Unavailable).reason.contains("Network unreachable"))
    }
}
