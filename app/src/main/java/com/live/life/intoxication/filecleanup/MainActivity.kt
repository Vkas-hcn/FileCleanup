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
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes

            // 计算使用百分比
            val usagePercentage = ((usedBytes.toFloat() / totalBytes.toFloat()) * 100).toInt()

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
        // 使用二进制计算（1024）保持与Android系统一致
        val gb = 1024L * 1024L * 1024L
        val mb = 1024L * 1024L
        val kb = 1024L

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