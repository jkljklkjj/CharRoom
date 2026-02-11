package core

// Public ApiUnwrap used across common and platform code
data class ApiUnwrap(
    val hasEnvelope: Boolean,
    val success: Boolean,
    val dataJson: String?,
    val message: String?
)

