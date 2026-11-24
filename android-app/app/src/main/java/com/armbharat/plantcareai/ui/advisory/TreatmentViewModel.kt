
package com.armbharat.plantcareai.ui.advisory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armbharat.plantcareai.core.database.TreatmentEntity
import com.armbharat.plantcareai.data.repository.TreatmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TreatmentViewModel(
    private val repository: TreatmentRepository
) : ViewModel() {

    private val _treatment =
        MutableStateFlow<TreatmentEntity?>(null)

    val treatment: StateFlow<TreatmentEntity?> =
        _treatment.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()

    private val _errorMessage =
        MutableStateFlow<String?>(null)

    val errorMessage: StateFlow<String?> =
        _errorMessage.asStateFlow()

    fun loadTreatment(
        classKey: String,
        region: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                _treatment.value =
                    repository.getTreatmentForRegion(
                        classKey = classKey,
                        region = region
                    )

            } catch (e: Exception) {
                _errorMessage.value =
                    e.message ?: "Unable to load treatment."

            } finally {
                _isLoading.value = false
            }
        }
    }
}
