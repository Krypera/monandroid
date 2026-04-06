package com.monandroido.data.model

data class ParsedPoolEndpoint(
    val normalizedUrl: String,
    val tlsOverride: Boolean? = null,
)

fun parsePoolEndpoint(rawValue: String): ParsedPoolEndpoint? {
    val trimmedValue = rawValue.trim()
    if (trimmedValue.isBlank()) return null

    val scheme = SUPPORTED_POOL_SCHEMES.firstOrNull { trimmedValue.startsWith(it.prefix, ignoreCase = true) }
    val normalizedUrl = scheme?.let { trimmedValue.substring(it.prefix.length) } ?: trimmedValue

    if (!normalizedUrl.isValidNormalizedPoolEndpoint()) {
        return null
    }

    return ParsedPoolEndpoint(
        normalizedUrl = normalizedUrl,
        tlsOverride = scheme?.tls,
    )
}

fun formatPoolEndpoint(
    url: String,
    tls: Boolean,
): String {
    val normalizedUrl = parsePoolEndpoint(url)?.normalizedUrl ?: url.trim()
    if (normalizedUrl.isBlank()) return normalizedUrl
    val prefix = if (tls) "stratum+ssl://" else "stratum+tcp://"
    return prefix + normalizedUrl
}

private fun String.isValidNormalizedPoolEndpoint(): Boolean {
    val value = trim()
    if (value.isBlank() || value.contains('/') || value.contains('?') || value.any(Char::isWhitespace)) {
        return false
    }

    return if (value.startsWith("[")) {
        val closingBracketIndex = value.indexOf(']')
        if (closingBracketIndex <= 1) return false
        if (closingBracketIndex == value.lastIndex) return false
        if (value.getOrNull(closingBracketIndex + 1) != ':') return false

        val port = value.substring(closingBracketIndex + 2).toIntOrNull() ?: return false
        port in 1..65535
    } else {
        val separatorIndex = value.lastIndexOf(':')
        if (separatorIndex <= 0 || separatorIndex == value.lastIndex) return false

        val host = value.substring(0, separatorIndex)
        val port = value.substring(separatorIndex + 1).toIntOrNull() ?: return false
        host.isNotBlank() && !host.contains(':') && port in 1..65535
    }
}

private data class PoolScheme(
    val prefix: String,
    val tls: Boolean,
)

private val SUPPORTED_POOL_SCHEMES = listOf(
    PoolScheme(prefix = "stratum+tcp://", tls = false),
    PoolScheme(prefix = "stratum+ssl://", tls = true),
    PoolScheme(prefix = "tcp://", tls = false),
    PoolScheme(prefix = "ssl://", tls = true),
)
