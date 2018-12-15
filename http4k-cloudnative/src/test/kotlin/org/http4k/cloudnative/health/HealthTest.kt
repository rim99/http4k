package org.http4k.cloudnative.health

import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.coroutines.runBlocking
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.I_M_A_TEAPOT
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SERVICE_UNAVAILABLE
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus
import org.http4k.routing.bind
import org.junit.jupiter.api.Test

class HealthTest {
    private val health = Health(extraRoutes = *arrayOf("/other" bind GET to HttpHandler { Response(I_M_A_TEAPOT) }))

    @Test
    fun liveness() = runBlocking {
        assertThat(health(Request(GET, "/liveness")), hasStatus(OK))
    }

    @Test
    fun readiness() = runBlocking {
        assertThat(health(Request(GET, "/readiness")), hasStatus(OK).and(hasBody("success=true")))
    }

    @Test
    fun `extra routes are callable`() = runBlocking {
        assertThat(health(Request(GET, "/other")), hasStatus(I_M_A_TEAPOT))
    }

    @Test
    fun `readiness with extra checks`() = runBlocking {
        assertThat(Health(checks = listOf(check(true, "first"), check(false, "second")))(Request(GET, "/readiness")),
            hasStatus(SERVICE_UNAVAILABLE).and(hasBody("overall=false\nfirst=true\nsecond=false [foobar]")))
    }

    @Test
    fun `readiness continues to run when check fails`() = runBlocking {
        assertThat(Health(checks = listOf(throws("boom"), check(true, "second")))(Request(GET, "/readiness")),
            hasStatus(SERVICE_UNAVAILABLE).and(hasBody("overall=false\nboom=false [foobar]\nsecond=true")))
    }

    private fun check(result: Boolean, name: String): ReadinessCheck = object : ReadinessCheck {
        override fun invoke(): ReadinessCheckResult =
            if (result) Completed(name) else Failed(name, "foobar")

        override val name: String = name
    }

    private fun throws(name: String): ReadinessCheck = object : ReadinessCheck {
        override fun invoke(): ReadinessCheckResult = throw Exception("foobar")

        override val name: String = name
    }
}