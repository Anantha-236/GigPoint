package com.example.gigpoint.actions

import com.example.gigpoint.domain.ActionPlan
import com.example.gigpoint.domain.InventoryOperation

enum class MerchantRole { OWNER, MANAGER, STAFF, UNKNOWN }

data class PolicyDecision(
    val allowed: Boolean,
    val requiresConfirmation: Boolean,
    val reason: String? = null
)

class ActionPolicyEngine {
    fun evaluate(
        plan: ActionPlan,
        role: MerchantRole,
        speakerVerified: Boolean
    ): PolicyDecision {
        val writes =
            plan.mutations.any { it.operation.changesStock || it.operation.destructive }

        if (writes && !speakerVerified) {
            return PolicyDecision(
                false,
                false,
                "Inventory changes require a verified authorized speaker."
            )
        }

        if (writes && role == MerchantRole.UNKNOWN) {
            return PolicyDecision(
                false,
                false,
                "This speaker is not authorized to change inventory."
            )
        }

        if (
            role == MerchantRole.STAFF &&
            plan.mutations.any {
                it.operation in setOf(
                    InventoryOperation.SET_STOCK,
                    InventoryOperation.SET_REORDER_LEVEL,
                    InventoryOperation.ARCHIVE_PRODUCT
                )
            }
        ) {
            return PolicyDecision(
                false,
                false,
                "This operation requires owner or manager permission."
            )
        }

        return PolicyDecision(
            allowed = true,
            requiresConfirmation =
                plan.requiresConfirmation ||
                plan.mutations.size > 1 ||
                plan.mutations.any {
                    it.operation.destructive || it.confidence.score < 0.85
                }
        )
    }
}
