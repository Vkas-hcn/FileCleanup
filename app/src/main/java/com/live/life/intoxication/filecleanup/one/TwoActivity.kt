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
import com.live.life.intoxication.filecleanup.databinding.ActivityTwoBinding
import com.live.life.intoxication.filecleanup.one.SetActivity.Companion.jumpToActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class TwoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTwoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTwoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.two)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        this.supportActionBar?.hide()
        onBackPressedDispatcher.addCallback {
        }
        AppDataTool.isShowPp = true
        binding.tvPro.setOnClickListener {
            jumpToActive(this@TwoActivity)
        }
        binding.imgGo1.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}