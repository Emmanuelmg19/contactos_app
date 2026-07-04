package com.cursokotlin.contactos_app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cursokotlin.contactos_app.data.model.Contact
import com.cursokotlin.contactos_app.data.model.ContactImage
import com.cursokotlin.contactos_app.data.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactViewModel(private val repository: ContactRepository) : ViewModel() {

    val allContacts: StateFlow<List<Contact>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteContacts: StateFlow<List<Contact>> = repository.favoriteContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedContact = MutableStateFlow<Contact?>(null)
    val selectedContact: StateFlow<Contact?> = _selectedContact.asStateFlow()

    private val _galleryContactId = MutableStateFlow<Long?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val galleryImages: StateFlow<List<ContactImage>> = _galleryContactId
        .flatMapLatest { id ->
            if (id == null) emptyFlow() else repository.getImagesForContact(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun fetchContactById(id: Long) {
        viewModelScope.launch {
            _selectedContact.value = withContext(Dispatchers.IO) {
                repository.getContactById(id)
            }
            _galleryContactId.value = id

            val contact = _selectedContact.value
            if (contact?.remoteId != null) {
                withContext(Dispatchers.IO) {
                    repository.fetchContactDetail(contact.id, contact.remoteId)
                }
            }
        }
    }

    suspend fun getContactById(id: Long): Contact? = withContext(Dispatchers.IO) {
        repository.getContactById(id)
    }

    suspend fun getContactByEmail(email: String): Contact? = withContext(Dispatchers.IO) {
        repository.getContactByEmail(email)
    }

    fun insert(contact: Contact) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(contact)
    }

    // Igual que insert(), pero espera y devuelve el id local generado.
    // Se usa cuando necesitamos asociar imágenes inmediatamente después de crear el contacto.
    suspend fun insertAndGetId(contact: Contact): Long = withContext(Dispatchers.IO) {
        repository.insert(contact)
    }

    fun update(contact: Contact) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(contact)
    }

    fun delete(contact: Contact) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(contact)
    }

    fun toggleFavorite(contact: Contact) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(contact.copy(isFavorite = !contact.isFavorite))
    }

    fun syncFromApi() = viewModelScope.launch(Dispatchers.IO) {
        repository.syncFromApi()
    }

    // Guarda localmente varias imágenes seleccionadas para un contacto (quedan pendientes de subir)
    fun addLocalImages(contactLocalId: Long, uris: List<Uri>) = viewModelScope.launch(Dispatchers.IO) {
        repository.addLocalImagesForContact(contactLocalId, uris)
    }

    // Elimina una imagen específica de la galería (desde la vista show)
    fun deleteImage(image: ContactImage) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteImage(image)
    }

    fun clearSelectedContact() {
        _selectedContact.value = null
        _galleryContactId.value = null
    }
}

class ContactViewModelFactory(private val repository: ContactRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ContactViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}