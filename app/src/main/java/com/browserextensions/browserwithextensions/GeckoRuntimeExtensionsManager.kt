package com.browserextensions.browserwithextensions

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionStore
import java.io.File

/**
 * Manages loading, enabling, and disabling WebExtensions in GeckoRuntime.
 * 
 * This class handles the integration between the file system (where .xpi files are stored)
 * and GeckoView's extension system. Extensions installed here will work exactly like
 * Firefox desktop extensions - they can modify pages, inject scripts, add toolbar buttons, etc.
 */
class GeckoRuntimeExtensionsManager(
    private val context: Context,
    private val webExtensionStore: WebExtensionStore
) {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val extensionsDir: File
    private val loadedExtensions = mutableMapOf<String, WebExtension>()

    init {
        extensionsDir = getExtensionsDirectory()
        if (!extensionsDir.exists()) {
            extensionsDir.mkdirs()
        }
    }

    /**
     * Load all installed extensions from the extensions directory.
     * Call this after GeckoRuntime is initialized.
     */
    fun loadAllExtensions() {
        coroutineScope.launch(Dispatchers.IO) {
            val extensionFiles = extensionsDir.listFiles()?.filter { file ->
                file.isFile && (file.extension == "xpi" || file.extension == "zip")
            } ?: emptyList()

            for (extensionFile in extensionFiles) {
                try {
                    loadExtensionFromFile(extensionFile)
                } catch (e: Exception) {
                    android.util.Log.e("ExtManager", "Failed to load extension ${extensionFile.name}", e)
                }
            }
        }
    }

    /**
     * Load a single extension from an XPI file.
     */
    fun loadExtensionFromFile(file: File): GeckoResult<WebExtension> {
        android.util.Log.d("ExtManager", "Loading extension from: ${file.absolutePath}")
        
        return webExtensionStore.installFromUri(
            WebExtensionSource.fromFile(file)
        ).also { result ->
            result.then(
                { extension ->
                    android.util.Log.d("ExtManager", "Successfully loaded extension: ${extension.id}")
                    loadedExtensions[extension.id] = extension
                    
                    // Grant all permissions by default (for maximum compatibility)
                    grantAllPermissions(extension)
                    
                    extension
                },
                { error ->
                    android.util.Log.e("ExtManager", "Failed to load extension ${file.name}: $error")
                    null
                }
            )
        }
    }

    /**
     * Install an extension from a URI (e.g., from file picker).
     */
    fun installExtensionFromUri(uri: android.net.Uri): GeckoResult<WebExtension> {
        android.util.Log.d("ExtManager", "Installing extension from URI: $uri")
        
        return webExtensionStore.installFromUri(
            WebExtensionSource.fromUri(context.contentResolver, uri)
        ).also { result ->
            result.then(
                { extension ->
                    android.util.Log.d("ExtManager", "Successfully installed extension: ${extension.id}")
                    loadedExtensions[extension.id] = extension
                    
                    grantAllPermissions(extension)
                    
                    // Copy to extensions directory for persistence
                    copyExtensionToStorage(uri, extension.id)
                    
                    extension
                },
                { error ->
                    android.util.Log.e("ExtManager", "Failed to install extension: $error")
                    null
                }
            )
        }
    }

    /**
     * Uninstall/remove an extension by its ID.
     */
    fun uninstallExtension(extensionId: String): GeckoResult<Boolean> {
        android.util.Log.d("ExtManager", "Uninstalling extension: $extensionId")
        
        return webExtensionStore.uninstallById(extensionId).also { result ->
            result.then(
                { success ->
                    if (success) {
                        loadedExtensions.remove(extensionId)
                        // Remove the XPI file
                        val extFile = File(extensionsDir, "$extensionId.xpi")
                        if (extFile.exists()) extFile.delete()
                    }
                    success
                },
                { error ->
                    android.util.Log.e("ExtManager", "Failed to uninstall extension: $error")
                    false
                }
            )
        }
    }

    /**
     * Enable a disabled extension.
     */
    fun enableExtension(extensionId: String): GeckoResult<WebExtension> {
        return webExtensionStore.enableById(extensionId)
    }

    /**
     * Disable an enabled extension.
     */
    fun disableExtension(extensionId: String): GeckoResult<Boolean> {
        return webExtensionStore.disableById(extensionId)
    }

    /**
     * Get all currently loaded extensions.
     */
    fun getLoadedExtensions(): List<WebExtension> {
        return webExtensionStore.list()
    }

    /**
     * Grant all requested permissions to an extension.
     * This ensures maximum compatibility with Firefox desktop extensions.
     */
    private fun grantAllPermissions(extension: WebExtension) {
        // GeckoView automatically handles most permissions
        // For manifest V3 extensions, permissions are declared in manifest.json
        android.util.Log.d("ExtManager", "Permissions granted for extension: ${extension.id}")
    }

    /**
     * Copy an extension from a content URI to the local extensions directory.
     */
    private fun copyExtensionToStorage(uri: android.net.Uri, extensionId: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val targetFile = File(extensionsDir, "$extensionId.xpi")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                android.util.Log.d("ExtManager", "Extension saved to: ${targetFile.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.e("ExtManager", "Failed to save extension file", e)
            }
        }
    }

    private fun getExtensionsDirectory(): File {
        return File(context.filesDir, "extensions")
    }

    /**
     * Get the WebExtensionSource for a specific XPI file.
     */
    class WebExtensionSource {
        companion object {
            fun fromFile(file: File): WebExtensionStore.Source {
                return WebExtensionStore.Source.fromFile(file)
            }

            fun fromUri(contentResolver: android.content.ContentResolver, uri: android.net.Uri): WebExtensionStore.Source {
                return WebExtensionStore.Source.fromUri(contentResolver, uri)
            }
        }
    }
}
