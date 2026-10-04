package app.mymusclemap.data.auth

/**
 * Release always uses [PRODUCTION]. Debug defaults to [DEBUG_LOOPBACK], which is the backend
 * port from `backend/src/main/resources/application.yml` (`server.port` 8082).
 *
 * Emulator or USB phone, after the backend is running on the computer:
 * `adb reverse tcp:8082 tcp:8082`
 * The device then opens `http://127.0.0.1:8082` and the host receives it on localhost:8082.
 * PostgreSQL stays on the computer at 127.0.0.1:5433; the phone does not connect to it.
 *
 * An emulator can instead set `strict.api.baseUrl=http://10.0.2.2:8082` in `local.properties`.
 * That alias is emulator-only. A USB phone should use `adb reverse` and the loopback default.
 */
object StrictBackendUrls {
    const val PRODUCTION = "https://api.strictworkout.eu"
    const val DEBUG_LOOPBACK = "http://127.0.0.1:8082"

    fun requireReleaseUrl(url: String) {
        val normalized = url.trim().lowercase()
        require(normalized.startsWith("https://")) { "Release Strict API URL must use HTTPS." }
        require(!normalized.startsWith("http://")) { "Release Strict API URL must not use cleartext HTTP." }
        require(!normalized.contains("localhost")) { "Release Strict API URL must not target localhost." }
        require(!normalized.contains("127.0.0.1")) { "Release Strict API URL must not target loopback." }
        require(!normalized.contains("10.0.2.2")) { "Release Strict API URL must not target the emulator host alias." }
        require(!normalized.contains("puff")) { "Release Strict API URL must not target Puff." }
    }
}
