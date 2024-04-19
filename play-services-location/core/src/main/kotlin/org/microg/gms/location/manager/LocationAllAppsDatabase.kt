/*
 * SPDX-FileCopyrightText: 2023 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.location.manager

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.util.Log
import androidx.core.content.contentValuesOf
import androidx.core.database.getIntOrNull

class LocationAllAppsDatabase(context: Context) : SQLiteOpenHelper(context, "location_apps.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_APPS($FIELD_PACKAGE TEXT NOT NULL, $FIELD_TYPE INTEGER NOT NULL);")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS ${TABLE_APPS}_index ON ${TABLE_APPS}(${FIELD_PACKAGE});")
    }

    fun insertIfMissing(packageName: String, autoClose: Boolean, vararg pairs: Pair<String, Any?>) {
        val values = contentValuesOf(FIELD_PACKAGE to packageName, *pairs)
        writableDatabase.insertWithOnConflict(TABLE_APPS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        if(autoClose) {
            close()
        }
    }

    fun insertOrUpdateApp(packageName: String, autoClose: Boolean, vararg pairs: Pair<String, Any?>) {
        val values = contentValuesOf(FIELD_PACKAGE to packageName, *pairs)
        if (writableDatabase.insertWithOnConflict(TABLE_APPS, null, values, SQLiteDatabase.CONFLICT_IGNORE) < 0) {
            writableDatabase.update(TABLE_APPS, values, "$FIELD_PACKAGE = ?", arrayOf(packageName))
        }
        if(autoClose) {
            close()
        }
    }

    fun listAppsByPackageName(): List<Pair<String, Int>> {
        val res = arrayListOf<Pair<String, Int>>()
        readableDatabase.query(TABLE_APPS, arrayOf(FIELD_PACKAGE, FIELD_TYPE), null, null, null, null, "$FIELD_PACKAGE ASC", null).apply {
            while (moveToNext()) {
                res.add(getString(0) to getInt(1))
            }
            close()
        }
        return res
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)

    }

    companion object {
        const val TABLE_APPS = "apps"
        const val FIELD_PACKAGE = "package"
        const val FIELD_TYPE = "location_type"
        const val TYPE_NO_LOCATION = 0
        const val TYPE_FIXED_LOCATION = 1
        const val TYPE_COARSE_LOCATION = 2
        const val TYPE_FINE_LOCATION = 3
        val TYPE_LABELS = arrayOf("no_location", "fixed_location", "coarse_location", "fine_location")
    }
}