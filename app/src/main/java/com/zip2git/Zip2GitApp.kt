package com.zip2git

import android.app.Application
import com.zip2git.data.local.FilterStore
import com.zip2git.data.local.HistoryStore
import com.zip2git.data.local.ThemeStore
import com.zip2git.data.local.TokenStore

/** Process-wide singletons. Kept small; a DI framework is unnecessary here. */
class Zip2GitApp : Application() {

    val tokenStore: TokenStore by lazy { TokenStore(this) }
    val themeStore: ThemeStore by lazy { ThemeStore(this) }
    val historyStore: HistoryStore by lazy { HistoryStore(this) }
    val filterStore: FilterStore by lazy { FilterStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: Zip2GitApp
            private set
    }
}
