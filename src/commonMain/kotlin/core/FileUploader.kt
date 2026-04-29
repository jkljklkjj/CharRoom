package core

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.core.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * 文件上传服务
 */
object FileUploader {
    private val client = HttpClient {
        followRedirects = true
    }

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
                header("Authorization", "Bearer ${ServerConfig.Token}")
                timeout {
                    requestTimeoutMillis = 30000 // 30秒超时
                }
            }

            if (response.status.isSuccess()) {
                val result = response.body<Map<String, Any>>()
                result["data"] as? String
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
}
