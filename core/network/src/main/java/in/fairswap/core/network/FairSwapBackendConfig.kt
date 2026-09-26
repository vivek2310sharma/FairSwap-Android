package in.fairswap.core.network

data class FairSwapBackendConfig(
    val environmentName: String,
    val supabaseUrl: String,
    val publishableKey: String,
) {
    val isConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") && publishableKey.isNotBlank()
}
