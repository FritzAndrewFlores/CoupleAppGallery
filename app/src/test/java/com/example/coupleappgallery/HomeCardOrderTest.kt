package com.example.coupleappgallery

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeCardOrderTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
    }

    @Test
    fun loadCardOrder_whenNoSavedPreference_returnsDefaultOrder() {
        val order = loadHomeCardOrder(fakePrefs, "user123")
        assertEquals(DefaultHomeCardOrder, order)
    }

    @Test
    fun saveAndLoadCardOrder_persistsCustomArrangement() {
        val customOrder = listOf(
            HomeCardType.NOTE,
            HomeCardType.REQUESTS,
            HomeCardType.MOOD,
            HomeCardType.WISHLIST
        )

        saveHomeCardOrder(fakePrefs, "user123", customOrder)
        val loaded = loadHomeCardOrder(fakePrefs, "user123")

        assertEquals(customOrder, loaded)
    }

    @Test
    fun loadCardOrder_whenInvalidItemsInSavedString_filtersAndAppendsMissing() {
        // Suppose a corrupted or removed enum was saved
        fakePrefs.putString("card_order_user123", "NOTE,INVALID_ENUM_NAME,MOOD")

        val loaded = loadHomeCardOrder(fakePrefs, "user123")

        // Should contain NOTE and MOOD first, followed by missing WISHLIST and REQUESTS
        assertEquals(HomeCardType.NOTE, loaded[0])
        assertEquals(HomeCardType.MOOD, loaded[1])
        assertTrue(loaded.contains(HomeCardType.WISHLIST))
        assertTrue(loaded.contains(HomeCardType.REQUESTS))
        assertEquals(4, loaded.size)
    }

    @Test
    fun loadCardOrder_differentUsers_areSeparated() {
        val user1Order = listOf(HomeCardType.NOTE, HomeCardType.MOOD, HomeCardType.REQUESTS, HomeCardType.WISHLIST)
        val user2Order = listOf(HomeCardType.MOOD, HomeCardType.WISHLIST, HomeCardType.NOTE, HomeCardType.REQUESTS)

        saveHomeCardOrder(fakePrefs, "user1", user1Order)
        saveHomeCardOrder(fakePrefs, "user2", user2Order)

        assertEquals(user1Order, loadHomeCardOrder(fakePrefs, "user1"))
        assertEquals(user2Order, loadHomeCardOrder(fakePrefs, "user2"))
    }
}

/**
 * Lightweight in-memory fake of SharedPreferences for local unit testing.
 */
class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    fun putString(key: String, value: String?) {
        map[key] = value
    }

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (map[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(map)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val backingMap: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) pending[key] = values
            return this
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = this
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clearRequested) backingMap.clear()
            for ((k, v) in pending) {
                if (v === this) backingMap.remove(k) else backingMap[k] = v
            }
        }
    }
}
