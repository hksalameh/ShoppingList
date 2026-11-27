package com.hksalameh.shoppinglist

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

@Composable
internal fun PhotoScreen(
    shopping: ShoppingViewModel,
    photo: PhotoImportViewModel,
    modifier: Modifier = Modifier,
    onOpenCart: () -> Unit
) {
    val context = LocalContext.current
    val cameraDirectory = File(context.cacheDir, "shopping_photos")
    var cameraFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var quickText by rememberSaveable { mutableStateOf("") }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photo.scan(listOf(uri))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = cameraFileName?.let { File(cameraDirectory, it) }
        cameraFileName = null
        if (file != null) {
            if (saved) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
                photo.scan(listOf(uri), file)
            } else {
                file.delete()
            }
        }
    }
    val cartItems = shopping.items.filter { it.selected }
        .sortedByDescending { it.purchased }
        .associateBy { itemKey(it.name) }
    val proposedRows = photo.rows.map { it.item }
    val approvable = uniqueImportItems(proposedRows).filter { cartItems[itemKey(it.name)]?.purchased != true }
    val duplicateCounts = proposedRows.groupingBy { itemKey(it.name) }.eachCount()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("استيراد من صورة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "صوّر القائمة أو اخترها من المعرض. من واتساب، شارك الصورة مع «مشترياتي».",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                try {
                                    cameraDirectory.mkdirs()
                                    val file = File.createTempFile("list-", ".jpg", cameraDirectory)
                                    cameraFileName = file.name
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
                                    camera.launch(uri)
                                } catch (_: ActivityNotFoundException) {
                                    cameraFileName?.let { File(cameraDirectory, it).delete() }
                                    cameraFileName = null
                                    photo.showMessage("الكاميرا غير متاحة. اختر صورة من المعرض.")
                                } catch (_: IOException) {
                                    photo.showMessage("تعذّر تجهيز ملف الصورة.")
                                }
                            },
                            enabled = !photo.busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Text("الكاميرا", Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(
                            onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !photo.busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Text("المعرض", Modifier.padding(start = 6.dp))
                        }
                    }
                    if (photo.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("جاري قراءة الصور على الجهاز…")
                    }
                    photo.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        if (photo.rows.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مراجعة المواد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = photo::clear, enabled = !photo.busy) { Text("إلغاء المراجعة") }
                }
            }
            items(photo.rows, key = { it.id }) { row ->
                val key = itemKey(row.item.name)
                val cartItem = cartItems[key]
                val note = when {
                    key.isBlank() -> null
                    cartItem?.purchased == true -> "تم شراؤها، وستبقى في السلة مرة واحدة."
                    (duplicateCounts[key] ?: 0) > 1 -> "مكررة. ستُعتمد مرة واحدة بأول كمية مكتوبة."
                    cartItem != null -> "موجودة في السلة. سيتم تحديث كميتها عند إدخال كمية."
                    else -> null
                }
                ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = row.item.name,
                                onValueChange = { photo.edit(row.id, row.item.copy(name = it)) },
                                label = { Text("اسم المادة") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { photo.remove(row.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف ${row.item.name}")
                            }
                        }
                        OutlinedTextField(
                            value = row.item.quantity,
                            onValueChange = { photo.edit(row.id, row.item.copy(quantity = it)) },
                            label = { Text("الكمية") },
                            placeholder = { Text("مثال ٢ كيلو") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item {
                OutlinedButton(onClick = photo::addRow, modifier = Modifier.fillMaxWidth()) {
                    Text("إضافة مادة للمراجعة")
                }
                Button(
                    onClick = {
                        shopping.selectImported(approvable)
                        photo.clear()
                        onOpenCart()
                    },
                    enabled = approvable.isNotEmpty() && !photo.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("اعتماد ${approvable.size} للسلة", Modifier.padding(start = 6.dp))
                }
            }
        } else {
            item {
                ElevatedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("إدخال سريع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = quickText,
                            onValueChange = { quickText = it },
                            label = { Text("اكتب كل مادة في سطر") },
                            placeholder = { Text("حليب ٢ لتر\nتونة\nرز بسمتي ١ كيلو") },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val parsed = parseShoppingText(quickText)
                        Button(
                            onClick = {
                                val count = shopping.selectImported(parsed)
                                quickText = ""
                                if (count > 0) onOpenCart()
                                else photo.showMessage("المواد المدخلة موجودة بالفعل ضمن المشتريات.")
                            },
                            enabled = parsed.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("إضافة المواد إلى السلة") }
                    }
                }
            }
        }
    }
}
