package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CommandType
import com.example.data.model.DeliveryStatus
import com.example.data.model.VmAccountStatus
import com.example.data.model.VmStatus
import com.example.data.network.VmGatewayClient
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class FleetRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureAdminProfile_and_initializeHundredSlots_creates100UnprovisionedSlots() = runBlocking {
        val uid = signInTestUser("admin_${UUID.randomUUID()}@test.com")
        val repo = FleetRepository(firestore, VmGatewayClient(), auth)

        val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureAdminProfile(uid, "admin@test.com", "Fleet Admin")
        }
        assertTrue(profileResult.isSuccess)
        assertEquals("OWNER_ADMIN", profileResult.getOrThrow().role)

        val initResult = withTimeout(15000L) {
            repo.initializeHundredSlots(uid, "admin@test.com")
        }
        assertTrue("Expected initializeHundredSlots success, got ${initResult.exceptionOrNull()}", initResult.isSuccess)

        val vms = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeVms(uid).first { it.size == 100 }
        }
        assertEquals(100, vms.size)
        assertEquals("VM-001", vms.first().vmId)
        assertEquals("VM-100", vms.last().vmId)
        assertTrue(vms.all { it.statusEnum == VmStatus.UNPROVISIONED })

        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeAuditLogs(uid).first { it.isNotEmpty() }
        }
        assertTrue(logs.any { it.action == "INIT_100_SLOTS" })
    }

    @Test
    fun dispatchUrl_and_appAction_persistCommandsAndIsolatedApps() = runBlocking {
        val uid = signInTestUser("ops_${UUID.randomUUID()}@test.com")
        val repo = FleetRepository(firestore, VmGatewayClient(), auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureAdminProfile(uid, "ops@test.com", "Ops Admin").getOrThrow()
            repo.initializeHundredSlots(uid, "ops@test.com").getOrThrow()
        }

        val vms = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeVms(uid).first { it.size == 100 }
        }
        val targetSlot = vms.first().copy(
            name = "QA Pixel Node",
            androidVersion = "Android 15",
            status = VmStatus.ONLINE.name,
            hostUrl = "https://vm-node-01.example.invalid"
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveVmSlot(uid, "ops@test.com", targetSlot).getOrThrow()
        }

        val urlDispatch = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.dispatchUrlToVms(
                userId = uid,
                actorEmail = "ops@test.com",
                targets = listOf(targetSlot),
                url = "https://testing.example.org/build-42",
                targetPackage = "com.android.chrome",
                gatewayBaseUrl = "",
                gatewayToken = ""
            )
        }
        assertTrue(urlDispatch.isSuccess)
        assertEquals(DeliveryStatus.HOST_OFFLINE, urlDispatch.getOrThrow()["VM-001"])

        val appAction = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.dispatchAppAction(
                userId = uid,
                actorEmail = "ops@test.com",
                targets = listOf(targetSlot),
                commandType = CommandType.INSTALL_APK,
                packageName = "com.example.targetapp",
                appName = "Target QA App",
                versionName = "2.1.0",
                versionCode = 210L,
                apkSourceUrl = "https://releases.example.org/app-2.1.0.apk",
                gatewayBaseUrl = "",
                gatewayToken = ""
            )
        }
        assertTrue(appAction.isSuccess)

        val apps = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeApps(uid).first { it.isNotEmpty() }
        }
        assertTrue(apps.any { it.vmId == "VM-001" && it.sessionProfileId.startsWith("iso_VM-001_") })

        val gmailBatch = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.assignGmailAccountsBatch(
                userId = uid,
                actorEmail = "ops@test.com",
                assignments = mapOf("VM-001" to "vm001.signup@gmail.com")
            )
        }
        assertTrue("Expected assignGmailAccountsBatch success, got ${gmailBatch.exceptionOrNull()}", gmailBatch.isSuccess)

        val updatedVm001 = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeVms(uid).first { list ->
                list.firstOrNull { it.vmId == "VM-001" }?.assignedGmail == "vm001.signup@gmail.com"
            }.first { it.vmId == "VM-001" }
        }
        assertEquals(VmAccountStatus.GMAIL_ASSIGNED, updatedVm001.accountStatusEnum)

        val googleSignInDispatch = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.openGoogleSignInOnVms(
                userId = uid,
                actorEmail = "ops@test.com",
                targets = listOf(updatedVm001),
                gatewayBaseUrl = "",
                gatewayToken = ""
            )
        }
        assertTrue("Expected openGoogleSignInOnVms success, got ${googleSignInDispatch.exceptionOrNull()}", googleSignInDispatch.isSuccess)
        assertEquals(DeliveryStatus.HOST_OFFLINE, googleSignInDispatch.getOrThrow()["VM-001"])
    }

    @Test
    fun crossUserRead_onAnotherAdminsFleet_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val aliceRepo = FleetRepository(firestore, VmGatewayClient(), auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureAdminProfile(aliceUid, "alice@test.com", "Alice").getOrThrow()
        }

        signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val bobRepo = FleetRepository(firestore, VmGatewayClient(), auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeVms(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's fleet")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }
    }

    @Test
    fun unauthenticatedRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("unauth_${UUID.randomUUID()}@test.com")
        auth.signOut()
        val repo = FleetRepository(firestore, VmGatewayClient(), auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeVms(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
