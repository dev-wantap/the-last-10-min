package app.last10min.data

import app.last10min.engine.WireEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Append-only JSONL buffer: events survive ignition off and are sent on the next run (PLAN 6.2). */
class EventStore(private val file: File) {
    private val lock = Any()

    fun append(e: WireEvent) = synchronized(lock) { file.appendText(e.toJson().toString() + "\n") }

    fun pending(limit: Int = 500): List<String> = synchronized(lock) {
        if (!file.exists()) emptyList() else file.readLines().filter { it.isNotBlank() }.take(limit)
    }

    fun drop(count: Int) = synchronized(lock) {
        if (!file.exists()) return@synchronized
        val rest = file.readLines().filter { it.isNotBlank() }.drop(count)
        file.writeText(if (rest.isEmpty()) "" else rest.joinToString("\n", postfix = "\n"))
    }

    fun size(): Int = synchronized(lock) { if (!file.exists()) 0 else file.readLines().count { it.isNotBlank() } }
}

fun WireEvent.toJson(): JSONObject = JSONObject()
    .put("zoneId", zoneId)
    .put("zoneType", zoneType)
    .put("event", event)
    .put("durationSec", durationSec)
    .put("weekday", weekday)
    .put("timeBucket", timeBucket)
    .put("day", day)
    .put("dailyToken", dailyToken)

data class UploadStatus(val pending: Int, val lastResult: String)

class EventUploader(private val http: Http, private val store: EventStore) {
    private val _status = MutableStateFlow(UploadStatus(store.size(), "아직 전송 안 함"))
    val status: StateFlow<UploadStatus> = _status

    fun flush() {
        val batch = store.pending()
        if (batch.isEmpty() || !http.enabled) {
            _status.value = _status.value.copy(pending = store.size())
            return
        }
        val result = try {
            val events = JSONArray()
            batch.forEach { events.put(JSONObject(it)) }
            val res = JSONObject(http.post("/v1/events", JSONObject().put("events", events).toString()))
            store.drop(batch.size)
            val rejected = res.optJSONArray("rejected")?.length() ?: 0
            "전송 ${res.optInt("accepted")}건" + if (rejected > 0) " · 거부 $rejected" else ""
        } catch (e: Exception) {
            "전송 실패 · 다음에 재시도 (${e.message?.take(60)})"
        }
        _status.value = UploadStatus(store.size(), result)
    }
}
