package core

import core.state.GlobalAppState
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.core.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文件上传服务
 */
object FileUploader {
    // 复用ApiClient中统一配置的HttpClient实例
    private val client get() = httpClient

    /**
     * 上传文件
     * @param bytes 文件字节数组
     * @param fileName 文件名
     * @return 上传成功返回文件URL，失败返回null
     */
    suspend fun uploadFile(bytes: ByteArray, fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.submitFormWithBinaryData(
                url = ApiEndpoints.url(ApiEndpoints.FILE_UPLOAD),
                formData = formData {
                    append("file", fileName, ContentType.Application.OctetStream) {
                        writeFully(bytes)
                    }
                }
            ) {
                header("Authorization", "Bearer ${GlobalAppState.currentToken}")
                timeout {
                    requestTimeoutMillis = 30000 // 30秒超时
                }
            }

            if (response.status.isSuccess()) {
                val result = response.body<ApiResponse<String>>()
                result.data
            } else {
                println("File upload failed: ${response.status}")
                null
            }
        } catch (e: Exception) {
            println("File upload error: ${e.message}")
            null
        }
    }

    /**
     * 上传图片
     * @param bytes 图片字节数组
     * @param fileName 文件名
     * @return 上传成功返回图片URL，失败返回null
     */
    suspend fun uploadImage(bytes: ByteArray, fileName: String): String? {
        return uploadFile(bytes, fileName)
    }

    /**
     * 上传头像（使用专门的头像上传接口）
     * @param bytes 头像图片字节数组
     * @param fileName 文件名
     * @return 上传成功返回头像URL，失败返回null
     */
    suspend fun uploadAvatar(bytes: ByteArray, fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.submitFormWithBinaryData(
                url = ApiEndpoints.url(ApiEndpoints.USER_AVATAR_UPLOAD),
                formData = formData {
                    append("file", fileName, ContentType.Image.Any) {
                        writeFully(bytes)
                    }
                }
            ) {
                header("Authorization", "Bearer ${GlobalAppState.currentToken}")
                timeout {
                    requestTimeoutMillis = 30000 // 30秒超时
                }
            }

            if (response.status.isSuccess()) {
                val result = response.body<ApiResponse<String>>()
                result.data
            } else {
                println("Avatar upload failed: ${response.status}")
                null
            }
        } catch (e: Exception) {
            println("Avatar upload error: ${e.message}")
            null
        }
    }
}
