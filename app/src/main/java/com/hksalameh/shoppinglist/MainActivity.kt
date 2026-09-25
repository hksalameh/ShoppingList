package com.hksalameh.shoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider

enum class AppPage { LIST, CART, PHOTO }
enum class CartFilter { REMAINING, ALL, PURCHASED }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[ShoppingViewModel::class.java]
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    ShoppingApp(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingApp(viewModel: ShoppingViewModel) {
    var page by remember { mutableStateOf(AppPage.LIST) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("مشترياتي")
                        Text(
                            text = if (viewModel.selectedCount == 0) "جهّز قائمة التسوق" else "${viewModel.remainingCount} متبقي من ${viewModel.selectedCount}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
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
                    label = { Text("السلة (${viewModel.selectedCount})") }
                )
                NavigationBarItem(
                    selected = page == AppPage.PHOTO,
                    onClick = { page = AppPage.PHOTO },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
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
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                label = { Text("بحث عن مادة") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { itemCategory ->
                    FilterChip(
                        selected = category == itemCategory,
                        onClick = { category = itemCategory },
                        label = { Text(itemCategory) }
                    )
                }
            }

            Text(
                "ضع علامة على المواد التي تريد شراءها، ثم افتح السلة.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val grouped = visible.groupBy { it.category }
                grouped.forEach { (groupName, groupItems) ->
                    item(key = "header-$groupName") {
                        Text(
                            text = groupName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(groupItems, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleSelected(item.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = item.selected,
                                    onCheckedChange = { viewModel.toggleSelected(item.id) }
                                )
                                Text(
                                    text = item.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                IconButton(onClick = { editing = item }) {
                                    Icon(Icons.Default.Edit, contentDescription = "تعديل")
                                }
                                IconButton(onClick = { deleting = item }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف")
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "إضافة مادة")
        }
    }

    if (adding) {
        ItemEditorDialog(
            title = "إضافة مادة",
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
private fun CartScreen(viewModel: ShoppingViewModel, modifier: Modifier = Modifier) {
    var filter by remember { mutableStateOf(CartFilter.REMAINING) }
    var showFinishConfirm by remember { mutableStateOf(false) }

    val selected = viewModel.items.filter { it.selected }
    val visible = when (filter) {
        CartFilter.REMAINING -> selected.filter { !it.purchased }
        CartFilter.ALL -> selected
        CartFilter.PURCHASED -> selected.filter { it.purchased }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        if (selected.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null)
                    Spacer(Modifier.height(8.dp))
                    Text("السلة فارغة", style = MaterialTheme.typography.titleMedium)
                    Text("ارجع إلى القائمة واختر المواد المطلوبة.")
                }
            }
            return
        }

        Text(
            "المتبقي ${viewModel.remainingCount} • تم شراء ${viewModel.purchasedCount}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == CartFilter.REMAINING,
                onClick = { filter = CartFilter.REMAINING },
                label = { Text("المتبقي") }
            )
            FilterChip(
                selected = filter == CartFilter.ALL,
                onClick = { filter = CartFilter.ALL },
                label = { Text("الكل") }
            )
            FilterChip(
                selected = filter == CartFilter.PURCHASED,
                onClick = { filter = CartFilter.PURCHASED },
                label = { Text("تم شراؤه") }
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visible, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.togglePurchased(item.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.purchased,
                            onCheckedChange = { viewModel.togglePurchased(item.id) }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (item.purchased) TextDecoration.LineThrough else TextDecoration.None
                            )
                            Text(item.category, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(if (item.purchased) "✓ تم" else "متبقي")
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showFinishConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            text = { Text("إنهاء التسوق") },
            icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) }
        )
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("إنهاء جولة التسوق؟") },
            text = { Text("سيتم إفراغ السلة وإلغاء علامات الشراء، وستبقى القائمة الأساسية محفوظة للمرة القادمة.") },
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
private fun PhotoScreen(viewModel: ShoppingViewModel, modifier: Modifier = Modifier) {
    var quickText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null)
        Text("تحويل صورة إلى قائمة", style = MaterialTheme.typography.headlineSmall)
        Text(
            "التقاط صورة وقراءة خط اليد العربي ستُضاف في المرحلة التالية. جهزنا هذه الصفحة الآن حتى تبقى الميزة جزءًا من التطبيق من البداية."
        )
        Text(
            "إدخال سريع مؤقت: اكتب أو الصق عدة مواد، كل مادة في سطر، وستُضاف مباشرة إلى السلة.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = quickText,
            onValueChange = { quickText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            label = { Text("مثال:\nحليب\nتونة\nسائل جلي") }
        )
        Button(
            onClick = {
                viewModel.selectImported(quickText.lines())
                quickText = ""
            },
            enabled = quickText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("إضافة المواد إلى السلة")
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المادة") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("التصنيف") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, category) },
                enabled = name.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
