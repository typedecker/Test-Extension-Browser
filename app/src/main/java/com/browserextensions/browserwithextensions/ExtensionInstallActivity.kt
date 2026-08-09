package com.browserextensions.browserwithextensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ExtensionInstallActivity : Activity() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val intent = intent
        if (intent?.data != null) {
            installExtension(intent.data!!)
        } else {
            finish()
        }
    }

    private fun installExtension(uri: Uri) {
        coroutineScope.launch {
            try {
                val fileName = getFileName(uri) ?: "extension.xpi"
                val targetFile = File(
                    BrowserApplication.getInstance().getExtensionsDirectory(), 
                    fileName
                )
                
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                Toast.makeText(this@ExtensionInstallActivity, 
                    "Extension installed successfully!", 
                    Toast.LENGTH_LONG).show()
                
                // Open the extensions manager to show the new extension
                val openExtensions = Intent(this@ExtensionInstallActivity, ExtensionsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(openExtensions)
                
            } catch (e: Exception) {
                Toast.makeText(this@ExtensionInstallActivity, 
                    "Failed to install extension: ${e.message}", 
                    Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        return if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } else {
            Uri.decode(uri.lastPathSegment)
        }?.takeIf { it.endsWith(".xpi", ignoreCase = true) || 
                     it.endsWith(".zip", ignoreCase = true) }
    }
}
