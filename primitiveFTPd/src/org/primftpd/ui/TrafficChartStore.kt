package org.primftpd.ui

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.slf4j.LoggerFactory

/**
 * A single per-second traffic-chart sample. Timestamps are Unix timestamps in seconds.
 */
data class TrafficChartSample(
    val timestampSeconds: Long,
    val ftpBytesPerSecond: Long,
    val sftpBytesPerSecond: Long,
)

/**
 * Persists the traffic-chart history in a small SQLite database.
 *
 * The chart keeps at most [MAX_AGE_SECONDS] of history. Samples are stored once per second, so the
 * database contains at most 259,200 rows. The in-memory list held by [NetworkViewModel] is pruned
 * with the same cutoff; the database is pruned periodically.
 */
class TrafficChartStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val logger = LoggerFactory.getLogger(javaClass)

    private val openHelper = object : SQLiteOpenHelper(
        appContext,
        DATABASE_NAME,
        null,
        DATABASE_VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(SQL_CREATE_TABLE)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }
    }

    @Synchronized
    fun load(maxAgeSeconds: Long = MAX_AGE_SECONDS): List<TrafficChartSample> {
        val cutoff = currentTimestampSeconds() - maxAgeSeconds
        val result = ArrayList<TrafficChartSample>()
        try {
            openHelper.readableDatabase.query(
                TABLE_NAME,
                COLUMNS,
                "$COLUMN_TIMESTAMP >= ?",
                arrayOf(cutoff.toString()),
                null,
                null,
                "$COLUMN_TIMESTAMP ASC",
            ).use { cursor ->
                val timestampColumn = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
                val ftpColumn = cursor.getColumnIndexOrThrow(COLUMN_FTP_BYTES)
                val sftpColumn = cursor.getColumnIndexOrThrow(COLUMN_SFTP_BYTES)
                while (cursor.moveToNext()) {
                    result.add(
                        TrafficChartSample(
                            timestampSeconds = cursor.getLong(timestampColumn),
                            ftpBytesPerSecond = cursor.getLong(ftpColumn),
                            sftpBytesPerSecond = cursor.getLong(sftpColumn),
                        )
                    )
                }
            }
        } catch (exception: RuntimeException) {
            logger.warn("Could not load traffic-chart history", exception)
        }
        return result
    }

    @Synchronized
    fun append(sample: TrafficChartSample) {
        try {
            val values = ContentValues().apply {
                put(COLUMN_TIMESTAMP, sample.timestampSeconds)
                put(COLUMN_FTP_BYTES, sample.ftpBytesPerSecond)
                put(COLUMN_SFTP_BYTES, sample.sftpBytesPerSecond)
            }
            openHelper.writableDatabase.insertWithOnConflict(
                TABLE_NAME,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } catch (exception: RuntimeException) {
            logger.warn("Could not persist traffic-chart sample: {}", sample, exception)
        }
    }

    @Synchronized
    fun prune(cutoffTimestampSeconds: Long): Int {
        return try {
            openHelper.writableDatabase.delete(
                TABLE_NAME,
                "$COLUMN_TIMESTAMP < ?",
                arrayOf(cutoffTimestampSeconds.toString()),
            )
        } catch (exception: RuntimeException) {
            logger.warn("Could not prune traffic-chart history", exception)
            0
        }
    }

    @Synchronized
    fun clear() {
        try {
            openHelper.writableDatabase.delete(TABLE_NAME, null, null)
        } catch (exception: RuntimeException) {
            logger.warn("Could not clear traffic-chart history", exception)
        }
    }

    @Synchronized
    fun sampleCount(): Long {
        return try {
            openHelper.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_NAME",
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
        } catch (exception: RuntimeException) {
            logger.warn("Could not count traffic-chart samples", exception)
            0L
        }
    }

    companion object {
        /** Keep up to three days of per-second samples. */
        const val MAX_AGE_SECONDS: Long = 3L * 24L * 60L * 60L

        private const val DATABASE_NAME = "traffic_chart.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "traffic_samples"
        private const val COLUMN_TIMESTAMP = "timestamp_seconds"
        private const val COLUMN_FTP_BYTES = "ftp_bytes_per_second"
        private const val COLUMN_SFTP_BYTES = "sftp_bytes_per_second"
        private val COLUMNS = arrayOf(COLUMN_TIMESTAMP, COLUMN_FTP_BYTES, COLUMN_SFTP_BYTES)
        private val SQL_CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_TIMESTAMP INTEGER PRIMARY KEY NOT NULL,
                $COLUMN_FTP_BYTES INTEGER NOT NULL,
                $COLUMN_SFTP_BYTES INTEGER NOT NULL
            )
        """.trimIndent()

        @Volatile
        private var instance: TrafficChartStore? = null

        @JvmStatic
        fun getInstance(context: Context): TrafficChartStore =
            instance ?: synchronized(this) {
                instance ?: TrafficChartStore(context).also { instance = it }
            }
    }
}

private fun currentTimestampSeconds(): Long = System.currentTimeMillis() / 1000L
