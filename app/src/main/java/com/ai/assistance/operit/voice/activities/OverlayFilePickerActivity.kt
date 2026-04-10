package com.ai.assistance.operit.voice.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.services.core.AttachmentDelegate
import com.ai.assistance.operit.core.tools.AIToolHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Transparent activity that handles file picking for the overlay AI agent.
 * Launched when user wants to attach files from the overlay input box.
 */
class OverlayFilePickerActivity : Activity() {
    
    companion object {
        const val EXTRA_PICKER_TYPE = "picker_type"
        const val PICKER_TYPE_IMAGE = "image"
        const val PICKER_TYPE_FILE = "file"
        const val PICKER_TYPE_AUDIO = "audio"
        const val EXTRA_ATTACHED_FILE = "attached_file"
        
        private var attachmentDelegate: AttachmentDelegate? = null
        
        fun initialize(attachmentDelegate: AttachmentDelegate) {
            this.attachmentDelegate = attachmentDelegate
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.Main)
    private var pickerType: String = PICKER_TYPE_FILE
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make window transparent
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        pickerType = intent.getStringExtra(EXTRA_PICKER_TYPE) ?: PICKER_TYPE_FILE
        
        // Launch appropriate file picker
        when (pickerType) {
            PICKER_TYPE_IMAGE -> launchImagePicker()
            PICKER_TYPE_FILE -> launchFilePicker()
            PICKER_TYPE_AUDIO -> launchAudioPicker()
            else -> finishWithResult(null)
        }
    }
    
    private fun launchImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Select Images"), 1001)
    }
    
    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Select Files"), 1002)
    }
    
    private fun launchAudioPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Select Audio Files"), 1003)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK && data != null) {
            val clipData = data.clipData
            val uriList = mutableListOf<Uri>()
            
            if (clipData != null) {
                // Multiple files selected
                for (i in 0 until clipData.itemCount) {
                    uriList.add(clipData.getItemAt(i).uri)
                }
            } else {
                // Single file selected
                data.data?.let { uriList.add(it) }
            }
            
            if (uriList.isNotEmpty() && attachmentDelegate != null) {
                scope.launch {
                    try {
                        val attachedFiles = mutableListOf<String>()
                        
                        for (uri in uriList) {
                            val filePath = processUriToFile(uri)
                            if (filePath != null) {
                                attachmentDelegate?.handleAttachment(filePath)
                                attachedFiles.add(filePath)
                            }
                        }
                        
                        if (attachedFiles.isNotEmpty()) {
                            Toast.makeText(
                                this@OverlayFilePickerActivity,
                                "✅ ${attachedFiles.size} file(s) attached",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        
                        finishWithResult(attachedFiles)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@OverlayFilePickerActivity,
                            "❌ Failed to attach file: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        finishWithResult(null)
                    }
                }
            } else {
                finishWithResult(null)
            }
        } else {
            finishWithResult(null)
        }
    }
    
    /**
     * Process URI to a file path that AttachmentDelegate can use
     */
    private suspend fun processUriToFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            // If it's already a file URI
            if (uri.scheme == "file") {
                return@withContext uri.path
            }
            
            // For content URIs (from file picker, photos, etc.)
            if (uri.scheme == "content") {
                val fileName = getFileNameFromUri(uri) ?: "file_${System.currentTimeMillis()}"
                val tempFile = File(cacheDir, fileName)
                
                // Copy content to temp file
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                return@withContext tempFile.absolutePath
            }
            
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get file name from URI
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        
        // Try to get display name
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        
        // Fallback to last path segment
        if (fileName == null) {
            fileName = uri.lastPathSegment?.substringAfterLast('/') ?: null
        }
        
        return fileName
    }
    
    private fun finishWithResult(files: List<String>?) {
        val resultIntent = Intent().apply {
            if (files != null) {
                putStringArrayListExtra(EXTRA_ATTACHED_FILE, ArrayList(files))
            }
        }
        setResult(if (files != null) RESULT_OK else RESULT_CANCELED, resultIntent)
        finish()
    }
}
