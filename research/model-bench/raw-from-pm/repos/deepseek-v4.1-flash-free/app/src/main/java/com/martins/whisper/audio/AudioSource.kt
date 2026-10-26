package com.martins.whisper.audio

interface AudioSource {
    fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit)
    fun stop()
    fun release()
}
