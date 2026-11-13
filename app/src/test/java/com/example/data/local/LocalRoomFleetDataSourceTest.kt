package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CommandType
import com.example.data.model.DeliveryStatus
import com.example.data.model.VmStatus
import com.example.data.network.GatewayVmTelemetryItem
import com.example.data.network.VmGatewayClient
import com.example.data.repository.LocalRoomFleetDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalRoomFleetDataSourceTest {

    private lateinit var db: FleetDatabase
    private lateinit var dataSource: LocalRoomFleetDataSource

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FleetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataSource = LocalRoomFleetDataSource(db.fleetDao(), VmGatewayClient())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun initializeAndOperateWithoutLogin_creates100SlotsAndDispatchesUrlsAndApps() = runBlocking {
        dataSource.ensureInitializedProfile()
        val initRes = dataSource.initializeHundredSlots()
        assertTrue(initRes.isSuccess)

        val vms = dataSource.observeVms().first { it.size == 100 }
        assertEquals("VM-001", vms.first().vmId)
        assertEquals("VM-100", vms.last().vmId)
        assertTrue(vms.all { it.statusEnum == VmStatus.UNPROVISIONED })

        val updatedSlot = vms.first().copy(
            name = "Local KVM Node 1",
            status = VmStatus.ONLINE.name,
            androidVersion = "Android 15",
            ipAddress = "10.20.0.101"
        )
        assertTrue(dataSource.saveVmSlot(updatedSlot).isSuccess)

        val syncRes = dataSource.syncVmTelemetry(
            listOf(
                GatewayVmTelemetryItem(
                    vmId = "VM-002",
                    status = "ONLINE",
                    connectivity = "WIFI",
                    ipAddress = "10.20.0.102",
                    androidVersion = "Android 15"
                )
            )
        )
        assertTrue(syncRes.isSuccess)
        assertEquals(1, syncRes.getOrThrow())

        val refreshedVms = dataSource.observeVms().first { list ->
            list.first { it.vmId == "VM-002" }.ipAddress == "10.20.0.102"
        }
        assertEquals("10.20.0.101", refreshedVms.first { it.vmId == "VM-001" }.ipAddress)
        assertEquals("10.20.0.102", refreshedVms.first { it.vmId == "VM-002" }.ipAddress)

        val urlResult = dataSource.dispatchUrlToVms(
            targets = listOf(updatedSlot),
            url = "https://testing.example.org",
            targetPackage = "com.android.chrome",
            gatewayBaseUrl = "",
            gatewayToken = ""
        )
        assertTrue(urlResult.isSuccess)
        assertEquals(DeliveryStatus.QUEUED, urlResult.getOrThrow()["VM-001"])

        val appResult = dataSource.dispatchAppAction(
            targets = listOf(updatedSlot),
            commandType = CommandType.INSTALL_APK,
            packageName = "com.example.qa",
            appName = "QA App",
            versionName = "1.0.0",
            versionCode = 1L,
            apkSourceUrl = "https://releases.example.org/qa.apk",
            gatewayBaseUrl = "",
            gatewayToken = ""
        )
        assertTrue(appResult.isSuccess)

        val apps = dataSource.observeApps().first { it.isNotEmpty() }
        assertEquals("iso_VM-001_com_example_qa", apps.first().sessionProfileId)

        val launchResult = dataSource.launchAppOnVms(
            targets = listOf(updatedSlot, vms[1]),
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            gatewayBaseUrl = "",
            gatewayToken = ""
        )
        assertTrue(launchResult.isSuccess)
        assertEquals(DeliveryStatus.QUEUED, launchResult.getOrThrow()["VM-001"])
        assertEquals(DeliveryStatus.HOST_OFFLINE, launchResult.getOrThrow()["VM-002"])

        val prefs = com.example.data.prefs.GatewayPreferences(ApplicationProvider.getApplicationContext())
        prefs.addPhoneApps(
            listOf(
                com.example.data.model.SavedPhoneApp("com.whatsapp", "WhatsApp", "2.24.1", 22401L),
                com.example.data.model.SavedPhoneApp("com.android.chrome", "Chrome", "130.0", 130L)
            )
        )
        val saved = prefs.savedPhoneAppsFlow.first { it.size == 2 }
        assertEquals("Chrome", saved.first().appName)
        assertEquals("WhatsApp", saved.last().appName)

        val pushResult = dataSource.pushPhoneAppToVms(
            targets = listOf(updatedSlot, vms[1]),
            app = com.example.data.model.SavedPhoneApp("com.whatsapp", "WhatsApp", "2.24.1", 22401L),
            apkFiles = emptyList(),
            openAfterInstall = true,
            gatewayBaseUrl = "",
            gatewayToken = ""
        )
        assertTrue(pushResult.isSuccess)
        assertEquals(DeliveryStatus.QUEUED, pushResult.getOrThrow()["VM-001"])
        assertEquals(DeliveryStatus.HOST_OFFLINE, pushResult.getOrThrow()["VM-002"])

        val gmailAssignRes = dataSource.assignGmailAccountsBatch(
            mapOf("VM-001" to "screen01@gmail.com", "VM-002" to "screen02@gmail.com")
        )
        assertTrue(gmailAssignRes.isSuccess)
        assertEquals(2, gmailAssignRes.getOrThrow())

        val signedInSync = dataSource.syncVmTelemetry(
            listOf(
                GatewayVmTelemetryItem(
                    vmId = "VM-002",
                    status = "ONLINE",
                    connectivity = "WIFI",
                    ipAddress = "10.20.0.102",
                    androidVersion = "Android 15",
                    signedInGmail = "screen02@gmail.com"
                )
            )
        )
        assertTrue(signedInSync.isSuccess)

        val afterGmailVms = dataSource.observeVms().first { list ->
            list.first { it.vmId == "VM-002" }.accountStatus == com.example.data.model.VmAccountStatus.PLAY_SIGNED_IN.name
        }
        assertEquals("screen01@gmail.com", afterGmailVms.first { it.vmId == "VM-001" }.assignedGmail)
        assertEquals(
            com.example.data.model.VmAccountStatus.GMAIL_ASSIGNED,
            afterGmailVms.first { it.vmId == "VM-001" }.accountStatusEnum
        )
        assertEquals(
            com.example.data.model.VmAccountStatus.PLAY_SIGNED_IN,
            afterGmailVms.first { it.vmId == "VM-002" }.accountStatusEnum
        )

        val signInDispatch = dataSource.openGoogleSignInOnVms(
            targets = listOf(afterGmailVms.first { it.vmId == "VM-001" }),
            gatewayBaseUrl = "",
            gatewayToken = ""
        )
        assertTrue(signInDispatch.isSuccess)
        assertEquals(DeliveryStatus.QUEUED, signInDispatch.getOrThrow()["VM-001"])

        val clearRes = dataSource.clearAppSessionOnVms(
            targets = listOf(afterGmailVms.first { it.vmId == "VM-001" }),
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            openAfterClear = true,
            gatewayBaseUrl = "",
            gatewayToken = ""
        )
        assertTrue(clearRes.isSuccess)
        assertEquals(DeliveryStatus.QUEUED, clearRes.getOrThrow()["VM-001"])

        val logs = dataSource.observeAuditLogs().first { it.size >= 9 }
        assertTrue(logs.any { it.action == "INIT_100_SLOTS" })
        assertTrue(logs.any { it.action == "LAUNCH_APP" })
        assertTrue(logs.any { it.action == "PUSH_INSTALL_AND_OPEN" })
        assertTrue(logs.any { it.action == "ASSIGN_GMAIL_ACCOUNTS" })
        assertTrue(logs.any { it.action == "OPEN_GOOGLE_SIGNIN" })
        assertTrue(logs.any { it.action == "RESET_APP_SESSION_SIGNUP" })
    }
}
