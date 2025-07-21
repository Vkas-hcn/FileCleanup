package com.live.life.intoxication.filecleanup.one

import com.live.life.intoxication.filecleanup.MainActivity
import com.live.life.intoxication.filecleanup.databinding.ActivityOneBinding
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.live.life.intoxication.filecleanup.AppDataTool
import com.live.life.intoxication.filecleanup.R
import com.live.life.intoxication.filecleanup.databinding.ActivityLoadBinding
import com.live.life.intoxication.filecleanup.databinding.ActivityScanBinding
import com.live.life.intoxication.filecleanup.databinding.ActivityScanResultBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class ScanResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.result)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        this.supportActionBar?.hide()
        binding.textViewId.setOnClickListener {
            finish()
        }
        binding.tvSaveData.text = "Saved ${AppDataTool.cleanNum} space for you"
    }
}