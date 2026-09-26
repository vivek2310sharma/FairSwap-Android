package com.fairswap.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class BackendHealthRepository(
    private val config: FairSwapBackendConfig,
    private val supabase: SupabaseClient,
) {
    suspend fun check(): BackendHealth {
        if (!config.isConfigured) return BackendHealth.NotConfigured

        return runCatching {
            val categories = supabase
                .from("categories")
                .select(columns = Columns.list("id, name")) {
                    limit(count = 1)
                }
                .decodeList<CategoryProbeDto>()

            BackendHealth.Reachable(
                sampleCategoryName = categories.firstOrNull()?.name,
            )
        }.getOrElse { error ->
            BackendHealth.Unreachable(
                reason = error.message?.take(180) ?: error::class.simpleName.orEmpty(),
            )
        }
    }
}

sealed interface BackendHealth {
    data object NotConfigured : BackendHealth

    data class Reachable(
        val sampleCategoryName: String?,
    ) : BackendHealth

    data class Unreachable(
        val reason: String,
    ) : BackendHealth
}

@Serializable
private data class CategoryProbeDto(
    val id: String,
    @SerialName("name") val name: String,
)
