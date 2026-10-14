package com.awbuilds.auraspend.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.awbuilds.auraspend.R
import com.awbuilds.auraspend.domain.model.Category
import com.awbuilds.auraspend.ui.core.UiError
import com.awbuilds.auraspend.ui.designsystem.AuraCard
import com.awbuilds.auraspend.ui.designsystem.AuraCardStyle
import com.awbuilds.auraspend.ui.designsystem.AuraEmptyState
import com.awbuilds.auraspend.ui.designsystem.AuraErrorBanner
import com.awbuilds.auraspend.ui.designsystem.AuraSpacing
import com.awbuilds.auraspend.ui.designsystem.categoryColorChoices
import com.awbuilds.auraspend.ui.designsystem.categoryIconChoices
import com.awbuilds.auraspend.ui.designsystem.categoryIconVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    categories: List<Category>,
    onSaveCategory: suspend (Category) -> Boolean,
    onDeleteCategory: suspend (String) -> Boolean,
    onBack: () -> Unit,
    /**
     * A failed category write, reported inline while the list stays visible.
     * The save/delete callbacks return whether the write landed, so a dialog
     * closes only on success — a failed save used to close as if it had worked
     * (edit) or never close at all (add), and the edit was lost either way.
     */
    error: UiError? = null,
    onErrorDismissed: () -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<Category?>(null) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.categories_add))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            error?.let { failure ->
                AuraErrorBanner(
                    message = stringResource(failure.messageRes),
                    modifier = Modifier.padding(
                        horizontal = AuraSpacing.gutter,
                        vertical = AuraSpacing.sm
                    ),
                    onDismiss = onErrorDismissed
                )
            }
            if (categories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AuraEmptyState(
                        icon = Icons.Default.Category,
                        title = stringResource(R.string.categories_empty_title),
                        message = stringResource(R.string.categories_empty_message)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = AuraSpacing.gutter,
                        vertical = AuraSpacing.lg
                    ),
                    verticalArrangement = Arrangement.spacedBy(AuraSpacing.sm)
                ) {
                    items(categories, key = { it.id }) { category ->
                        CategoryCard(
                            category = category,
                            onEdit = { editCategory = category },
                            onDelete = { deleteConfirmId = category.id }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryEditDialog(
            title = stringResource(R.string.categories_add),
            initialName = "",
            initialIcon = "category",
            initialColor = 0xFF757575.toInt(),
            onSave = { name, icon, color ->
                scope.launch {
                    val saved = onSaveCategory(
                        Category(
                            name = name,
                            icon = icon,
                            color = color,
                            isDefault = false
                        )
                    )
                    if (saved) showAddDialog = false
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editCategory?.let { cat ->
        CategoryEditDialog(
            title = stringResource(R.string.categories_edit),
            initialName = cat.name,
            initialIcon = cat.icon,
            initialColor = cat.color,
            onSave = { name, icon, color ->
                scope.launch {
                    val saved = onSaveCategory(cat.copy(name = name, icon = icon, color = color))
                    if (saved) editCategory = null
                }
            },
            onDismiss = { editCategory = null }
        )
    }

    deleteConfirmId?.let { id ->
        val cat = categories.find { it.id == id }
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text(stringResource(R.string.categories_delete_title), modifier = Modifier.semantics { heading() }) },
            text = {
                if (cat?.isDefault == true) {
                    Text(stringResource(R.string.categories_default_locked))
                } else {
                    Text(stringResource(R.string.categories_delete_message, cat?.name ?: ""))
                }
            },
            confirmButton = {
                if (cat?.isDefault == true) {
                    TextButton(onClick = { deleteConfirmId = null }) {
                        Text(stringResource(R.string.action_ok))
                    }
                } else {
                    TextButton(onClick = {
                        scope.launch {
                            val deleted = onDeleteCategory(id)
                            if (deleted) deleteConfirmId = null
                        }
                    }) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = Color(category.color.toLong())
    AuraCard(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        style = AuraCardStyle.Outlined
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(category.icon),
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(AuraSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (category.isDefault) {
                    Spacer(modifier = Modifier.height(AuraSpacing.xxs))
                    Text(
                        stringResource(R.string.categories_default_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!category.isDefault) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryEditDialog(
    title: String,
    initialName: String,
    initialIcon: String,
    initialColor: Int,
    onSave: (name: String, icon: String, color: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    var color by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AuraSpacing.md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.categories_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // The icon grid: exactly the keys the renderer knows, six per
                // row, 48dp targets. Chunked rows instead of FlowRow — a fixed
                // offer needs no layout experiment.
                Column(verticalArrangement = Arrangement.spacedBy(AuraSpacing.xs)) {
                    Text(
                        stringResource(R.string.categories_icon),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    categoryIconChoices.chunked(6).forEach { rowChoices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.xs)) {
                            rowChoices.forEach { choice ->
                                val selected = choice == icon
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable { icon = choice },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = categoryIconVector(choice)
                                            ?: Icons.Default.Category,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(AuraSpacing.xs)) {
                    Text(
                        stringResource(R.string.categories_color),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    categoryColorChoices.chunked(6).forEach { rowChoices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.xs)) {
                            rowChoices.forEach { choice ->
                                val selected = choice == color
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable { color = choice },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (selected) 30.dp else 24.dp)
                                            .clip(CircleShape)
                                            .background(Color(choice.toLong()))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(name.trim(), icon, color)
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private fun categoryIcon(iconName: String): ImageVector {
    return when (iconName) {
        "restaurant" -> Icons.Default.Restaurant
        "directions_car" -> Icons.Default.DirectionsCar
        "shopping_bag" -> Icons.Default.ShoppingBag
        "receipt_long" -> Icons.AutoMirrored.Filled.ReceiptLong
        "movie" -> Icons.Default.Movie
        "local_hospital" -> Icons.Default.LocalHospital
        "school" -> Icons.Default.School
        "account_balance" -> Icons.Default.AccountBalance
        "subscriptions" -> Icons.Default.Subscriptions
        "swap_horiz" -> Icons.Default.SwapHoriz
        "local_grocery_store" -> Icons.Default.LocalGroceryStore
        "category" -> Icons.Default.Category
        else -> Icons.Default.Category
    }
}
