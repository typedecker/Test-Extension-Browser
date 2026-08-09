package com.browserextensions.browserwithextensions

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebExtensionStore
import java.io.File

class BrowserApplication : Application() {

    companion object {
        private const val EXTENSIONS_DIR_NAME = "extensions"
        
        @Volatile
        private var instance: BrowserApplication? = null
        
        fun getInstance(): BrowserApplication = 
            instance ?: synchronized(this) { instance ?: BrowserApplication().also { instance = it } }
    }

    lateinit var geckoRuntime: GeckoRuntime
        private set
    
    lateinit var webExtensionStore: WebExtensionStore
        private set
    
    lateinit var extensionsManager: GeckoRuntimeExtensionsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        initGeckoRuntime()
    }

    private fun initGeckoRuntime() {
        // Create GeckoRuntime with proper configuration for extension support
        val runtimeBuilder = GeckoRuntime.Builder(this)
            .setUserAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .setAppInfo(
                appName = "ExtensionBrowser",
                appVersion = "1.0",
                os = "Android",
                platformVersion = android.os.Build.VERSION.RELEASE,
                identifier = "geckoview",
                extensionID = "{extension-browser}"
            )

        geckoRuntime = runtimeBuilder.build()
        
        // Get the WebExtensionStore from the runtime for managing extensions
        webExtensionStore = geckoRuntime.webExtensionStore
        
        // Initialize our custom extensions manager
        extensionsManager = GeckoRuntimeExtensionsManager(this, webExtensionStore)

        // Initialize the runtime and load extensions when ready
        if (geckoRuntime.state == GeckoRuntime.State.UNINITIALIZED || 
            geckoRuntime.state == GeckoRuntime.State.INITIALIZING) {
            geckoRuntime.initialize().then(
                onSuccess = {
                    android.util.Log.d("BrowserApp", "GeckoRuntime initialized successfully")
                    // Load all previously installed extensions
                    extensionsManager.loadAllExtensions()
                },
                onError = { error ->
                    android.util.Log.e("BrowserApp", "Failed to initialize GeckoRuntime", error)
                }
            )
        } else {
            // Already initialized, load extensions immediately
            extensionsManager.loadAllExtensions()
        }
    }

    fun getExtensionsDirectory(): File {
        return File(filesDir, EXTENSIONS_DIR_NAME)
    }

    /**
     * Get the list of all installed extensions with their details.
     */
    fun getInstalledExtensions(): List<WebExtensionStore.Extension> {
        return webExtensionStore.list()
    }

    override fun onTerminate() {
        geckoRuntime.shutdown().await()
        super.onTerminate()
    }
}
