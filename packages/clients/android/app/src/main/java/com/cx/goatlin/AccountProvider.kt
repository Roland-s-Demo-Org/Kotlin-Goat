package com.cx.goatlin

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.database.sqlite.SQLiteQueryBuilder
import com.cx.goatlin.helpers.DatabaseHelper


class AccountProvider : ContentProvider() {

    private lateinit var database: DatabaseHelper

    private val ACCOUNTS = 1
    private val ACCOUNTS_ID = 2


    private val sURIMatcher = UriMatcher(UriMatcher.NO_MATCH)

    init {
        sURIMatcher.addURI(AUTHORITY, ACCOUNTS_TABLE, ACCOUNTS)
        sURIMatcher.addURI(AUTHORITY, ACCOUNTS_TABLE + "/#",
                ACCOUNTS_ID)
    }

    override fun onCreate(): Boolean {
        this.database = DatabaseHelper(context)
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        // Verify caller is from the same application (defense-in-depth)
        val callingPackage = callingPackage
        if (callingPackage != null && callingPackage != context?.packageName) {
            throw SecurityException("Access denied: AccountProvider is private to this application")
        }

        // Restrict projection to prevent password exposure in collection queries
        val safeProjection = if (projection == null) {
            // Default projection excludes password for collection queries
            arrayOf("id AS _id", "username")
        } else {
            // Filter out password column from requested projection for collection queries
            projection.filter { it != "password" && !it.contains("password", ignoreCase = true) }
                    .toTypedArray()
        }

        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.tables = ACCOUNTS_TABLE

        val uriType = sURIMatcher.match(uri)

        when (uriType) {
            ACCOUNTS_ID -> {
                queryBuilder.appendWhere("id = " + uri.lastPathSegment)
                // For specific account queries, allow full projection including password
                // since this is used internally by the app for authentication
                val cursor = queryBuilder.query(this.database.readableDatabase,
                        projection, selection, selectionArgs, null, null,
                        sortOrder)
                cursor.setNotificationUri(context?.contentResolver, uri)
                return cursor
            }
            ACCOUNTS -> {
                // For collection queries, use restricted projection
                val cursor = queryBuilder.query(this.database.readableDatabase,
                        safeProjection, selection, selectionArgs, null, null,
                        sortOrder)
                cursor.setNotificationUri(context?.contentResolver, uri)
                return cursor
            }
            else -> throw IllegalArgumentException("Unknown URI")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // Verify caller is from the same application (defense-in-depth)
        val callingPackage = callingPackage
        if (callingPackage != null && callingPackage != context?.packageName) {
            throw SecurityException("Access denied: AccountProvider is private to this application")
        }

        val uriType = sURIMatcher.match(uri)

        val sqlDB = this.database.writableDatabase

        val id: Long
        when (uriType) {
            ACCOUNTS -> id = sqlDB.insert(ACCOUNTS_TABLE, null, values)
            else -> throw IllegalArgumentException("Unknown URI: " + uri)
        }
        context?.contentResolver?.notifyChange(uri, null)
        return Uri.parse(ACCOUNTS_TABLE + "/" + id)
    }


    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun getType(uri: Uri): String? {
        TODO("Implement this to handle requests for the MIME type of the data" +
                "at the given URI")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("Not yet implemented")
    }


    companion object {
        private val AUTHORITY = "com.cx.goatlin.accounts"
        private val ACCOUNTS_TABLE = "Accounts"
        val CONTENT_URI : Uri = Uri.parse("content://" + AUTHORITY + "/" +
                ACCOUNTS_TABLE)
        private val DATABASE_NAME = "data"
    }
}
