package com.live.life.intoxication.filecleanup

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.live.life.intoxication.filecleanup.databinding.ActivityMainBinding
import com.live.life.intoxication.filecleanup.one.SetActivity
import java.text.DecimalFormat
import kotlin.math.round
import android.content.Context
import android.os.storage.StorageManager
import android.app.usage.StorageStatsManager
import android.widget.Toast
import androidx.core.view.isVisible
import com.live.life.intoxication.filecleanup.PermissionHelper.MANAGE_EXTERNAL_STORAGE_CODE
import com.live.life.intoxication.filecleanup.file.CleanFileActivity
import com.live.life.intoxication.filecleanup.image.CleanPhotosActivity
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var jumpType: Int = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        this.supportActionBar?.hide()



        clickFun()
        updateStorageInfo()
    }


    private fun updateStorageInfo() {
        try {
            // 方法1：使用StorageManager获取更准确的总容量（API 18+）
            val totalBytes = getTotalStorageSpace()
            val availableBytes = getAvailableStorageSpace()
            val usedBytes = totalBytes - availableBytes

            // 计算使用百分比
            val usagePercentage = if (totalBytes > 0) {
                ((usedBytes.toFloat() / totalBytes.toFloat()) * 100).toInt()
            } else 0

            // 更新UI
            updateStorageDisplay(totalBytes, usedBytes, availableBytes, usagePercentage)

        } catch (e: Exception) {
            e.printStackTrace()
            // 设置默认值
            binding.tvFreeNum.text = "0"
            binding.tvUserNum.text = "0"
            binding.tvProNum.text = "0"
            binding.progressUser.progress = 0
        }
    }


    private fun getTotalStorageSpace(): Long {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                // 使用StorageManager获取主存储的总容量
                val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val storageVolume = storageManager.primaryStorageVolume

                // 对于API 24+，可以使用StorageStatsManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val storageStatsManager =
                        getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                    val uuid = StorageManager.UUID_DEFAULT
                    storageStatsManager.getTotalBytes(uuid)
                } else {
                    // 回退到StatFs方法，但使用根目录
                    val stat = StatFs(Environment.getDataDirectory().path)
                    stat.blockCountLong * stat.blockSizeLong
                }
            } else {
                // 旧版本Android的回退方法
                val stat = StatFs(Environment.getExternalStorageDirectory().path)
                stat.blockCountLong * stat.blockSizeLong
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果所有方法都失败，使用原来的方法作为回退
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            stat.blockCountLong * stat.blockSizeLong
        }
    }


    private fun getAvailableStorageSpace(): Long {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val storageStatsManager =
                    getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val uuid = StorageManager.UUID_DEFAULT
                storageStatsManager.getFreeBytes(uuid)
            } else {
                val stat = StatFs(Environment.getExternalStorageDirectory().path)
                stat.availableBlocksLong * stat.blockSizeLong
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        }
    }

    private fun updateStorageDisplay(
        totalBytes: Long,
        usedBytes: Long,
        availableBytes: Long,
        percentage: Int
    ) {
        // 使用统一的格式化方法，保持一致性
        val (freeSize, freeUnit) = formatStorageSize(availableBytes)
        val (usedSize, usedUnit) = formatStorageSize(usedBytes)
        val (totalSize, totalUnit) = formatStorageSize(totalBytes)

        binding.apply {
            tvFreeNum.text = freeSize
            tvGb.text = freeUnit

            tvUserNum.text = usedSize
            tvGb2.text = usedUnit

            tvProNum.text = percentage.toString()

            progressUser.progress = percentage
        }
    }

    private fun formatStorageSize(bytes: Long): Pair<String, String> {
        val gb = 1000L * 1000L * 1000L
        val mb = 1000L * 1000L
        val kb = 1000L
        return when {
            bytes >= gb -> {
                val size = round((bytes.toDouble() / gb) * 100) / 100
                Pair(DecimalFormat("#.#").format(size), "GB")
            }

            bytes >= mb -> {
                val size = round((bytes.toDouble() / mb) * 100) / 100
                Pair(DecimalFormat("#.#").format(size), "MB")
            }

            bytes >= kb -> {
                val size = round((bytes.toDouble() / kb) * 100) / 100
                Pair(DecimalFormat("#.#").format(size), "KB")
            }

            else -> {
                Pair(bytes.toString(), "B")
            }
        }
    }

    private fun clickFun() {
        with(binding) {
            imgSetting.setOnClickListener {
                startActivity(Intent(this@MainActivity, SetActivity::class.java))
            }
            tvClean.setOnClickListener {
                jumpType = 0
                if (PermissionHelper.hasStoragePermission(this@MainActivity)) {
                    jumpToCleanActivity()
                } else {
                    binding.conDialog.isVisible = true
                }
            }
            llPicture.setOnClickListener {
                jumpType = 1
                if (PermissionHelper.hasStoragePermission(this@MainActivity)) {
                    jumpToCleanActivity()
                } else {
                    binding.conDialog.isVisible = true
                }
            }
            llFile.setOnClickListener {
                jumpType = 2
                if (PermissionHelper.hasStoragePermission(this@MainActivity)) {
                    jumpToCleanActivity()
                } else {
                    binding.conDialog.isVisible = true
                }
            }
            conDialog.setOnClickListener { }
            tvCancel.setOnClickListener {
                binding.conDialog.isVisible = false
            }
            tvYes.setOnClickListener {
                binding.conDialog.isVisible = false
                PermissionHelper.requestStoragePermission(this@MainActivity)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCAN_REQUEST_CODE && resultCode == RESULT_OK) {
            updateStorageInfo()
        }
        if (requestCode == MANAGE_EXTERNAL_STORAGE_CODE) {
            if (PermissionHelper.hasStoragePermission(this)) {
                jumpToCleanActivity()
            } else {
                binding.conDialog.isVisible = true
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.STORAGE_PERMISSION_CODE) {
            if (PermissionHelper.hasStoragePermission(this)) {
                jumpToCleanActivity()
            }
        }
    }

    fun jumpToCleanActivity() {
        val cls = when (jumpType) {
            0 -> ScanActivity::class.java
            1 -> CleanPhotosActivity::class.java
            2 -> CleanFileActivity::class.java
            else -> ScanActivity::class.java
        }
        val intent = Intent(this@MainActivity, cls)
        startActivityForResult(intent, SCAN_REQUEST_CODE)
    }

    companion object {
        private const val SCAN_REQUEST_CODE = 200
    }

    override fun onResume() {
        super.onResume()
        updateStorageInfo()
    }
}