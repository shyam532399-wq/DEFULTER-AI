package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.BoardDocument
import com.example.data.model.DataUiState
import com.example.ui.BusinessToolState
import com.example.ui.ImageStudioState
import com.example.ui.MayaViewModel
import com.example.ui.PendingConfirmation
import com.example.ui.theme.LocalPersonaAccent

@Composable
fun BoardStudioScreen(
    viewModel: MayaViewModel,
    documentsState: DataUiState<List<BoardDocument>>,
    imageStudioState: ImageStudioState,
    businessToolState: BusinessToolState,
    pendingConfirmation: PendingConfirmation?,
    onBackToCockpit: () -> Unit
) {
    var selectedDoc by remember { mutableStateOf<BoardDocument?>(null) }
    var activeSection by rememberSaveable { mutableStateOf("board") } // board, image_studio, business
    var selectedCategory by rememberSaveable { mutableStateOf("all") }
    var showCreateNoteDialog by remember { mutableStateOf(false) }

    // Mandatory BackHandler for secondary screen & nested detail view
    BackHandler {
        if (selectedDoc != null) {
            selectedDoc = null
        } else {
            onBackToCockpit()
        }
    }

    val accent = LocalPersonaAccent.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Maya Board & Studio",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Research, emails, Gemini Image macro, aur business outputs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showCreateNoteDialog = true },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("new_board_note_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Note")
                }
            }

            // Confirmation banner if Boss requested a document delete
            if (pendingConfirmation != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pendingConfirmation.promptSpoken,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::confirmPendingAction) { Text("Haan") }
                        TextButton(onClick = viewModel::cancelPendingAction) { Text("Nahi") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-section Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeSection == "board",
                    onClick = { activeSection = "board" },
                    label = { Text("Board Docs") },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = activeSection == "image_studio",
                    onClick = { activeSection = "image_studio" },
                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Gemini Image Macro") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("section_image_studio")
                )
                FilterChip(
                    selected = activeSection == "business",
                    onClick = { activeSection = "business" },
                    leadingIcon = { Icon(Icons.Filled.BusinessCenter, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Business Suite") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("section_business_suite")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (activeSection) {
                "image_studio" -> GeminiImageStudioPanel(
                    state = imageStudioState,
                    onGenerate = { prompt -> viewModel.generateRuntimeImage(prompt, autoSaveToGallery = false) },
                    onSaveGallery = viewModel::saveStudioBitmapToGallery
                )

                "business" -> BusinessToolkitPanel(
                    state = businessToolState,
                    onRunTool = viewModel::runBusinessGenerator
                )

                else -> {
                    val categories = listOf("all", "research", "marketing", "reviews", "email", "market", "dashboard", "note")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (documentsState) {
                        is DataUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accent.primary)
                        }

                        is DataUiState.Error -> Text(
                            text = documentsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        is DataUiState.Success -> {
                            val filtered = if (selectedCategory == "all") {
                                documentsState.data
                            } else {
                                documentsState.data.filter { it.category == selectedCategory }
                            }

                            if (filtered.isEmpty()) {
                                EmptyBoardPlaceholder()
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(filtered, key = { it.id }) { doc ->
                                        BoardDocCard(
                                            doc = doc,
                                            onOpen = { selectedDoc = doc },
                                            onTogglePin = { viewModel.togglePinDocument(doc) },
                                            onRequestDelete = { viewModel.requestDeleteBoardDoc(doc) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { selectedDoc = null },
            title = { Text(doc.title, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Category: ${doc.category.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = doc.content, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDoc = null }) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Band karein")
                }
            }
        )
    }

    if (showCreateNoteDialog) {
        CreateNoteDialog(
            onDismiss = { showCreateNoteDialog = false },
            onSave = { title, cat, body ->
                viewModel.addManualBoardNote(title, cat, body)
                showCreateNoteDialog = false
            }
        )
    }
}

@Composable
private fun BoardDocCard(
    doc: BoardDocument,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val accent = LocalPersonaAccent.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("board_doc_card_${doc.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accent.primary.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = doc.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onTogglePin, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Pin document",
                        tint = if (doc.pinned) accent.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRequestDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete document",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (doc.summary.isNotBlank()) {
                Text(
                    text = doc.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun EmptyBoardPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Maya Board abhi khaali hai, Boss.",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Lambi reports, emails, market snapshots aur research yahan save hoti hain — voice me sirf kaam ki 2 line!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GeminiImageStudioPanel(
    state: ImageStudioState,
    onGenerate: (String) -> Unit,
    onSaveGallery: (android.graphics.Bitmap, String) -> Unit
) {
    var prompt by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Built-in Macro: Gemini Image (gemini-nano-banana-2.1)",
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Image prompt likhein (jaise: Sunset on a beach in Goa, cinematic)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("image_prompt_input")
        )
        Button(
            onClick = { onGenerate(prompt) },
            enabled = prompt.isNotBlank() && state !is ImageStudioState.Generating,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("generate_image_button")
        ) {
            Text("Gemini se Image Banao")
        }

        when (state) {
            is ImageStudioState.Idle -> Text(
                text = "Tip: Image generate hone me 30–60 seconds lag sakte hain.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            is ImageStudioState.Generating -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Image ban rahi hai (30–60s)…")
                    }
                }
            }

            is ImageStudioState.Ready -> {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = state.prompt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
                OutlinedButton(
                    onClick = { onSaveGallery(state.bitmap, state.prompt) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_gallery_button")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gallery me Save Karein")
                }
                state.savedMessage?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                }
            }

            is ImageStudioState.Error -> {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BusinessToolkitPanel(
    state: BusinessToolState,
    onRunTool: (String, String) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf("review_reply") }
    var rawInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Section 5 Business & Marketing Rules Engine",
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "review_reply" to "Review Reply",
                "review_themes" to "Review Themes",
                "dashboard" to "Dashboard (8-line)",
                "marketing_plan" to "Marketing Plan"
            ).forEach { (id, label) ->
                FilterChip(
                    selected = mode == id,
                    onClick = { mode = id },
                    label = { Text(label) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        OutlinedTextField(
            value = rawInput,
            onValueChange = { rawInput = it },
            minLines = 4,
            label = {
                Text(
                    when (mode) {
                        "review_reply" -> "Customer review paste karein (aur contact info agar ho)"
                        "review_themes" -> "Multiple customer reviews paste karein"
                        "dashboard" -> "KPI / Dashboard metrics paste karein"
                        else -> "Product, offer aur target audience likhein"
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("business_tool_input")
        )

        Button(
            onClick = { onRunTool(mode, rawInput) },
            enabled = rawInput.isNotBlank() && state !is BusinessToolState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("run_business_tool_button")
        ) {
            Text("Generate & Save to Board")
        }

        when (state) {
            is BusinessToolState.Idle -> {}
            is BusinessToolState.Loading -> CircularProgressIndicator()
            is BusinessToolState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is BusinessToolState.Success -> {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.output,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Naya Board Document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.ifBlank { "Quick Note" }, "note", content.ifBlank { "-" }) },
                enabled = title.isNotBlank() || content.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
