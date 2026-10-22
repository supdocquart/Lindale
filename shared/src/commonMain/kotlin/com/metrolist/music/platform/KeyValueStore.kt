package com.metrolist.music.platform

import timber.log.Timber
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/** Small typed key/value file store (one .properties file per name) for data kept outside DataStore. */
class KeyValueStore private constructor(private val file: File) {
    private val lock = Any()
    private val data: MutableMap<String, Any> = load()

    private fun load(): MutableMap<String, Any> {
        val map = mutableMapOf<String, Any>()
        runCatching {
            if (!file.exists()) return@runCatching
            val props = Properties()
            file.inputStream().use { props.load(it) }
            for (key in props.stringPropertyNames()) {
                val raw = props.getProperty(key) ?: continue
                val value = raw.substringAfter(':')
                map[key] = when (raw.substringBefore(':')) {
                    "l" -> value.toLongOrNull() ?: continue
                    "i" -> value.toIntOrNull() ?: continue
                    "b" -> value.toBoolean()
                    else -> value
                }
            }
        }.onFailure { Timber.e(it, "KeyValueStore load failed: ${file.name}") }
        return map
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val props = Properties()
            for ((key, value) in data) {
                props[key] = when (value) {
                    is Long -> "l:$value"
                    is Int -> "i:$value"
                    is Boolean -> "b:$value"
                    else -> "s:$value"
                }
            }
            val tmp = File(file.path + ".tmp")
            tmp.outputStream().use { props.store(it, null) }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { Timber.e(it, "KeyValueStore save failed: ${file.name}") }
    }

    fun getString(key: String, defValue: String?): String? = synchronized(lock) { data[key] as? String ?: defValue }
    fun getLong(key: String, defValue: Long): Long = synchronized(lock) { (data[key] as? Number)?.toLong() ?: defValue }
    fun getInt(key: String, defValue: Int): Int = synchronized(lock) { (data[key] as? Number)?.toInt() ?: defValue }
    fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(lock) { data[key] as? Boolean ?: defValue }
    fun contains(key: String): Boolean = synchronized(lock) { data.containsKey(key) }

    fun edit(): Editor = Editor()

    /** Applies the changes made in [action] and writes the file once. */
    inline fun edit(action: Editor.() -> Unit) {
        edit().apply(action).commit()
    }

    inner class Editor internal constructor() {
        private val changes = mutableMapOf<String, Any?>()
        private var clearAll = false

        fun putString(key: String, value: String?) = apply { changes[key] = value }
        fun putLong(key: String, value: Long) = apply { changes[key] = value }
        fun putInt(key: String, value: Int) = apply { changes[key] = value }
        fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        fun remove(key: String) = apply { changes[key] = null }
        fun clear() = apply { clearAll = true }

        fun commit(): Boolean = synchronized(lock) {
            if (clearAll) data.clear()
            for ((key, value) in changes) {
                if (value == null) data.remove(key) else data[key] = value
            }
            save()
            true
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, KeyValueStore>()

        // Directory name kept so existing tokens and caches stay readable.
        fun get(name: String): KeyValueStore =
            instances.getOrPut(name) { KeyValueStore(File(DesktopFiles.dataDir, "shared_prefs/$name.properties")) }
    }
}
