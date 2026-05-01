package com.wizaird.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizaird.app.data.*
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BackupScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalWizairdColors.current
    
    var showImportDialog by remember { mutableStateOf(false) }
    var backupInfo by remember { mutableStateOf<BackupData?>(null) }
    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    
    // Export backup launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = exportBackup(context, uri)
                statusMessage = result.getOrNull() ?: result.exceptionOrNull()?.message ?: "Export failed"
            }
        }
    }
    
    // Import backup launcher
    val importPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedImportUri = uri
            scope.launch {
                val result = getBackupInfo(context, uri)
                if (result.isSuccess) {
                    backupInfo = result.getOrNull()
                    showImportDialog = true
                } else {
                    statusMessage = result.exceptionOrNull()?.message ?: "Failed to read backup"
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(48.dp))

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val backInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .drawPixelCircle(
                            fillColor   = colors.secondaryButton,
                            borderColor = Color.Transparent,
                            cutColor    = colors.background
                        )
                        .pixelCircleClickable(interactionSource = backInteraction) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .drawPixelArrowButton(
                                fillColor  = colors.secondaryButton,
                                cutColor   = colors.secondaryButton,
                                arrowColor = colors.secondaryIcon,
                                direction  = -1f
                            )
                    )
                }
                Text(
                    "BACKUP & RESTORE",
                    style = pixelStyle(14, colors.secondaryIcon),
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-2).dp)
                )
            }
            
            // Two large buttons at top
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PixelButtonLarge(
                    label = "EXPORT BACKUP",
                    onClick = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        exportLauncher.launch("wizaird_backup_$timestamp.zip")
                    },
                    primary = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                PixelButtonLarge(
                    label = "IMPORT BACKUP",
                    onClick = {
                        importPreviewLauncher.launch(arrayOf("application/zip"))
                    },
                    primary = false,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Status message
                if (statusMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = statusMessage,
                        style = pixelStyle(10, colors.secondaryIcon).copy(lineHeight = (10 * 1.6).sp),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                }
            }
        }
        
        // Import confirmation dialog
        if (showImportDialog && backupInfo != null) {
            PixelConfirmationDialog(
                title = "IMPORT BACKUP",
                message = "This will replace all your current data with:\n\n" +
                        "• ${backupInfo!!.projects.size} projects\n" +
                        "• ${backupInfo!!.notes.size} notes\n" +
                        "• ${backupInfo!!.chats.size} chats\n" +
                        "• ${backupInfo!!.storedInsights.size} insights\n" +
                        "• ${backupInfo!!.imageFiles.size} images\n\n" +
                        "Export Date: ${backupInfo!!.exportDate}\n\n" +
                        "This cannot be undone.",
                confirmLabel = "IMPORT",
                cancelLabel = "CANCEL",
                isDestructive = true,
                onConfirm = {
                    showImportDialog = false
                    val uri = selectedImportUri
                    if (uri != null) {
                        scope.launch {
                            val result = importBackup(context, uri, mergeMode = false)
                            statusMessage = result.getOrNull() ?: result.exceptionOrNull()?.message ?: "Import failed"
                            backupInfo = null
                            selectedImportUri = null
                        }
                    }
                },
                onDismiss = {
                    showImportDialog = false
                    backupInfo = null
                    selectedImportUri = null
                }
            )
        }
    }
}
