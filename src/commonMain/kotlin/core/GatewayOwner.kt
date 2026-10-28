package core

import kotlinx.serialization.Serializable
import java.net.URI

@Serializable data class GatewayEndpoint(val gatewayId: String = "", val endpoint: String = "")
@Serializable data class GatewayOwner(val ownerGatewayId: String? = null, val ownerEndpoint: String? = null,
    val gateways: List<GatewayEndpoint> = emptyList())

fun parseGatewayEndpoint(endpoint: String): Pair<String, Int> {
    val uri = URI(if (endpoint.contains("://")) endpoint else "quic://$endpoint")
    require(uri.scheme in listOf("quic", "https") && !uri.host.isNullOrBlank() && uri.port in 1..65535 &&
        uri.userInfo == null && uri.rawQuery == null && uri.fragment == null && uri.path.orEmpty() in listOf("", "/")) {
        "Invalid gateway endpoint"
    }
    return uri.host.removePrefix("[").removeSuffix("]") to uri.port
}

suspend fun discoverGatewayOwner(token: String, fallbackHost: String, fallbackPort: Int): Pair<String, Int> {
    val result = sendRequest<GatewayOwner>("/cluster/owner", token = token, timeoutSeconds = 10)
    check(result.isSuccess) { "Gateway owner discovery failed" }
    val owner = result.data ?: error("Missing gateway discovery response")
    if (!owner.ownerEndpoint.isNullOrBlank()) return parseGatewayEndpoint(owner.ownerEndpoint)
    check(owner.gateways.size <= 1) { "Owner gateway endpoint is not configured" }
    return fallbackHost to fallbackPort // single-node development may omit its advertised endpoint
}

/** Routing changes and server saturation are retriable; neither invalidates credentials. */
fun isTransientGatewayResponse(message: String): Boolean =
    message in setOf("OWNER_REDIRECT", "OWNER_UNAVAILABLE", "SERVER_BUSY")
