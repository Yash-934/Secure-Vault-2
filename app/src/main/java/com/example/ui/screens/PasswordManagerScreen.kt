package com.example.ui.screens
import androidx.compose.material.icons.automirrored.filled.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.VaultPassword
import com.example.security.PasswordCryptoHelper
import com.example.ui.theme.VaultBorder
import com.example.ui.theme.VaultErrorRed
import com.example.ui.theme.VaultNeonGreen
import com.example.ui.theme.VaultPrimaryCyan
import com.example.ui.theme.VaultSecondaryNeonBlue
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceVariant
import com.example.ui.theme.VaultTextPrimary
import com.example.ui.theme.VaultTextSecondary
import kotlinx.coroutines.launch

private val CATEGORIES = listOf("All", "Login", "Card", "Bank", "Note", "Server")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordManagerScreen(
    isDecoy: Boolean = false,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember(isDecoy) {
        if (isDecoy) AppDatabase.getDecoyDatabase(context) else AppDatabase.getDatabase(context)
    }
    val passwordDao = remember(db) { db.vaultPasswordDao() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val passwordsFlow = remember(searchQuery, db) {
        if (searchQuery.isBlank()) {
            passwordDao.getAllPasswords()
        } else {
            passwordDao.searchPasswords(searchQuery)
        }
    }
    val passwordList by passwordsFlow.collectAsState(initial = emptyList())

    val filteredList = remember(passwordList, selectedCategory) {
        if (selectedCategory == "All") passwordList
        else passwordList.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingEntity by remember { mutableStateOf<VaultPassword?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<VaultPassword?>(null) }

    Scaffold(
        containerColor = Color(0xFF06090E),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PASSWORD VAULT",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            if (isDecoy) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFFB703).copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "DECOY",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFB703),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        Text(
                            text = "AES-256-GCM Zero-Knowledge Credential Vault",
                            fontSize = 11.sp,
                            color = VaultTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("password_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF06090E))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingEntity = null
                    showAddEditDialog = true
                },
                containerColor = VaultPrimaryCyan,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_password_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Password")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NEW ENTRY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_search_field"),
                placeholder = { Text("Search logins, cards, accounts...", fontSize = 13.sp, color = VaultTextSecondary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = VaultPrimaryCyan
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultPrimaryCyan,
                    unfocusedBorderColor = Color(0xFF1B3148),
                    focusedContainerColor = Color(0xFF0C1420),
                    unfocusedContainerColor = Color(0xFF0C1420),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(CATEGORIES) { category ->
                    val isSelected = selectedCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) VaultPrimaryCyan.copy(alpha = 0.2f)
                                else Color(0xFF0C1420)
                            )
                            .border(
                                1.dp,
                                if (isSelected) VaultPrimaryCyan else Color(0xFF1B3148),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = category,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) VaultPrimaryCyan else VaultTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(VaultPrimaryCyan.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = VaultPrimaryCyan,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No Passwords Stored Yet" else "No Matching Entries Found",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap 'NEW ENTRY' below to store a secure password, login or card.",
                            fontSize = 12.sp,
                            color = VaultTextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        PasswordCardItem(
                            item = item,
                            onEdit = {
                                editingEntity = item
                                showAddEditDialog = true
                            },
                            onDelete = {
                                showDeleteConfirmDialog = item
                            },
                            onToggleFavorite = {
                                scope.launch {
                                    passwordDao.updatePassword(item.copy(isFavorite = !item.isFavorite))
                                }
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditPasswordDialog(
            initialEntity = editingEntity,
            onDismiss = { showAddEditDialog = false },
            onSave = { newEntity ->
                scope.launch {
                    if (editingEntity == null) {
                        passwordDao.insertPassword(newEntity)
                    } else {
                        passwordDao.updatePassword(newEntity)
                    }
                    showAddEditDialog = false
                    Toast.makeText(context, "Password securely saved", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showDeleteConfirmDialog != null) {
        val target = showDeleteConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            containerColor = Color(0xFF0C1420),
            title = {
                Text(
                    text = "Delete Password?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${target.title}\"? This encrypted entry cannot be restored.",
                    fontSize = 13.sp,
                    color = VaultTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            passwordDao.deletePassword(target)
                            showDeleteConfirmDialog = null
                            Toast.makeText(context, "Entry deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultErrorRed)
                ) {
                    Text("DELETE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("CANCEL", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun PasswordCardItem(
    item: VaultPassword,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current
    var isPasswordVisible by remember { mutableStateOf(false) }
    val decryptedPassword = remember(item.encryptedPasswordBlob) {
        PasswordCryptoHelper.decryptText(item.encryptedPasswordBlob)
    }
    val decryptedNotes = remember(item.encryptedNotesBlob) {
        PasswordCryptoHelper.decryptText(item.encryptedNotesBlob)
    }

    val categoryIcon = when (item.category.lowercase()) {
        "card" -> Icons.Default.CreditCard
        "bank" -> Icons.Default.Public
        "note" -> Icons.AutoMirrored.Filled.Notes
        else -> Icons.Default.Lock
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF1B3148), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1420))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VaultPrimaryCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = VaultPrimaryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = item.category.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (item.isFavorite) Color(0xFFFFD166) else VaultTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = VaultPrimaryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = VaultErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Username / Email
            if (item.usernameOrEmail.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF06090E))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = item.usernameOrEmail,
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Username", item.usernameOrEmail))
                            Toast.makeText(context, "Username copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Username",
                            tint = VaultPrimaryCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Password Field Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF06090E))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isPasswordVisible) decryptedPassword else "••••••••••••",
                    fontSize = 13.sp,
                    fontFamily = if (isPasswordVisible) FontFamily.Monospace else FontFamily.Default,
                    color = if (isPasswordVisible) VaultNeonGreen else Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                IconButton(
                    onClick = { isPasswordVisible = !isPasswordVisible },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle Visibility",
                        tint = VaultTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Password", decryptedPassword))
                        Toast.makeText(context, "Password copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Password",
                        tint = VaultPrimaryCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Notes / URL
            if (item.websiteOrUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.websiteOrUrl,
                    fontSize = 11.sp,
                    color = VaultSecondaryNeonBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (decryptedNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = decryptedNotes,
                    fontSize = 11.sp,
                    color = VaultTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AddEditPasswordDialog(
    initialEntity: VaultPassword?,
    onDismiss: () -> Unit,
    onSave: (VaultPassword) -> Unit
) {
    var title by remember { mutableStateOf(initialEntity?.title ?: "") }
    var category by remember { mutableStateOf(initialEntity?.category ?: "Login") }
    var username by remember { mutableStateOf(initialEntity?.usernameOrEmail ?: "") }
    var website by remember { mutableStateOf(initialEntity?.websiteOrUrl ?: "") }
    var notes by remember {
        mutableStateOf(
            if (initialEntity != null) PasswordCryptoHelper.decryptText(initialEntity.encryptedNotesBlob)
            else ""
        )
    }
    var password by remember {
        mutableStateOf(
            if (initialEntity != null) PasswordCryptoHelper.decryptText(initialEntity.encryptedPasswordBlob)
            else ""
        )
    }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var showGeneratorSection by remember { mutableStateOf(false) }

    // Generator Options
    var genLength by remember { mutableIntStateOf(16) }
    var genIncludeUpper by remember { mutableStateOf(true) }
    var genIncludeLower by remember { mutableStateOf(true) }
    var genIncludeDigits by remember { mutableStateOf(true) }
    var genIncludeSymbols by remember { mutableStateOf(true) }

    val (strengthScore, strengthLabel) = remember(password) {
        PasswordCryptoHelper.evaluateStrength(password)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0C1420),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = if (initialEntity == null) "NEW ENCRYPTED ENTRY" else "EDIT ENTRY",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title (e.g., Google, Bank, Netflix)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VaultPrimaryCyan,
                                unfocusedBorderColor = Color(0xFF1B3148),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    item {
                        Text(
                            text = "Category",
                            fontSize = 11.sp,
                            color = VaultTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(CATEGORIES.filter { it != "All" }) { cat ->
                                val isSelected = category == cat
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) VaultPrimaryCyan.copy(alpha = 0.2f) else Color(0xFF06090E))
                                        .border(1.dp, if (isSelected) VaultPrimaryCyan else Color(0xFF1B3148), RoundedCornerShape(8.dp))
                                        .clickable { category = cat }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 11.sp,
                                        color = if (isSelected) VaultPrimaryCyan else VaultTextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username / Email / ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VaultPrimaryCyan,
                                unfocusedBorderColor = Color(0xFF1B3148),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    item {
                        Column {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password / Secret") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = VaultTextSecondary
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VaultPrimaryCyan,
                                    unfocusedBorderColor = Color(0xFF1B3148),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            if (password.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LinearProgressIndicator(
                                        progress = { strengthScore / 100f },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = when {
                                            strengthScore >= 80 -> VaultNeonGreen
                                            strengthScore >= 50 -> Color(0xFFFFD166)
                                            else -> VaultErrorRed
                                        },
                                        trackColor = Color(0xFF1B3148)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = strengthLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = when {
                                            strengthScore >= 80 -> VaultNeonGreen
                                            strengthScore >= 50 -> Color(0xFFFFD166)
                                            else -> VaultErrorRed
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { showGeneratorSection = !showGeneratorSection },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF06090E),
                                contentColor = VaultPrimaryCyan
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showGeneratorSection) "HIDE GENERATOR" else "GENERATE STRONG PASSWORD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (showGeneratorSection) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF06090E))
                                    .border(1.dp, Color(0xFF1B3148), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Length: $genLength chars",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Slider(
                                    value = genLength.toFloat(),
                                    onValueChange = { genLength = it.toInt() },
                                    valueRange = 8f..32f,
                                    steps = 23,
                                    colors = SliderDefaults.colors(
                                        thumbColor = VaultPrimaryCyan,
                                        activeTrackColor = VaultPrimaryCyan
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Include Numbers (0-9)", fontSize = 11.sp, color = VaultTextSecondary)
                                    Switch(
                                        checked = genIncludeDigits,
                                        onCheckedChange = { genIncludeDigits = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = VaultPrimaryCyan)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Include Symbols (!@#$)", fontSize = 11.sp, color = VaultTextSecondary)
                                    Switch(
                                        checked = genIncludeSymbols,
                                        onCheckedChange = { genIncludeSymbols = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = VaultPrimaryCyan)
                                    )
                                }

                                Button(
                                    onClick = {
                                        password = PasswordCryptoHelper.generatePassword(
                                            length = genLength,
                                            includeUpper = genIncludeUpper,
                                            includeLower = genIncludeLower,
                                            includeDigits = genIncludeDigits,
                                            includeSymbols = genIncludeSymbols
                                        )
                                        isPasswordVisible = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = VaultPrimaryCyan, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Generate & Apply", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = website,
                            onValueChange = { website = it },
                            label = { Text("Website URL (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VaultPrimaryCyan,
                                unfocusedBorderColor = Color(0xFF1B3148),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Secure Notes (Encrypted)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VaultPrimaryCyan,
                                unfocusedBorderColor = Color(0xFF1B3148),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val encryptedPass = PasswordCryptoHelper.encryptText(password)
                    val encryptedNotes = PasswordCryptoHelper.encryptText(notes)

                    val entity = (initialEntity ?: VaultPassword(
                        title = title.trim(),
                        category = category,
                        usernameOrEmail = username.trim(),
                        encryptedPasswordBlob = encryptedPass,
                        websiteOrUrl = website.trim(),
                        encryptedNotesBlob = encryptedNotes
                    )).copy(
                        title = title.trim(),
                        category = category,
                        usernameOrEmail = username.trim(),
                        encryptedPasswordBlob = encryptedPass,
                        websiteOrUrl = website.trim(),
                        encryptedNotesBlob = encryptedNotes,
                        updatedTimestamp = System.currentTimeMillis()
                    )

                    onSave(entity)
                },
                colors = ButtonDefaults.buttonColors(containerColor = VaultPrimaryCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("SAVE ENCRYPTED", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.White)
            }
        }
    )
}
