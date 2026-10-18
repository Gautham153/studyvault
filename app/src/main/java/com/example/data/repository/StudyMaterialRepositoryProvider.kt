package com.example.data.repository

object StudyMaterialRepositoryProvider {

    private var useMockExplicitly: Boolean = false

    fun setExplicitMockMode(enabled: Boolean) {
        useMockExplicitly = enabled
    }

    val repository: StudyMaterialRepository
        get() {
            return if (useMockExplicitly || StudyVaultRepositoryProvider.isExplicitMockMode()) {
                MockStudyMaterialRepository.instance
            } else {
                SupabaseStudyMaterialRepository.instance
            }
        }
}
