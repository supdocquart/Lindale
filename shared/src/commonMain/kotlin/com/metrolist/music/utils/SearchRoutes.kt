/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource


fun encodeURLPathPart(s: String): String = s // stub

object SearchRoutes {
    const val ROUTE = "search/{query}"

    // Prefix query payloads so a literal "null" search is not confused with
    // an absent or undefined Navigation argument.
    private const val QUERY_ROUTE_PREFIX = "__metrolist_search_query__"

    // Encode the prefixed query as a route path segment before navigating.
    fun resultRoute(query: String): String =
        "search/${encodeURLPathPart(QUERY_ROUTE_PREFIX + query)}"

    // Navigation already decodes path segments once, so only strip the prefix.
    // Decoding again would turn literal input like "%2F" into "/".
    fun decodeQuery(rawQuery: String): String =
        rawQuery.removePrefix(QUERY_ROUTE_PREFIX)
}


