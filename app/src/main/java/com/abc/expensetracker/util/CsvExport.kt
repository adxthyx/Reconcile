package com.abc.expensetracker.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.abc.expensetracker.data.Account
import com.abc.expensetracker.data.Category
import com.abc.expensetracker.data.Txn
import java.io.File

object CsvExport {

    fun buildCsv(txns: List<Txn>, categories: List<Category>, accounts: List<Account>): String {
        val catById = categories.associateBy { it.id }
        val accById = accounts.associateBy { it.id }
        val sb = StringBuilder("date,amount,direction,merchant,category,account,source,note,ref,tags,excluded,split_owed,split_with\n")
        for (t in txns) {
            sb.append(Dates.dayYear(t.timestamp)).append(',')
                .append(t.amountPaise / 100.0).append(',')
                .append(t.direction.name).append(',')
                .append(esc(t.merchant)).append(',')
                .append(esc(catById[t.categoryId]?.name)).append(',')
                .append(esc(accById[t.accountId]?.name)).append(',')
                .append(t.source.name).append(',')
                .append(esc(t.note)).append(',')
                .append(esc(t.refId)).append(',')
                .append(esc(t.tags)).append(',')
                .append(if (t.excluded) "yes" else "no").append(',')
                .append(t.splitOwedPaise?.let { it / 100.0 } ?: "").append(',')
                .append(esc(t.splitWith)).append('\n')
        }
        return sb.toString()
    }

    private fun esc(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        return if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s
    }

    /** Writes the CSV to app-private storage and opens a share sheet. */
    fun share(context: Context, csv: String) {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "kharcha-export-${System.currentTimeMillis()}.csv")
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export transactions"))
    }
}
