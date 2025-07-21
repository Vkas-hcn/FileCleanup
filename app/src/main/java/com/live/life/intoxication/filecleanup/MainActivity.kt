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
import com.live.life.intoxication.filecleanup.utils.PermissionHelper
import java.text.DecimalFormat
import kotlin.math.round
import android.content.Context
import android.os.storage.StorageManager
import android.app.usage.StorageStatsManager
import android.text.format.Formatter

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

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

        // 检查权限
        if (!PermissionHelper.hasStoragePermission(this)) {
            PermissionHelper.requestStoragePermission(this)
        }

        clickFun()
        updateStorageInfo()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.STORAGE_PERMISSION_CODE) {
            if (PermissionHelper.hasStoragePermission(this)) {
                updateStorageInfo()
            }
        }
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

    /**
     * 获取设备总存储空间（更准确的方法）
     */
    private fun getTotalStorageSpace(): Long {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                // 使用StorageManager获取主存储的总容量
                val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val storageVolume = storageManager.primaryStorageVolume

                // 对于API 24+，可以使用StorageStatsManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val storageStatsManager = getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
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

    /**
     * 获取可用存储空间
     */
    private fun getAvailableStorageSpace(): Long {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val storageStatsManager = getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
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

    private fun updateStorageDisplay(totalBytes: Long, usedBytes: Long, availableBytes: Long, percentage: Int) {
        // 使用统一的格式化方法，保持一致性
        val (freeSize, freeUnit) = formatStorageSize(availableBytes)
        val (usedSize, usedUnit) = formatStorageSize(usedBytes)
        val (totalSize, totalUnit) = formatStorageSize(totalBytes)

        binding.apply {
            // 显示可用空间
            tvFreeNum.text = freeSize
            tvGb.text = freeUnit

            // 显示已用空间
            tvUserNum.text = usedSize
            tvGb2.text = usedUnit

            // 显示使用百分比
            tvProNum.text = percentage.toString()

            // 设置进度条
            progressUser.progress = percentage
        }

        // 添加调试日志，查看实际容量
        Log.d("StorageInfo", "Total: ${formatStorageSize(totalBytes).first}${formatStorageSize(totalBytes).second}")
        Log.d("StorageInfo", "Used: ${formatStorageSize(usedBytes).first}${formatStorageSize(usedBytes).second}")
        Log.d("StorageInfo", "Free: ${formatStorageSize(availableBytes).first}${formatStorageSize(availableBytes).second}")
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
                val intent = Intent(this@MainActivity, ScanActivity::class.java)
                startActivityForResult(intent, SCAN_REQUEST_CODE)
            }
            llPicture.setOnClickListener {
                // 图片清理功能
                val intent = Intent(this@MainActivity, ScanActivity::class.java)
                intent.putExtra("scan_type", "pictures")
                startActivityForResult(intent, SCAN_REQUEST_CODE)
            }
            llFile.setOnClickListener {
                // 文件清理功能
                val intent = Intent(this@MainActivity, ScanActivity::class.java)
                intent.putExtra("scan_type", "files")
                startActivityForResult(intent, SCAN_REQUEST_CODE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCAN_REQUEST_CODE && resultCode == RESULT_OK) {
            // 扫描清理完成后，更新存储信息
            updateStorageInfo()
        }
    }

    companion object {
        private const val SCAN_REQUEST_CODE = 200
    }

    override fun onResume() {
        super.onResume()
        updateStorageInfo()
    }
}