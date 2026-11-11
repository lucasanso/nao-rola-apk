package com.lucasanso.naorola.data.db

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One block-screen interaction. Stores no content: only time, surface and outcome. */
@Entity(tableName = "block_events", indices = [Index("timeMillis")])
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val surface: String,
    val platform: String,
    val outcome: String,
)

/** A period with short-video content on screen that was not blocked. */
@Entity(tableName = "exposures", indices = [Index("startMillis")])
data class ExposureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surface: String,
    val platform: String,
    val startMillis: Long,
    val endMillis: Long,
)

/** Service connected/disconnected and similar facts about the protection itself. */
@Entity(tableName = "protection_events", indices = [Index("timeMillis")])
data class ProtectionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val type: String,
)

/** Time inside an app on a closed day, saved because Android keeps detailed usage events briefly. */
@Entity(tableName = "daily_app_usage", primaryKeys = ["epochDay", "packageName"])
data class DailyAppUsageEntity(
    val epochDay: Long,
    val packageName: String,
    val foregroundMillis: Long,
)

data class PlatformCount(
    @ColumnInfo(name = "platform") val platform: String,
    @ColumnInfo(name = "count") val count: Int,
)

data class PlatformDuration(
    @ColumnInfo(name = "platform") val platform: String,
    @ColumnInfo(name = "total") val totalMillis: Long,
)

@Dao
interface StatsDao {
    @Insert suspend fun insertEvent(event: BlockEventEntity)

    @Insert suspend fun insertExposure(exposure: ExposureEntity)

    @Insert suspend fun insertProtectionEvent(event: ProtectionEventEntity)

    @Query(
        "SELECT platform, COUNT(*) AS count FROM block_events " +
            "WHERE outcome = :outcome AND timeMillis >= :since GROUP BY platform",
    )
    fun countByPlatformSince(outcome: String, since: Long): Flow<List<PlatformCount>>

    @Query(
        "SELECT platform, SUM(endMillis - startMillis) AS total FROM exposures " +
            "WHERE startMillis >= :since GROUP BY platform",
    )
    fun exposureByPlatformSince(since: Long): Flow<List<PlatformDuration>>

    @Query("SELECT * FROM block_events WHERE timeMillis >= :since ORDER BY timeMillis")
    suspend fun eventsSince(since: Long): List<BlockEventEntity>

    @Query("SELECT * FROM exposures WHERE startMillis >= :since ORDER BY startMillis")
    suspend fun exposuresSince(since: Long): List<ExposureEntity>

    @Query("SELECT * FROM protection_events WHERE timeMillis >= :since ORDER BY timeMillis")
    suspend fun protectionEventsSince(since: Long): List<ProtectionEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyUsage(rows: List<DailyAppUsageEntity>)

    @Query("SELECT * FROM daily_app_usage WHERE epochDay >= :fromDay")
    suspend fun dailyUsageSince(fromDay: Long): List<DailyAppUsageEntity>

    @Query("DELETE FROM daily_app_usage WHERE epochDay < :beforeDay")
    suspend fun purgeDailyUsage(beforeDay: Long)

    @Query("SELECT * FROM protection_events ORDER BY timeMillis DESC LIMIT :limit")
    fun latestProtectionEvents(limit: Int): Flow<List<ProtectionEventEntity>>

    @Query("DELETE FROM block_events WHERE timeMillis < :before")
    suspend fun purgeEvents(before: Long)

    @Query("DELETE FROM exposures WHERE endMillis < :before")
    suspend fun purgeExposures(before: Long)

    @Query("DELETE FROM protection_events WHERE timeMillis < :before")
    suspend fun purgeProtectionEvents(before: Long)
}

@Database(
    entities = [BlockEventEntity::class, ExposureEntity::class, ProtectionEventEntity::class, DailyAppUsageEntity::class],
    version = 2,
    exportSchema = true,
    // v2 only adds a table: Room generates the migration, so 0.1.0 history is kept on update.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class NaoRolaDatabase : RoomDatabase() {
    abstract fun statsDao(): StatsDao

    companion object {
        const val NAME = "naorola.db"
    }
}
