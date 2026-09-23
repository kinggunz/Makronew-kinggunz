package com.gunz.makro

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SpeedSettingsActivity : AppCompatActivity() {

    private lateinit var seekSpeed: SeekBar
    private lateinit var tvSpeedValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speed_settings)

        seekSpeed = findViewById(R.id.seekSpeed)
        tvSpeedValue = findViewById(R.id.tvSpeedValue)

        val currentInterval = Prefs.getTapIntervalMs(this)
        val currentProgress = Prefs.intervalMsToSliderProgress(currentInterval)
        seekSpeed.progress = currentProgress
        updateLabel(currentInterval)

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val intervalMs = Prefs.sliderProgressToIntervalMs(progress)
                updateLabel(intervalMs)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        findViewById<android.widget.Button>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<android.widget.Button>(R.id.btnConfirm).setOnClickListener {
            val intervalMs = Prefs.sliderProgressToIntervalMs(seekSpeed.progress)
            Prefs.setTapIntervalMs(this, intervalMs)
            finish()
        }
    }

    private fun updateLabel(intervalMs: Int) {
        tvSpeedValue.text = "$intervalMs ms / ketuk"
    }
}
