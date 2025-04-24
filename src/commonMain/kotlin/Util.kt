import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class Util {
    companion object {
        private val objectMapper = jacksonObjectMapper()

        fun jsonToMap(json: String): Map<String, Any> {
            return objectMapper.readValue(json)
        }
    }
}