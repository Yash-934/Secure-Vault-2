package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DecoyNote(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val content: String,
    val date: String = "Today"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onPinSubmit: (String) -> Unit
) {
    val notes = remember {
        mutableStateListOf(
            DecoyNote(1, "Grocery List", "Milk, Eggs, Whole grain bread, Avocados, Olive oil", "Yesterday"),
            DecoyNote(2, "Meeting Reminders", "Discuss quarterly roadmaps with engineering team on Thursday 10 AM", "2 days ago"),
            DecoyNote(3, "Book Recommendations", "Atomic Habits, Clean Architecture, The Pragmatic Programmer", "Last week")
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var activeNote by remember { mutableStateOf<DecoyNote?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var isCreatingNew by remember { mutableStateOf(false) }

    // Covert Trigger: Check if search query matches PIN
    fun checkCovertTrigger(input: String) {
        if (input.length in 4..8 && input.all { it.isDigit() }) {
            onPinSubmit(input)
        }
    }

    if (activeNote != null || isCreatingNew) {
        // Edit / View Note Sub-Screen
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isCreatingNew) "New Note" else "Edit Note",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            activeNote = null
                            isCreatingNew = false
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                checkCovertTrigger(editTitle)
                                if (isCreatingNew) {
                                    if (editTitle.isNotBlank() || editContent.isNotBlank()) {
                                        notes.add(0, DecoyNote(title = editTitle.ifBlank { "Untitled" }, content = editContent))
                                    }
                                } else {
                                    val idx = notes.indexOfFirst { it.id == activeNote?.id }
                                    if (idx != -1) {
                                        notes[idx] = activeNote!!.copy(title = editTitle.ifBlank { "Untitled" }, content = editContent)
                                    }
                                }
                                activeNote = null
                                isCreatingNew = false
                            },
                            modifier = Modifier.testTag("save_note_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save Note", tint = Color(0xFF64B5F6))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
                )
            },
            containerColor = Color(0xFF0F172A)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = {
                        editTitle = it
                        checkCovertTrigger(it)
                    },
                    placeholder = { Text("Title", color = Color(0xFF94A3B8)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    placeholder = { Text("Note content...", color = Color(0xFF94A3B8)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("note_content_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )
            }
        }
    } else {
        // Main Notes List Screen
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                // Covert trigger: clicking title 5 times
                            }
                        ) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = "Notes",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Quick Notes",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editTitle = ""
                        editContent = ""
                        isCreatingNew = true
                    },
                    containerColor = Color(0xFF0284C7),
                    contentColor = Color.White,
                    modifier = Modifier.testTag("create_new_note_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Note")
                }
            },
            containerColor = Color(0xFF0F172A)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Search bar with covert PIN detector
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        checkCovertTrigger(it)
                    },
                    placeholder = { Text("Search notes...", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF94A3B8))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notes_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                val filteredNotes = notes.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                            it.content.contains(searchQuery, ignoreCase = true)
                }

                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No notes found", color = Color(0xFF64748B), fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                                    .clickable {
                                        activeNote = note
                                        editTitle = note.title
                                        editContent = note.content
                                    }
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = note.title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = note.date,
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = note.content,
                                        fontSize = 13.sp,
                                        color = Color(0xFFCBD5E1),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
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
