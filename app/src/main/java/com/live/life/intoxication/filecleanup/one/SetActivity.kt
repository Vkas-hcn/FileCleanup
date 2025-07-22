package com.live.life.intoxication.filecleanup.one

import com.live.life.intoxication.filecleanup.MainActivity
import com.live.life.intoxication.filecleanup.databinding.ActivityOneBinding
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.live.life.intoxication.filecleanup.R
import com.live.life.intoxication.filecleanup.databinding.ActivitySetBinding
import com.live.life.intoxication.filecleanup.databinding.ActivityTwoBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class SetActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetBinding

    companion object {
        fun jumpToActive(activity: AppCompatActivity) {
            //TODO: 2023/5/23 跳转网页
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
            activity.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.set)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        this.supportActionBar?.hide()
        with(binding) {
            textViewId.setOnClickListener {
                finish()
            }
            atvShare.setOnClickListener {
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(
                    Intent.EXTRA_TEXT,
                    "https://play.google.com/store/apps/details?id=${this@SetActivity.packageName}"
                )
                startActivity(Intent.createChooser(intent, "Share"))
            }
            atvNet.setOnClickListener {
                jumpToActive(this@SetActivity)
            }
        }
    }
}