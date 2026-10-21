package android.database

import java.io.Closeable

interface Cursor : Closeable {
    fun moveToFirst(): Boolean = false
    fun getColumnIndex(columnName: String): Int = -1
    fun getString(columnIndex: Int): String? = null
    fun getLong(columnIndex: Int): Long = 0L
    fun getInt(columnIndex: Int): Int = 0
    override fun close() {}
}

/** Single-row cursor over named values, used to describe local files. */
class MapCursor(private val row: Map<String, Any?>) : Cursor {
    private val columns = row.keys.toList()
    private var positioned = false

    override fun moveToFirst(): Boolean {
        positioned = true
        return true
    }

    override fun getColumnIndex(columnName: String): Int = columns.indexOf(columnName)

    private fun value(index: Int): Any? = if (positioned) columns.getOrNull(index)?.let { row[it] } else null

    override fun getString(columnIndex: Int): String? = value(columnIndex)?.toString()
    override fun getLong(columnIndex: Int): Long = (value(columnIndex) as? Number)?.toLong() ?: 0L
    override fun getInt(columnIndex: Int): Int = (value(columnIndex) as? Number)?.toInt() ?: 0
}
