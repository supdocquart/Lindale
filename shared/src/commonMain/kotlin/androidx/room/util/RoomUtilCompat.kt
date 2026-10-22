package androidx.room.util

import androidx.collection.ArrayMap

// Room's KSP output for the JVM target calls this helper, which room-runtime does not ship for the JVM.
// It must keep this package name so the generated DAO code resolves it.

fun <K : Any, V> recursiveFetchArrayMap(
    map: ArrayMap<K, V>,
    isFetch: Boolean,
    fetchBlock: (ArrayMap<K, V>) -> Unit,
) {
    val limit = 999
    val keys = map.keys.toList()
    var index = 0
    while (index < keys.size) {
        val nextIndex = minOf(index + limit, keys.size)
        val subMap = ArrayMap<K, V>()
        for (i in index until nextIndex) {
            val key = keys[i]
            map[key]?.let { subMap[key] = it }
        }
        fetchBlock(subMap)
        index = nextIndex
    }
}
