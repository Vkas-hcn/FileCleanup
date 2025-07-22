package com.live.life.intoxication.filecleanup

object AppDataTool {
    var cleanNum: String = ""
    var jumpType: Int = 0//0:scan,1:Image,2:File
    var isShowPp: Boolean by App.preference.boolean(false)
}