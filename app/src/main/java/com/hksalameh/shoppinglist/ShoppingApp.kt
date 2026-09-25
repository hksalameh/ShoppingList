package com.hksalameh.shoppinglist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

enum class AppPage { LIST, CART, PHOTO }
enum class CartFilter { REMAINING, ALL, PURCHASED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingApp(viewModel: ShoppingViewModel) {
    var page by remember { mutableStateOf(AppPage.LIST) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "مشترياتي",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                viewModel.selectedCount == 0 -> "قائمة البيت دائمًا جاهزة"
                                viewModel.remainingCount == 0 -> "تم شراء جميع المواد ✓"
                                else -> "${viewModel.remainingCount} متبقي من ${viewModel.selectedCount}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (viewModel.selectedCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = viewModel.selectedCount.toString(),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = page == AppPage.LIST,
                    onClick = { page = AppPage.LIST },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("القائمة") }
                )
                NavigationBarItem(
                    selected = page == AppPage.CART,
                    onClick = { page = AppPage.CART },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    label = {
                        Text(
                            if (viewModel.selectedCount > 0) "السلة (${viewModel.selectedCount})"
                            else "السلة"
                        )
                    }
                )
                NavigationBarItem(
                    selected = page == AppPage.PHOTO,
                    onClick = { page = AppPage.PHOTO },
                    icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    label = { Text("صورة") }
                )
            }
        }
    ) { padding ->
        when (page) {
            AppPage.LIST -> MasterListScreen(viewModel, Modifier.padding(padding))
            AppPage.CART -> CartScreen(viewModel, Modifier.padding(padding))
            AppPage.PHOTO -> PhotoScreen(viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun MasterListScreen(viewModel: ShoppingViewModel, modifier: Modifier = Modifier) {
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("الكل") }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ShoppingItem?>(null) }

    val categories = listOf("الكل") + viewModel.items.map { it.category }.distinct()
    val visible = viewModel.items.filter {
        (category == "الكل" || it.category == category) &&
            (search.isBlank() || it.name.contains(search, ignoreCase = true))
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HomeSummaryCard(viewModel)

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("ابحث عن مادة…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(18.dp),
                singleLine = true
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { itemCategory ->
                    FilterChip(
                        selected = category == itemCategory,
                        onClick = { category = itemCategory },
                        leadingIcon = {
                            Icon(
                                imageVector = categoryIcon(itemCategory),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text(itemCategory) }
                    )
                }
            }

            Text(
                text = "اضغط على أي مادة لإضافتها إلى سلة التسوق",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )

            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "لا توجد نتائج",
                    subtitle = "جرّب كلمة أخرى أو أضف المادة بنفسك.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    val grouped = visible.groupBy { it.category }
                    grouped.forEach { (groupName, groupItems) ->
                        item(key = "header-$groupName") {
                            CategoryHeader(groupName, groupItems.count { it.selected })
                        }
                        items(groupItems, key = { it.id }) { item ->
                            MasterItemCard(
                                item = item,
                                onToggle = { viewModel.toggleSelected(item.id) },
                                onEdit = { editing = item },
                                onDelete = { deleting = item }
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("إضافة مادة") }
        )
    }

    if (adding) {
        ItemEditorDialog(
            title = "إضافة مادة جديدة",
            initialName = "",
            initialCategory = if (category == "الكل") "مواد غذائية" else category,
            onDismiss = { adding = false },
            onSave = { name, itemCategory ->
                viewModel.addItem(name, itemCategory)
                adding = false
            }
        )
    }

    editing?.let { item ->
        ItemEditorDialog(
            title = "تعديل المادة",
            initialName = item.name,
            initialCategory = item.category,
            onDismiss = { editing = null },
            onSave = { name, itemCategory ->
                viewModel.editItem(item.id, name, itemCategory)
                editing = null
            }
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("حذف المادة؟") },
            text = { Text("سيتم حذف «${item.name}» من القائمة الأساسية.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteItem(item.id)
                    deleting = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun HomeSummaryCard(viewModel: ShoppingViewModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.LocalGroceryStore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "قائمة البيت",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (viewModel.selectedCount == 0)
                        "اختر ما تحتاجه اليوم، والقائمة الأصلية ستبقى محفوظة."
                    else
                        "أضفت ${viewModel.selectedCount} مواد • المتبقي ${viewModel.remainingCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(name: String, selected: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = categoryIcon(name),
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (selected > 0) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    "$selected مختارة",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun MasterItemCard(
    item: ShoppingItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (item.selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (item.selected) 1.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.selected,
                onCheckedChange = { onToggle() }
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal
                )
                if (item.selected) {
                    Text(
                        "في السلة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "تعديل ${item.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "حذف ${item.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CartScreen(viewModel: ShoppingViewModel, modifier: Modifier = Modifier) {
    var filter by remember { mutableStateOf(CartFilter.REMAINING) }
    var showFinishConfirm by remember { mutableStateOf(false) }

    val selected = viewModel.items.filter { it.selected }
    val visible = when (filter) {
        CartFilter.REMAINING -> selected.filter { !it.purchased }
        CartFilter.ALL -> selected
        CartFilter.PURCHASED -> selected.filter { it.purchased }
    }

    if (selected.isEmpty()) {
        Column(modifier.fillMaxSize()) {
            EmptyState(
                icon = Icons.Default.ShoppingCart,
                title = "السلة فارغة",
                subtitle = "ارجع إلى القائمة واختر المواد التي تريد شراءها.",
                modifier = Modifier.weight(1f)
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        CartProgressCard(viewModel)

        LazyRow(
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = filter == CartFilter.REMAINING,
                    onClick = { filter = CartFilter.REMAINING },
                    label = { Text("المتبقي ${viewModel.remainingCount}") }
                )
            }
            item {
                FilterChip(
                    selected = filter == CartFilter.ALL,
                    onClick = { filter = CartFilter.ALL },
                    label = { Text("الكل ${viewModel.selectedCount}") }
                )
            }
            item {
                FilterChip(
                    selected = filter == CartFilter.PURCHASED,
                    onClick = { filter = CartFilter.PURCHASED },
                    label = { Text("تم شراؤه ${viewModel.purchasedCount}") }
                )
            }
        }

        if (viewModel.remainingCount == 0) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("اكتملت المشتريات", fontWeight = FontWeight.Bold)
                        Text("كل المواد الموجودة في السلة تم شراؤها.")
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            EmptyState(
                icon = Icons.Default.CheckCircle,
                title = "لا توجد مواد هنا",
                subtitle = "غيّر الفلتر لعرض بقية السلة.",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(visible, key = { it.id }) { item ->
                    CartItemCard(
                        item = item,
                        onTogglePurchased = { viewModel.togglePurchased(item.id) }
                    )
                }
            }
        }

        Button(
            onClick = { showFinishConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 4.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("إنهاء التسوق وتجهيز القائمة للمرة القادمة")
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
            title = { Text("إنهاء جولة التسوق؟") },
            text = {
                Text("سيتم إفراغ السلة وإلغاء علامات الشراء، وستبقى كل المواد في القائمة الأساسية محفوظة.")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.finishShopping()
                    showFinishConfirm = false
                }) { Text("إنهاء") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun CartProgressCard(viewModel: ShoppingViewModel) {
    val progress = if (viewModel.selectedCount == 0) 0f
    else viewModel.purchasedCount.toFloat() / viewModel.selectedCount.toFloat()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("جولة التسوق", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("تم ${viewModel.purchasedCount} • متبقي ${viewModel.remainingCount}")
                }
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun CartItemCard(item: ShoppingItem, onTogglePurchased: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTogglePurchased),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (item.purchased)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.purchased,
                onCheckedChange = { onTogglePurchased() }
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.purchased) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    item.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = if (item.purchased)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = if (item.purchased) Icons.Default.CheckCircle else categoryIcon(item.category),
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp).size(21.dp),
                    tint = if (item.purchased)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PhotoScreen(viewModel: ShoppingViewModel, modifier: Modifier = Modifier) {
    var quickText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "صوّر قائمتك الورقية",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "جهزنا مكان الميزة داخل التطبيق. القراءة التلقائية للخط العربي من الصورة ستُفعّل بعد تثبيت النسخة الأساسية والتأكد من عملها.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ميزة التصوير: المرحلة التالية", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("إدخال سريع الآن", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "اكتب كل مادة في سطر مستقل.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = quickText,
                        onValueChange = { quickText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        placeholder = { Text("حليب\nتونة\nسائل غسيل الملابس") },
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.selectImported(quickText.lines())
                            quickText = ""
                        },
                        enabled = quickText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("إضافة المواد إلى السلة")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ItemEditorDialog(
    title: String,
    initialName: String,
    initialCategory: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    val quickCategories = listOf("لحوم ومجمدات", "مواد غذائية", "بهارات", "منظفات وعناية", "أخرى")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("اسم المادة") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("التصنيف") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Text(
                    "تصنيفات سريعة",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(quickCategories) { quick ->
                        FilterChip(
                            selected = category == quick,
                            onClick = { category = quick },
                            label = { Text(quick) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), category.trim()) },
                enabled = name.isNotBlank() && category.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    "لحوم ومجمدات" -> Icons.Default.Kitchen
    "بهارات" -> Icons.Default.Restaurant
    "منظفات وعناية" -> Icons.Default.CleaningServices
    "الكل" -> Icons.Default.List
    else -> Icons.Default.LocalGroceryStore
}
