package com.browserextensions.browserwithextensions

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.browserextensions.browserwithextensions.databinding.ActivityExtensionsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoResult
import org.mozilla.webextensions.WebExtension
import java.io.File
import java.io.FileOutputStream

class ExtensionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExtensionsBinding
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var extensionsAdapter: ExtensionsAdapter
    private val installedExtensions = mutableListOf<ExtensionInfo>()

    data class ExtensionInfo(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val enabled: Boolean,
        val path: File?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExtensionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Extensions"
        }

        setupRecyclerView()
        setupInstallButtons()
        loadInstalledExtensions()
    }

    private fun setupRecyclerView() {
        extensionsAdapter = ExtensionsAdapter(installedExtensions,
            onRemoveClick = { extension -> removeExtension(extension) },
            onToggleEnabled = { extension -> toggleExtensionEnabled(extension) }
        )
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = extensionsAdapter
        
        if (installedExtensions.isEmpty()) {
            binding.emptyStateView.visibility = android.view.View.VISIBLE
        }
    }

    private fun loadInstalledExtensions() {
        coroutineScope.launch {
            val extensions = withContext(Dispatchers.IO) {
                scanInstalledExtensions()
            }
            
            installedExtensions.clear()
            installedExtensions.addAll(extensions)
            extensionsAdapter.notifyDataSetChanged()
            
            binding.emptyStateView.visibility = 
                if (installedExtensions.isEmpty()) android.view.View.VISIBLE
                else android.view.View.GONE
        }
    }

    private fun scanInstalledExtensions(): List<ExtensionInfo> {
        val app = BrowserApplication.getInstance()
        val extensionsDir = app.getExtensionsDirectory()
        
        if (!extensionsDir.exists()) return emptyList()
        
        val extensions = mutableListOf<ExtensionInfo>()
        
        for (file in extensionsDir.listFiles().orEmpty()) {
            if (file.isDirectory || file.extension == "xpi") {
                try {
                    val info = parseExtensionInfo(file)
                    if (info != null) {
                        extensions.add(info)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ExtensionsActivity", "Error parsing extension", e)
                }
            }
        }
        
        return extensions
    }

    private fun parseExtensionInfo(file: File): ExtensionInfo? {
        // If it's a directory, look for manifest.json inside
        val manifestFile = if (file.isDirectory) {
            File(file, "manifest.json")
        } else {
            // For XPI files, we'd need to extract and read the manifest
            // For now, use filename as name
            return ExtensionInfo(
                id = file.nameWithoutExtension,
                name = file.nameWithoutExtension.replaceFirstChar { it.uppercase() },
                version = "1.0",
                description = "Extension from ${file.name}",
                enabled = true,
                path = file
            )
        }

        if (!manifestFile.exists()) return null
        
        try {
            val manifestContent = manifestFile.readText()
            // Simple JSON parsing for manifest.json
            val name = extractJsonString(manifestContent, "name") ?: file.name
            val version = extractJsonString(manifestContent, "version") ?: "1.0"
            val description = extractJsonString(manifestContent, "description") ?: ""
            val id = extractJsonString(manifestContent, "browser_specific_settings")?.let {
                extractExtensionId(it)
            } ?: file.name
            
            return ExtensionInfo(
                id = id,
                name = name,
                version = version,
                description = description,
                enabled = true,
                path = if (file.isDirectory) null else file
            )
        } catch (e: Exception) {
            android.util.Log.e("ExtensionsActivity", "Error reading manifest", e)
            return null
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = ""$key"\s*:\s*"([^"]*)""
        return java.util.regex.Pattern.compile(pattern).matcher(json)
            .takeIf { it.find() }?.group(1)
    }

    private fun extractExtensionId(browserSettings: String): String? {
        val pattern = ""gecko"\s*:\s*\{[^}]*"id"\s*:\s*"([^"]*)""
        return java.util.regex.Pattern.compile(pattern).matcher(browserSettings)
            .takeIf { it.find() }?.group(1)
    }

    private fun setupInstallButtons() {
        binding.installFromFileButton.setOnClickListener {
            openFilePicker()
        }

        binding.installFromUrlButton.setOnClickListener {
            showInstallFromUrlDialog()
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/x-xpinstall",
                "application/zip",
                "application/octet-stream"
            ))
        }
        
        try {
            startActivityForResult(Intent.createChooser(intent, "Select Extension File"), REQUEST_INSTALL_EXTENSION)
        } catch (e: Exception) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInstallFromUrlDialog() {
        val input = android.widget.EditText(this)
        input.hint = "https://example.com/extension.xpi"
        
        AlertDialog.Builder(this)
            .setTitle("Install Extension from URL")
            .setMessage("Enter the direct download URL of the .xpi file:")
            .setView(input)
            .setPositiveButton("Install") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    installExtensionFromUrl(url)
                } else {
                    Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun installExtensionFromUrl(url: String) {
        coroutineScope.launch {
            try {
                val fileName = url.substringAfterLast("/")
                    .takeIf { it.isNotEmpty() && it.contains(".") }
                    ?: "extension.xpi"
                
                val targetFile = File(BrowserApplication.getInstance().getExtensionsDirectory(), fileName)
                
                withContext(Dispatchers.IO) {
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    
                    try {
                        val inputStream = connection.inputStream
                        FileOutputStream(targetFile).use { output ->
                            inputStream.copyTo(output)
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExtensionsActivity, 
                        "Extension downloaded. Refresh to see it.", 
                        Toast.LENGTH_LONG).show()
                    loadInstalledExtensions()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExtensionsActivity, 
                        "Failed to download extension: ${e.message}", 
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_INSTALL_EXTENSION && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                installExtensionFromUri(uri)
            }
        }
    }

    private fun installExtensionFromUri(uri: Uri) {
        coroutineScope.launch {
            try {
                val fileName = getFileNameFromUri(uri) ?: "extension.xpi"
                val targetFile = File(BrowserApplication.getInstance().getExtensionsDirectory(), fileName)
                
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExtensionsActivity, 
                        R.string.extension_installed, 
                        Toast.LENGTH_LONG).show()
                    loadInstalledExtensions()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExtensionsActivity, 
                        "${R.string.extension_install_failed}: ${e.message}", 
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        } else if (uri.scheme == "file") {
            fileName = Uri.decode(uri.lastPathSegment)
        }
        
        return fileName?.takeIf { it.endsWith(".xpi", ignoreCase = true) || 
                                 it.endsWith(".zip", ignoreCase = true) }
    }

    private fun removeExtension(extension: ExtensionInfo) {
        AlertDialog.Builder(this)
            .setTitle("Remove Extension")
            .setMessage("Are you sure you want to remove "${extension.name}"?")
            .setPositiveButton("Remove") { _, _ ->
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        extension.path?.deleteRecursively()
                        File(BrowserApplication.getInstance().getExtensionsDirectory(), extension.id).apply {
                            if (exists()) deleteRecursively()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        installedExtensions.remove(extension)
                        extensionsAdapter.notifyDataSetChanged()
                        
                        if (installedExtensions.isEmpty()) {
                            binding.emptyStateView.visibility = android.view.View.VISIBLE
                        }
                        
                        Toast.makeText(this@ExtensionsActivity, 
                            "Extension removed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleExtensionEnabled(extension: ExtensionInfo) {
        // In a real implementation, this would communicate with GeckoRuntime
        // to enable/disable the extension. For now, we track it locally.
        val index = installedExtensions.indexOfFirst { it.id == extension.id }
        if (index != -1) {
            installedExtensions[index] = extension.copy(enabled = !extension.enabled)
            extensionsAdapter.notifyItemChanged(index)
            
            Toast.makeText(this, 
                "${extension.name} ${if (!extension.enabled) "disabled" else "enabled"}", 
                Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadInstalledExtensions()
    }

    companion object {
        private const val REQUEST_INSTALL_EXTENSION = 1001
    }
}
