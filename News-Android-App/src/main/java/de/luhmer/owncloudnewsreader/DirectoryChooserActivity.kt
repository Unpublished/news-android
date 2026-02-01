package de.luhmer.owncloudnewsreader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.rdrei.android.dirchooser.DirectoryChooserConfig
import java.io.File

/**
 * A directory chooser activity implemented using Jetpack Compose.
 * This activity replaces the old XML-based directory chooser with a modern Compose UI.
 */
class DirectoryChooserActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONFIG = "config"
        const val RESULT_SELECTED_DIR = "selected_dir"
        const val RESULT_CODE_DIR_SELECTED = Activity.RESULT_OK
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply theme from intent config or use default
        setTheme(R.style.AppTheme)

        val config = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CONFIG, DirectoryChooserConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CONFIG)
        }

        val initialDirectory = config?.initialDirectory?.takeIf { it.isNotEmpty() }
            ?: getExternalFilesDir(null)?.absolutePath
            ?: filesDir.absolutePath

        setContent {
            MaterialTheme {
                DirectoryChooserScreen(
                    initialDirectory = initialDirectory,
                    onDirectorySelected = { selectedPath ->
                        val resultIntent = Intent().apply {
                            putExtra(RESULT_SELECTED_DIR, selectedPath)
                        }
                        setResult(RESULT_CODE_DIR_SELECTED, resultIntent)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryChooserScreen(
    initialDirectory: String,
    onDirectorySelected: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var currentPath by remember { mutableStateOf(initialDirectory) }
    var currentDir by remember { mutableStateOf(File(initialDirectory)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Directory") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = "Cancel"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Current path display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = currentPath,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Directory list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Parent directory option if not at root
                if (currentDir.parent != null) {
                    item {
                        DirectoryItem(
                            name = "..",
                            isParent = true,
                            onClick = {
                                currentDir.parentFile?.let { parent ->
                                    currentDir = parent
                                    currentPath = parent.absolutePath
                                }
                            }
                        )
                    }
                }

                // List subdirectories
                val directories = try {
                    currentDir.listFiles { file ->
                        file.isDirectory && !file.isHidden
                    }?.sortedBy { it.name.lowercase() } ?: emptyList()
                } catch (e: SecurityException) {
                    emptyList()
                }

                items(directories) { directory ->
                    DirectoryItem(
                        name = directory.name,
                        isParent = false,
                        onClick = {
                            currentDir = directory
                            currentPath = directory.absolutePath
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onDirectorySelected(currentPath) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select")
                }
            }
        }
    }
}

@Composable
fun DirectoryItem(
    name: String,
    isParent: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (isParent) android.R.drawable.ic_menu_upload
                    else android.R.drawable.ic_menu_sort_by_size
                ),
                contentDescription = if (isParent) "Parent directory" else "Directory",
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
