package com.example.bsrtmp

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val pref = getSharedPreferences("DCCL_CONFIG", MODE_PRIVATE)
        val etPublish = findViewById<EditText>(R.id.etPublishUrl)
        val etPlay = findViewById<EditText>(R.id.etPlayUrl)
        val cbBackground = findViewById<CheckBox>(R.id.cbBackgroundStream)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // 저장된 값 불러오기
        etPublish.setText(pref.getString("publish_url", ""))
        etPlay.setText(pref.getString("play_url", ""))
        cbBackground.isChecked = pref.getBoolean("allow_background", true)

        btnSave.setOnClickListener {
            pref.edit().apply {
                putString("publish_url", etPublish.text.toString())
                putString("play_url", etPlay.text.toString())
                putBoolean("allow_background", cbBackground.isChecked)
                apply()
            }
            Toast.makeText(this, "저장되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }

    }
}