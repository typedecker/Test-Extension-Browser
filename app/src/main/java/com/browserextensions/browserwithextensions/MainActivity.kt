package com.browserextensions.browserwithextensions

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.browserextensions.browserwithextensions.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var geckoSession: GeckoSession? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // Tab management
    private val tabs = mutableListOf<Tab>()
    private var currentTabIndex = -1

    data class Tab(
        val session: GeckoSession,
        val title: String,
        val url: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        initGeckoView()
        setupNavigationButtons()
        setupUrlBar()
        setupFloatingActionButton()
        handleIntent(intent)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_extensions -> {
                    startActivity(Intent(this, ExtensionsActivity::class.java))
                    true
                }
                R.id.action_new_tab -> {
                    createNewTab()
                    true
                }
                R.id.action_close_tab -> {
                    closeCurrentTab()
                    true
                }
                else -> false
            }
        }
    }

    private fun initGeckoView() {
        val app = BrowserApplication.getInstance()
        
        // Wait for runtime to be ready
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                if (app.geckoRuntime.state == GeckoRuntime.State.UNINITIALIZED) {
                    app.geckoRuntime.initialize().await()
                }
            }

            withContext(Dispatchers.Main) {
                // Create the session
                val settings = GeckoSessionSettings().apply {
                    userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                    usePrivateBrowsing = false
                    isFullScreenEnabled = true
                    allowJavaScript = true
                    allowPopups = true
                    allowPlugins = true
                }

                geckoSession = GeckoSession(settings).also { session ->
                    setupSessionListeners(session)
                    
                    // Attach to GeckoView
                    binding.geckoView.apply {
                        runtime = app.geckoRuntime
                        attach(session)
                    }
                }

                tabs.add(Tab(geckoSession!!, "New Tab", null))
                currentTabIndex = 0
                updateTabIndicator()

                // Load default page
                loadUrl("https://www.google.com")
            }
        }
    }

    private fun setupSessionListeners(session: GeckoSession) {
        // Navigation listener
        session.addNavigationListener(object : NavigationListener {
            override fun onLoadRequest(
                session: GeckoSession,
                request: LoadRequest
            ): Boolean {
                val uri = request.uri
                if (uri != null && !isInternalUrl(uri)) {
                    updateUrlBar(uri)
                    return true // Allow the load
                }
                return false // Block internal URLs from being loaded externally
            }

            override fun onLocationChange(session: GeckoSession, url: String?) {
                url?.let { updateUrlBar(it) }
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: SecurityInfo
            ) {
                updateSecurityIndicator(securityInfo)
            }

            override fun onLoadStart(session: GeckoSession) {
                binding.progressBar.isVisible = true
            }

            override fun onLoadStop(session: GeckoSession, response: LoadResponse?) {
                binding.progressBar.isVisible = false
            }

            override fun onReload(session: GeckoSession, flag: Int) {
                binding.refreshButton.isEnabled = true
            }

            override fun onStopRequest(
                session: GeckoSession,
                request: StopRequest,
                newUri: String?
            ) {
                binding.progressBar.isVisible = false
            }
        })

        // Title listener
        session.addProgressListener(object : ProgressListener {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                title?.let {
                    if (currentTabIndex >= 0 && currentTabIndex < tabs.size) {
                        tabs[currentTabIndex] = tabs[currentTabIndex].copy(title = it)
                        updateTabIndicator()
                    }
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {}
        })

        // Tab listener for popup handling
        session.addTabListener(object : TabListener {
            override fun onNewTab(tab: GeckoSession.Tab): GeckoSession? {
                // Create a new tab for popups or link opens
                return createPopupTab(tab)
            }

            override fun onCloseTab(tab: GeckoSession.Tab?) {}
            override fun onSelectTab(tab: GeckoSession.Tab?, selected: Boolean) {}
        })

        // Fullscreen listener
        session.addFullScreenListener(object : FullScreenListener {
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (fullScreen) {
                    enterFullScreen()
                } else {
                    exitFullScreen()
                }
            }

            override fun onExitFullScreenRequested(session: GeckoSession): Boolean = true
        })

        // Content process crash handler
        session.addCrashListener(object : CrashListener {
            override fun onCrashed(session: GeckoSession) {
                Toast.makeText(
                    this@MainActivity,
                    "Content process crashed. Please refresh.",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        // Console message listener (useful for debugging extensions)
        session.addConsoleListener(object : ConsoleListener {
            override fun onConsoleMessage(message: ConsoleMessage) {
                // Log extension-related messages if needed
                android.util.Log.d("ExtensionBrowser", "Console: ${message.message}")
            }
        })

        // Permission request listener
        session.addPermissionDelegate(object : PermissionDelegate {
            override fun onRequest(
                session: GeckoSession,
                host: String,
                permissions: Array<PermissionRequest>
            ) {
                for (permission in permissions) {
                    permission.grant(permission.types, false)
                }
            }
        })

        // Find listener for "Find in Page"
        session.addFindListener(object : FindListener {
            override fun onFindResult(session: GeckoSession, result: FindResult) {}
        })
    }

    private fun isInternalUrl(uri: String): Boolean {
        return uri.startsWith("moz-extension://") ||
                uri.startsWith("chrome://") ||
                uri.startsWith("about:")
    }

    private fun createPopupTab(tab: GeckoSession.Tab): GeckoSession? {
        val app = BrowserApplication.getInstance()
        
        val newSession = GeckoSession(GeckoSessionSettings()).also { session ->
            setupSessionListeners(session)
            
            // Store the tab info
            tabs.add(Tab(session, "New Tab", null))
            currentTabIndex = tabs.size - 1
            updateTabIndicator()
            
            // Attach to a temporary GeckoView or switch to it
            binding.geckoView.attach(session)
            
            // Load the URL from the tab
            tab.uri?.let { loadUrl(it) }
        }
        
        return newSession
    }

    private fun createNewTab() {
        val app = BrowserApplication.getInstance()
        
        val settings = GeckoSessionSettings().apply {
            userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        }
        
        val newSession = GeckoSession(settings).also { session ->
            setupSessionListeners(session)
            
            tabs.add(Tab(session, "New Tab", null))
            currentTabIndex = tabs.size - 1
            updateTabIndicator()
            
            binding.geckoView.attach(session)
            loadUrl("https://www.google.com")
        }
    }

    private fun closeCurrentTab() {
        if (tabs.size <= 1) {
            Toast.makeText(this, "Cannot close the last tab", Toast.LENGTH_SHORT).show()
            return
        }
        
        val session = tabs.removeAt(currentTabIndex)
        session.session.close()
        
        currentTabIndex = minOf(currentTabIndex, tabs.size - 1)
        switchToTab(currentTabIndex)
    }

    private fun switchToTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        
        val tab = tabs[index]
        binding.geckoView.attach(tab.session)
        currentTabIndex = index
        updateUrlBar(tab.url ?: "")
        updateTabIndicator()
    }

    private fun setupNavigationButtons() {
        binding.backButton.setOnClickListener { goBack() }
        binding.forwardButton.setOnClickListener { goForward() }
        binding.refreshButton.setOnClickListener { refresh() }
        binding.homeButton.setOnClickListener { loadUrl("https://www.google.com") }
        
        // Update navigation buttons based on session state
        geckoSession?.addNavigationListener(object : NavigationListener {
            override fun onLoadRequest(session: GeckoSession, request: LoadRequest): Boolean = true
            override fun onLocationChange(session: GeckoSession, url: String?) {}
            override fun onSecurityChange(session: GeckoSession, securityInfo: SecurityInfo) {}
            override fun onLoadStart(session: GeckoSession) {}
            override fun onLoadStop(session: GeckoSession, response: LoadResponse?) {
                updateNavigationButtons()
            }
            override fun onReload(session: GeckoSession, flag: Int) {}
            override fun onStopRequest(session: GeckoSession, request: StopRequest, newUri: String?) {}
        })
    }

    private fun goBack() {
        geckoSession?.goBack() ?: run {
            if (currentTabIndex > 0) switchToTab(currentTabIndex - 1)
        }
    }

    private fun goForward() {
        geckoSession?.goForward() ?: run {
            if (currentTabIndex < tabs.size - 1) switchToTab(currentTabIndex + 1)
        }
    }

    private fun refresh() {
        geckoSession?.reload(false)
    }

    private fun updateNavigationButtons() {
        val session = geckoSession ?: return
        binding.backButton.isEnabled = session.canGoBack()
        binding.forwardButton.isEnabled = session.canGoForward()
    }

    private fun setupUrlBar() {
        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputtype.EditorInfo.IME_ACTION_GO) {
                val url = binding.urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    loadUrl(url)
                    binding.urlInput.clearFocus()
                }
                true
            } else false
        }

        binding.goButton.setOnClickListener {
            val url = binding.urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                loadUrl(url)
                binding.urlInput.clearFocus()
            }
        }

        // Double-click URL to select all
        binding.urlInput.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                binding.urlInput.selectAll()
            }
            false
        }
    }

    private fun loadUrl(url: String) {
        var targetUrl = url
        
        // Add https:// if no scheme is present
        if (!targetUrl.startsWith("http://") && 
            !targetUrl.startsWith("https://") && 
            !targetUrl.startsWith("about:") &&
            !targetUrl.contains(".")) {
            // Treat as search query
            targetUrl = "https://www.google.com/search?q=${Uri.encode(targetUrl)}"
        } else if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            targetUrl = "https://$targetUrl"
        }

        geckoSession?.loadUri(targetUrl)
        
        // Update current tab URL
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size) {
            tabs[currentTabIndex] = tabs[currentTabIndex].copy(url = targetUrl)
        }
    }

    private fun updateUrlBar(url: String?) {
        url?.let {
            binding.urlInput.setText(it)
            
            // Update current tab URL
            if (currentTabIndex >= 0 && currentTabIndex < tabs.size) {
                tabs[currentTabIndex] = tabs[currentTabIndex].copy(url = it)
            }
        }
    }

    private fun updateSecurityIndicator(securityInfo: SecurityInfo) {
        val isSecure = securityInfo.state == SecurityInfo.STATE_SECURE ||
                      securityInfo.state == SecurityInfo.STATE_BROKEN
        
        binding.securityIndicator.isVisible = true
        binding.securityIndicator.setImageResource(
            if (isSecure) android.R.drawable.ic_dialog_info
            else android.R.drawable.ic_dialog_alert
        )
    }

    private fun setupFloatingActionButton() {
        binding.fabExtensions.setOnClickListener {
            startActivity(Intent(this, ExtensionsActivity::class.java))
        }
    }

    private fun updateTabIndicator() {
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size) {
            val tab = tabs[currentTabIndex]
            binding.toolbar.title = "${tab.title} (${tabs.size})"
        }
    }

    private fun enterFullScreen() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    private fun exitFullScreen() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_browser, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_extensions -> {
                startActivity(Intent(this, ExtensionsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            if (it.action == Intent.ACTION_VIEW && it.data != null) {
                val url = it.data.toString()
                // Will be loaded after GeckoView is initialized
                coroutineScope.launch {
                    kotlinx.coroutines.delay(2000) // Wait for initialization
                    loadUrl(url)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        geckoSession?.let { binding.geckoView.attach(it) }
    }

    override fun onPause() {
        geckoSession?.let { binding.geckoView.detach(it) }
        super.onPause()
    }

    override fun onBackPressed() {
        if (geckoSession?.canGoBack() == true) {
            goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        tabs.forEach { it.session.close() }
        geckoSession = null
        super.onDestroy()
    }
}
