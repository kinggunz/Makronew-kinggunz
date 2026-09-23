package com.gunz.makro

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppListActivity : AppCompatActivity() {

    companion object {
        // Jika diisi, artinya mode "ganti aplikasi ini", bukan "tambah baru"
        const val EXTRA_REPLACE_PACKAGE = "extra_replace_package"
    }

    private var replacePackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        replacePackage = intent.getStringExtra(EXTRA_REPLACE_PACKAGE)

        val rv: RecyclerView = findViewById(R.id.rvApps)
        rv.layoutManager = LinearLayoutManager(this)

        val apps = getInstalledLaunchableApps()
        rv.adapter = AppAdapter(apps) { selected ->
            confirmSelection(selected)
        }
    }

    private fun getInstalledLaunchableApps(): List<AppEntry> {
        val pm: PackageManager = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolvedApps: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        return resolvedApps
            .filter { it.activityInfo.packageName != packageName }
            .map { resolveInfo ->
                AppEntry(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(pm).toString(),
                    icon = resolveInfo.loadIcon(pm)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun confirmSelection(app: AppEntry) {
        val isReplace = replacePackage != null
        val pesan = if (isReplace) {
            "Ganti aplikasi target menjadi \"${app.label}\"?"
        } else {
            "Tambahkan \"${app.label}\" sebagai aplikasi target?"
        }

        AlertDialog.Builder(this)
            .setTitle("Konfirmasi")
            .setMessage(pesan)
            .setPositiveButton("Konfirmasi") { _, _ ->
                if (isReplace) {
                    Prefs.replaceTargetApp(this, replacePackage!!, app.packageName, app.label)
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    val added = Prefs.addTargetApp(this, app.packageName, app.label)
                    if (!added) {
                        Toast.makeText(this, "Aplikasi ini sudah ada di daftar", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
