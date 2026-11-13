package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LocalProfileEntity::class,
        LocalVmSlotEntity::class,
        LocalVmGroupEntity::class,
        LocalVmCommandEntity::class,
        LocalVmAppEntity::class,
        LocalAuditLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class FleetDatabase : RoomDatabase() {
    abstract fun fleetDao(): FleetDao

    companion object {
        @Volatile
        private var INSTANCE: FleetDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_vms ADD COLUMN ipAddress TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_vms ADD COLUMN assignedGmail TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_vms ADD COLUMN accountStatus TEXT NOT NULL DEFAULT 'UNASSIGNED'")
            }
        }

        fun getInstance(context: Context): FleetDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FleetDatabase::class.java,
                    "vm_master_local.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
        }
    }
}
