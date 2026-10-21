package android.content

interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getLong(key: String, defValue: Long): Long
    fun getInt(key: String, defValue: Int): Int
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putLong(key: String, value: Long): Editor
        fun putInt(key: String, value: Int): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}

object SimpleSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (data[key] as? Number)?.toLong() ?: defValue
    override fun getInt(key: String, defValue: Int): Int = (data[key] as? Number)?.toInt() ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = SimpleEditor()

    class SimpleEditor : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            data[key] = value
            return this
        }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            data[key] = value
            return this
        }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            data[key] = value
            return this
        }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            data[key] = value
            return this
        }
        override fun remove(key: String): SharedPreferences.Editor {
            data.remove(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            data.clear()
            return this
        }
        override fun apply() {}
        override fun commit(): Boolean = true
    }
}

/** Persists each preferences name to its own properties file so values survive restarts. */
class FileSharedPreferences(private val file: java.io.File) : SharedPreferences {
    private val lock = Any()
    private val data: MutableMap<String, Any> = load()

    private fun load(): MutableMap<String, Any> {
        val map = mutableMapOf<String, Any>()
        runCatching {
            if (!file.exists()) return@runCatching
            val props = java.util.Properties()
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
        }.onFailure { timber.log.Timber.e(it, "SharedPreferences load failed: ${file.name}") }
        return map
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val props = java.util.Properties()
            for ((key, value) in data) {
                props[key] = when (value) {
                    is Long -> "l:$value"
                    is Int -> "i:$value"
                    is Boolean -> "b:$value"
                    else -> "s:$value"
                }
            }
            val tmp = java.io.File(file.path + ".tmp")
            tmp.outputStream().use { props.store(it, null) }
            try {
                java.nio.file.Files.move(
                    tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { timber.log.Timber.e(it, "SharedPreferences save failed: ${file.name}") }
    }

    override fun getString(key: String, defValue: String?): String? = synchronized(lock) { data[key] as? String ?: defValue }
    override fun getLong(key: String, defValue: Long): Long = synchronized(lock) { (data[key] as? Number)?.toLong() ?: defValue }
    override fun getInt(key: String, defValue: Int): Int = synchronized(lock) { (data[key] as? Number)?.toInt() ?: defValue }
    override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(lock) { data[key] as? Boolean ?: defValue }
    override fun contains(key: String): Boolean = synchronized(lock) { data.containsKey(key) }
    override fun edit(): SharedPreferences.Editor = FileEditor()

    private inner class FileEditor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clearAll = true }

        override fun apply() {
            commit()
        }

        override fun commit(): Boolean = synchronized(lock) {
            if (clearAll) data.clear()
            for ((key, value) in changes) {
                if (value == null) data.remove(key) else data[key] = value
            }
            save()
            true
        }
    }

    companion object {
        private val instances = java.util.concurrent.ConcurrentHashMap<String, FileSharedPreferences>()

        fun get(dir: java.io.File, name: String): FileSharedPreferences =
            instances.getOrPut(name) { FileSharedPreferences(java.io.File(dir, "shared_prefs/$name.properties")) }
    }
}
