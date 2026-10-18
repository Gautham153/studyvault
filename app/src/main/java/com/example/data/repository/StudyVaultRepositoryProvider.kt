package com.example.data.repository

/**
 * Service locator / provider for obtaining the active [StudyVaultRepository].
 *
 * During normal app execution, [SupabaseStudyVaultRepository] is used to provide
 * real Supabase PostgreSQL persistence with Row Level Security.
 * It never silently falls back to mock data when Supabase is unconfigured, ensuring
 * configuration issues are clearly surfaced. [MockStudyVaultRepository] is preserved
 * only when explicitly enabled for unit tests or previews via [setExplicitMockMode].
 */
object StudyVaultRepositoryProvider {

    private var useMockExplicitly: Boolean = false

    fun setExplicitMockMode(enabled: Boolean) {
        useMockExplicitly = enabled
    }

    fun isExplicitMockMode(): Boolean = useMockExplicitly

    val repository: StudyVaultRepository
        get() {
            return if (useMockExplicitly) {
                MockStudyVaultRepository.instance
            } else {
                SupabaseStudyVaultRepository.instance
            }
        }
}
