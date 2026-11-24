package com.zip2git.data.local

import android.content.Context
import com.zip2git.data.model.RecentPushItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Remembers recently pushed projects under `zip2git_history_prefs` for one-tap re-sync. */
class HistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("zip2git_history_prefs", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val items: List<RecentPushItem>
        get() {
            val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
            return runCatching {
                json.decodeFromString(ListSerializer(RecentPushItem.serializer()), raw)
            }.getOrDefault(emptyList())
        }

    /** Inserts [item] at the front, de-duplicating on owner/name/branch/file and capping the list. */
    fun record(item: RecentPushItem) {
        val kept = items.filterNot {
            it.repoOwner == item.repoOwner &&
                it.repoName == item.repoName &&
                it.branch == item.branch &&
                it.fileName == item.fileName
        }
        val updated = (listOf(item) + kept).take(MAX_ITEMS)
        prefs.edit().putString(
            KEY_ITEMS,
            json.encodeToString(ListSerializer(RecentPushItem.serializer()), updated),
        ).apply()
    }

    fun clear() = prefs.edit().remove(KEY_ITEMS).apply()

    private companion object {
        const val KEY_ITEMS = "recent_pushes"
        const val MAX_ITEMS = 20
    }
}
