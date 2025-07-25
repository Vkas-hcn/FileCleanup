package com.live.life.intoxication.filecleanup

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class Preference(context: Context, private val name: String = "default", private val mode: Int = Context.MODE_PRIVATE) {

    private val preferences: SharedPreferences = context.getSharedPreferences(name, mode)


    fun boolean(default: Boolean = false): ReadWriteProperty<Any?, Boolean> =
        object : PreferenceDelegate<Boolean>(default) {
            override fun getValueFromPreferences(key: String): Boolean = preferences.getBoolean(key, default)
            override fun setValueToPreferences(key: String, value: Boolean) = preferences.edit().putBoolean(key, value).apply()
        }


    fun int(default: Int = 0): ReadWriteProperty<Any?, Int> =
        object : PreferenceDelegate<Int>(default) {
            override fun getValueFromPreferences(key: String): Int = preferences.getInt(key, default)
            override fun setValueToPreferences(key: String, value: Int) = preferences.edit().putInt(key, value).apply()
        }


    fun long(default: Long = 0L): ReadWriteProperty<Any?, Long> =
        object : PreferenceDelegate<Long>(default) {
            override fun getValueFromPreferences(key: String): Long = preferences.getLong(key, default)
            override fun setValueToPreferences(key: String, value: Long) = preferences.edit().putLong(key, value).apply()
        }


    fun float(default: Float = 0f): ReadWriteProperty<Any?, Float> =
        object : PreferenceDelegate<Float>(default) {
            override fun getValueFromPreferences(key: String): Float = preferences.getFloat(key, default)
            override fun setValueToPreferences(key: String, value: Float) = preferences.edit().putFloat(key, value).apply()
        }


    fun string(default: String = ""): ReadWriteProperty<Any?, String> =
        object : PreferenceDelegate<String>(default) {
            override fun getValueFromPreferences(key: String): String = preferences.getString(key, default) ?: default
            override fun setValueToPreferences(key: String, value: String) = preferences.edit().putString(key, value).apply()
        }


    private abstract class PreferenceDelegate<T>(private val defaultValue: T) : ReadWriteProperty<Any?, T> {
        abstract fun getValueFromPreferences(key: String): T
        abstract fun setValueToPreferences(key: String, value: T)

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return getValueFromPreferences(property.name)
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            setValueToPreferences(property.name, value)
        }
    }
}
