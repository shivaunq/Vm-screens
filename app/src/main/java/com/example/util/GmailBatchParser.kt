package com.example.util

import com.example.data.model.VmSlot

object GmailBatchParser {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+._%\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return trimmed.length <= 160 && EMAIL_REGEX.matches(trimmed)
    }

    fun parseUniqueEmails(rawInput: String): List<String> {
        return rawInput
            .split('\n', '\r', ',', ';', ' ', '\t')
            .map { it.trim() }
            .filter { isValidEmail(it) }
            .distinctBy { it.lowercase() }
    }

    fun mapEmailsToVms(
        emails: List<String>,
        vms: List<VmSlot>,
        startSlotNumber: Int = 1
    ): Map<String, String> {
        if (emails.isEmpty() || vms.isEmpty()) return emptyMap()
        val orderedTargets = vms
            .sortedBy { it.slotNumber }
            .filter { it.slotNumber >= startSlotNumber.coerceIn(1, 100) }
        val result = linkedMapOf<String, String>()
        orderedTargets.zip(emails).forEach { (vm, email) ->
            result[vm.vmId] = email
        }
        return result
    }
}
