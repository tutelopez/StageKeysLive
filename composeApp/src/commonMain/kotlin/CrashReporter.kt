package com.midi.mainstage

expect object CrashReporter {
    fun log(message: String)
    fun recordException(throwable: Throwable, tag: String? = null)
    fun setCustomKey(key: String, value: String)
    fun setCustomKey(key: String, value: Boolean)
    fun setCustomKey(key: String, value: Int)
    fun setUserId(userId: String)
}
