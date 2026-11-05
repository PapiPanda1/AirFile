package com.airfile.tv.net

object UploadUrl {
    /** URL encoded in the QR code. The token is URL-safe, so no escaping is needed. */
    fun build(ip: String, port: Int, token: String): String = "http://$ip:$port/?t=$token"
}
