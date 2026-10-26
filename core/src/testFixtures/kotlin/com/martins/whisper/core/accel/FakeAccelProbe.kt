package com.martins.whisper.core.accel

class FakeAccelProbe(private val backends: List<BackendInfo>) : AccelProbe {
    override fun probe(): List<BackendInfo> = backends
}
