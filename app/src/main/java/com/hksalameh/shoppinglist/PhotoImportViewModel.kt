package com.hksalameh.shoppinglist

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ImportDraftRow(
    val id: Int, val item: ImportItem, val image: Bitmap? = null, val suggestion: String? = null
)

internal class PhotoImportViewModel(application: Application) : AndroidViewModel(application) {
    var knownNames: () -> List<String> = { emptyList() }
    private val ocr = PhotoOcr(application)
    private var nextRowId = 0
    private val draft = mutableStateListOf<ImportDraftRow>()
    val rows: List<ImportDraftRow> get() = draft

    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var shareRequest by mutableStateOf(0)
        private set

    fun receiveShare(uris: List<Uri>) {
        shareRequest++
        if (uris.isEmpty()) {
            showMessage("تعذّر فتح الصورة المشتركة.")
        } else {
            scan(uris)
        }
    }

    fun scan(uris: List<Uri>, cameraFile: File? = null) {
        if (busy || uris.size > 10) {
            cameraFile?.delete()
            showMessage(if (busy) "جاري قراءة الصور السابقة." else "شارك ١٠ صور أو أقل في كل مرة.")
            return
        }
        busy = true
        message = null
        viewModelScope.launch {
            try {
                val vocabulary = knownNames()
                val result = withContext(Dispatchers.IO) { ocr.recognize(uris, vocabulary) }
                for (scan in result.items) {
                    draft.add(ImportDraftRow(nextRowId++, scan.item, scan.image, scan.suggestion))
                }
                message = when {
                    result.items.isEmpty() -> "تعذّرت قراءة المواد. جرّب صورة أوضح أو استخدم الإدخال السريع."
                    result.unreadable > 0 -> "تعذّرت قراءة بعض الصور. راجع المواد المستخرجة."
                    else -> null
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message = "تعذّرت قراءة الصور. جرّب اختيارها مرة ثانية."
            } catch (_: LinkageError) {
                message = "تعذّر تشغيل قارئ الصور على هذا الجهاز."
            } finally {
                cameraFile?.delete()
                busy = false
            }
        }
    }

    fun reviewText(uri: Uri) {
        if (busy) return
        busy = true
        message = null
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    checkNotNull(resolver.openInputStream(uri)).bufferedReader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(65_537)
                        val count = reader.readNCharsCompat(buffer)
                        check(count < buffer.size)
                        String(buffer, 0, count).removePrefix("\uFEFF")
                    }
                }
                val rows = parseShoppingText(text)
                for (item in rows) draft.add(ImportDraftRow(nextRowId++, item))
                if (rows.isEmpty()) message = "الملف لا يحتوي على مواد قابلة للاستيراد."
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message = "تعذّر فتح الملف. اختر ملف TXT بترميز UTF-8."
            } finally {
                busy = false
            }
        }
    }

    private fun java.io.Reader.readNCharsCompat(buffer: CharArray): Int {
        var total = 0
        while (total < buffer.size) {
            val count = read(buffer, total, buffer.size - total)
            if (count == -1) break
            total += count
        }
        return total
    }

    fun edit(id: Int, item: ImportItem) {
        val index = draft.indexOfFirst { it.id == id }
        if (index >= 0) {
            val previous = draft[index]
            draft[index] = previous.copy(item = item, suggestion = previous.suggestion.takeIf {
                previous.item.name == item.name
            })
        }
    }

    fun addRow() {
        draft.add(ImportDraftRow(nextRowId++, ImportItem("")))
    }

    fun remove(id: Int) {
        draft.removeAll { it.id == id }
    }

    fun clear() {
        draft.clear()
        message = null
    }

    fun showMessage(text: String) {
        message = text
    }
}
