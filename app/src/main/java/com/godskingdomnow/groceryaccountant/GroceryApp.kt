package com.godskingdomnow.groceryaccountant

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.godskingdomnow.groceryaccountant.data.AppDatabase
import com.godskingdomnow.groceryaccountant.data.Repository
import com.godskingdomnow.groceryaccountant.sync.SessionStore
import com.godskingdomnow.groceryaccountant.sync.SyncApi
import com.godskingdomnow.groceryaccountant.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Holds the app's single database, sign-in and sync. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.create(app)
    val sessions = SessionStore(app)
    val sync = SyncManager(db, SyncApi(), sessions, scope)
    val repo = Repository(db) { sync.requestSoon() }
    /** Text shared into the app (e.g. a text message) waiting to be imported. */
    val pendingImport = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

class GroceryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // When the app comes to the front, pick up what the family changed (only if signed in).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.scope.launch {
                    if (container.sessions.get().signedIn) container.sync.syncNow()
                }
            }
        })
    }
}
