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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class OneActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOneBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOneBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.one)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        this.supportActionBar?.hide()
        onBackPressedDispatcher.addCallback {
        }
        startProgress()
    }

    private fun startProgress() {
        var progress = 0
        lifecycleScope.launch {
            while (true) {
                progress++
                binding.sP.progress = progress
                delay(20)
                if (binding.sP.progress == 100) {
                    if (AppDataTool.isShowPp) {
                        val intent = Intent(this@OneActivity, MainActivity::class.java)
                        startActivity(intent)
                    } else {
                        val intent = Intent(this@OneActivity, TwoActivity::class.java)
                        startActivity(intent)
                    }

                    finish()
                    break
                }
            }
        }
    }
}