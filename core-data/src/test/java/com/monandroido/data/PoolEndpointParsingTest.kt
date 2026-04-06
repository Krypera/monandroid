package com.monandroido.data

import com.google.common.truth.Truth.assertThat
import com.monandroido.data.model.parsePoolEndpoint
import org.junit.Test

class PoolEndpointParsingTest {
    @Test
    fun endpointWithWhitespace_isRejected() {
        assertThat(parsePoolEndpoint("pool example.com:3333")).isNull()
    }

    @Test
    fun unbracketedIpv6Endpoint_isRejected() {
        assertThat(parsePoolEndpoint("2001:db8::1:3333")).isNull()
    }

    @Test
    fun bracketedIpv6Endpoint_isAccepted() {
        val parsed = parsePoolEndpoint("[2001:db8::1]:3333")

        assertThat(parsed).isNotNull()
        assertThat(parsed?.normalizedUrl).isEqualTo("[2001:db8::1]:3333")
    }
}
