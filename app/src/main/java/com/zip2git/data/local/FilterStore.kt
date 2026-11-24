package com.zip2git.data.local

import android.content.Context
import android.content.SharedPreferences
import com.zip2git.data.model.CompareOptions
import com.zip2git.data.model.FilterDecision
import com.zip2git.data.model.SmartFilterRule
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Remembers the Advanced Options so a returning user does not re-enter them:
 * the push mode, the keep-important-files switch, and the Smart File Filter
 * rules.
 *
 * Nothing secret lives here, so plain [SharedPreferences] is enough (the token
 * has its own encrypted store).
 */
class FilterStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var mirrorSync: Boolean
        get() = prefs.getBoolean(KEY_MIRROR, false)
        set(value) = prefs.edit().putBoolean(KEY_MIRROR, value).apply()

    var keepImportantFiles: Boolean
        get() = prefs.getBoolean(KEY_KEEP_IMPORTANT, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_IMPORTANT, value).apply()

    var stripRootFolder: Boolean
        get() = prefs.getBoolean(KEY_STRIP_ROOT, true)
        set(value) = prefs.edit().putBoolean(KEY_STRIP_ROOT, value).apply()

    /** User-defined rules, on top of the built-in defaults. */
    var rules: List<SmartFilterRule>
        get() {
            val raw = prefs.getString(KEY_RULES, null) ?: return defaultRules()
            return runCatching {
                json.decodeFromString(ListSerializer(SmartFilterRule.serializer()), raw)
            }.getOrElse { defaultRules() }
        }
        set(value) {
            val encoded = json.encodeToString(ListSerializer(SmartFilterRule.serializer()), value)
            prefs.edit().putString(KEY_RULES, encoded).apply()
        }

    fun options(): CompareOptions = CompareOptions(
        mirrorSync = mirrorSync,
        filterRules = rules,
        keepImportantFiles = keepImportantFiles,
    )

    companion object {
        private const val PREFS_NAME = "zip2git_filter_prefs"
        private const val KEY_MIRROR = "mirror_sync"
        private const val KEY_KEEP_IMPORTANT = "keep_important"
        private const val KEY_STRIP_ROOT = "strip_root_folder"
        private const val KEY_RULES = "smart_filter_rules"

        /**
         * The starting rule set: the usual build artefacts and editor noise that
         * nobody wants in a repository.
         */
        fun defaultRules(): List<SmartFilterRule> = listOf(
            SmartFilterRule("node_modules/"),
            SmartFilterRule("build/"),
            SmartFilterRule(".gradle/"),
            SmartFilterRule("__MACOSX/"),
            SmartFilterRule("*.log"),
            SmartFilterRule("*.tmp"),
            SmartFilterRule(".DS_Store"),
            SmartFilterRule("Thumbs.db"),
        )
    }
}
