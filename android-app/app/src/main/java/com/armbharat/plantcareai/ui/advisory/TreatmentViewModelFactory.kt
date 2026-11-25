
package com.armbharat.plantcareai.ui.advisory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.armbharat.plantcareai.data.repository.TreatmentRepository

class TreatmentViewModelFactory(
    private val repository: TreatmentRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(TreatmentViewModel::class.java)) {
            return TreatmentViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
