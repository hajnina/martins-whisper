package com.martins.whisper.core.asr

import com.martins.whisper.core.ModelState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe lazy model lifecycle.
 *
 * - Loading and use are serialized by one fair lock (the native context must never be used
 *   concurrently).
 * - [maybeUnload] frees the model after [idleMs] without use; the service is expected to call it
 *   from its 15 s watchdog.
 * - A loader failure sets [state] to ERROR but does not poison the manager: the next call retries.
 * - [switchModel] immediately unloads the old model and installs a new loader.
 *
 * State listeners are invoked while the internal lock is held. They must be fast and must not call
 * back into this manager.
 */
class ModelManager<T : Any>(
    loader: () -> T,
    private val closer: (T) -> Unit,
    private val clock: () -> Long,
    private val idleMs: Long = DEFAULT_IDLE_MS,
    private val stateListener: ((ModelState) -> Unit)? = null,
) {
    init {
        require(idleMs >= 0) { "idleMs must be >= 0" }
    }

    private val lock = ReentrantLock(true)
    private val listeners = CopyOnWriteArrayList<(ModelState) -> Unit>()

    private var loader: () -> T = loader
    private var instance: T? = null
    private var lastUsedAtMs: Long = 0L

    @Volatile
    private var currentState: ModelState = ModelState.UNLOADED

    val state: ModelState
        get() = currentState

    init {
        stateListener?.let { listeners += it }
    }

    fun addStateListener(listener: (ModelState) -> Unit) {
        listeners += listener
    }

    fun removeStateListener(listener: (ModelState) -> Unit) {
        listeners -= listener
    }

    fun getOrLoad(): T = lock.withLock {
        val model = instance
        if (model != null) return model
        loadLocked()
    }

    fun markUsed() = lock.withLock {
        if (instance != null) lastUsedAtMs = clock()
    }

    /** Returns true if the idle model was freed. */
    fun maybeUnload(): Boolean = lock.withLock {
        val model = instance ?: return false
        if (clock() - lastUsedAtMs < idleMs) return false
        unloadLocked(model)
        true
    }

    fun unloadNow() = lock.withLock {
        instance?.let { unloadLocked(it) }
    }

    /**
     * Runs [block] with the loaded model while holding the ASR lock. Usage is recorded even if
     * [block] throws.
     */
    fun <R> withModel(block: (T) -> R): R = lock.withLock {
        val model = instance ?: loadLocked()
        try {
            block(model)
        } finally {
            lastUsedAtMs = clock()
        }
    }

    /** Unloads the old model immediately; the new loader is used lazily on the next request. */
    fun switchModel(newLoader: () -> T) = lock.withLock {
        instance?.let { unloadLocked(it) }
        loader = newLoader
    }

    fun close() = unloadNow()

    private fun loadLocked(): T {
        instance?.let { return it }
        transitionLocked(ModelState.LOADING)
        val loaded = try {
            loader()
        } catch (t: Throwable) {
            transitionLocked(ModelState.ERROR)
            throw t
        }
        instance = loaded
        lastUsedAtMs = clock()
        transitionLocked(ModelState.READY)
        return loaded
    }

    private fun unloadLocked(model: T) {
        instance = null
        lastUsedAtMs = 0L
        runCatching { closer(model) }
        transitionLocked(ModelState.UNLOADED)
    }

    private fun transitionLocked(newState: ModelState) {
        if (currentState == newState) return
        currentState = newState
        listeners.forEach { listener -> runCatching { listener(newState) } }
    }

    companion object {
        const val DEFAULT_IDLE_MS: Long = 300_000L
        const val WATCHDOG_INTERVAL_MS: Long = 15_000L
    }
}
