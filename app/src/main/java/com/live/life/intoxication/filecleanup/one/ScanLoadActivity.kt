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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


class ScanLoadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoadBinding
    var progressJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.load)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        this.supportActionBar?.hide()
        onBackPressedDispatcher.addCallback {
        }
        binding.tvBack.setOnClickListener {
            progressJob?.cancel()
            finish()
        }
        var progress = 0
        progressJob = lifecycleScope.launch {
            while (true) {
                progress++
                binding.pg.progress = progress
                delay(20)
                if (binding.pg.progress >= 100) {
                    val intent = Intent(this@ScanLoadActivity, ScanResultActivity::class.java)
                    startActivity(intent)
                    finish()
                    break
                }
            }
        }
    }
}