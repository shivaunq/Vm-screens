package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FleetDao {
    @Query("SELECT * FROM local_profile WHERE id = 'local_admin' LIMIT 1")
    fun observeProfile(): Flow<LocalProfileEntity?>

    @Query("SELECT * FROM local_profile WHERE id = 'local_admin' LIMIT 1")
    suspend fun getProfileOnce(): LocalProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: LocalProfileEntity)

    @Query("SELECT * FROM local_vms ORDER BY slotNumber ASC")
    fun observeVms(): Flow<List<LocalVmSlotEntity>>

    @Query("SELECT * FROM local_vms ORDER BY slotNumber ASC")
    suspend fun observeVmsOnce(): List<LocalVmSlotEntity>

    @Query("SELECT COUNT(*) FROM local_vms")
    suspend fun countVms(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVms(vms: List<LocalVmSlotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVm(vm: LocalVmSlotEntity)

    @Query("UPDATE local_vms SET groupId = :groupId, updatedAtMs = :updatedAtMs WHERE vmId IN (:vmIds)")
    suspend fun updateGroupForVms(vmIds: List<String>, groupId: String, updatedAtMs: Long)

    @Query("SELECT * FROM local_vm_groups ORDER BY name ASC")
    fun observeGroups(): Flow<List<LocalVmGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: LocalVmGroupEntity)

    @Query("SELECT * FROM local_vm_commands ORDER BY createdAtMs DESC LIMIT 150")
    fun observeCommands(): Flow<List<LocalVmCommandEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommands(commands: List<LocalVmCommandEntity>)

    @Query("SELECT * FROM local_vm_apps ORDER BY updatedAtMs DESC LIMIT 200")
    fun observeApps(): Flow<List<LocalVmAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApps(apps: List<LocalVmAppEntity>)

    @Query("SELECT * FROM local_audit_logs ORDER BY createdAtMs DESC LIMIT 120")
    fun observeAuditLogs(): Flow<List<LocalAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: LocalAuditLogEntity)
}
