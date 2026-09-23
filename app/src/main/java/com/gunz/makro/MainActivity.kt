package com.gunz.makro

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var btnAktivasi: Button
    private lateinit var btnKecepatan: Button
    private lateinit var btnMulai: Button
    private lateinit var tvStatusAksesibilitas: TextView
    private lateinit var rvSelectedApps: RecyclerView
    private lateinit var adapter: SelectedAppAdapter

    companion object {
        const val REQ_APP_PICKER = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnAktivasi = findViewById(R.id.btnAktivasi)
        btnKecepatan = findViewById(R.id.btnKecepatan)
        btnMulai = findViewById(R.id.btnMulai)
        tvStatusAksesibilitas = findViewById(R.id.tvStatusAksesibilitas)
        rvSelectedApps = findViewById(R.id.rvSelectedApps)

        setupRecyclerView()

        btnAktivasi.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnKecepatan.setOnClickListener {
            startActivity(Intent(this, SpeedSettingsActivity::class.java))
        }

        btnMulai.setOnClickListener {
            mulaiSekarang()
        }

        refreshUi()
    }

    private fun setupRecyclerView() {
        val currentApps = Prefs.getTargetApps(this).toMutableList()

        adapter = SelectedAppAdapter(
            packageManager = packageManager,
            apps = currentApps,
            onAppClick = { app -> showAppOptionsDialog(app) },
            onAddClick = {
                startActivityForResult(Intent(this, AppListActivity::class.java), REQ_APP_PICKER)
            },
            onOrderChanged = { newOrder ->
                Prefs.reorderTargetApps(this, newOrder)
            }
        )

        rvSelectedApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvSelectedApps.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return if (adapter.isDraggable(viewHolder.bindingAdapterPosition)) {
                    makeMovementFlags(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0)
                } else {
                    makeMovementFlags(0, 0)
                }
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (!adapter.isDraggable(from) || !adapter.isDraggable(to)) return false
                adapter.onMoveItems(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Tidak dipakai, hanya drag reorder
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                adapter.onDragFinished()
            }
        })
        touchHelper.attachToRecyclerView(rvSelectedApps)
    }

    private fun showAppOptionsDialog(app: TargetApp) {
        val options = arrayOf("Ganti aplikasi", "Hapus dari daftar")
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, AppListActivity::class.java)
                        intent.putExtra(AppListActivity.EXTRA_REPLACE_PACKAGE, app.packageName)
                        startActivityForResult(intent, REQ_APP_PICKER)
                    }
                    1 -> {
                        Prefs.removeTargetApp(this, app.packageName)
                        refreshUi()
                    }
                }
            }
            .show()
    }

    private fun mulaiSekarang() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Aktifkan dulu layanan aksesibilitas", Toast.LENGTH_SHORT).show()
            return
        }
        val targetApps = Prefs.getTargetApps(this)
        if (targetApps.isEmpty()) {
            Toast.makeText(this, "Pilih minimal 1 aplikasi target dulu", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Izinkan tampil di atas aplikasi lain, lalu tekan Mulai lagi", Toast.LENGTH_LONG).show()
            return
        }

        // Masuk otomatis ke aplikasi target pertama, lalu tampilkan mode mengambang di atasnya
        val firstApp = targetApps.first()
        val launchIntent = packageManager.getLaunchIntentForPackage(firstApp.packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "Tidak bisa membuka ${firstApp.label}", Toast.LENGTH_SHORT).show()
        }

        startService(Intent(this, FloatingService::class.java))
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_APP_PICKER) {
            refreshUi()
        }
    }

    private fun refreshUi() {
        val aksesibilitasAktif = isAccessibilityServiceEnabled()
        tvStatusAksesibilitas.text = if (aksesibilitasAktif) {
            "Status Aktivasi: aktif"
        } else {
            "Status Aktivasi: belum aktif"
        }

        val apps = Prefs.getTargetApps(this)
        adapter.setApps(apps)

        btnMulai.isEnabled = aksesibilitasAktif && apps.isNotEmpty()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, MacroAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val componentName = ComponentName.unflattenFromString(splitter.next())
            if (componentName != null && componentName == expectedComponent) {
                return true
            }
        }
        return false
    }
}
