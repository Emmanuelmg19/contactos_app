package com.cursokotlin.contactos_app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.cursokotlin.contactos_app.data.model.Contact
import com.cursokotlin.contactos_app.ui.ContactViewModel
import com.cursokotlin.contactos_app.ui.components.common.FullScreenLoading
import com.cursokotlin.contactos_app.ui.components.form.Input
import com.cursokotlin.contactos_app.ui.components.form.SmartPhoneSelector
import com.cursokotlin.contactos_app.validation.ContactValidator
import com.cursokotlin.contactos_app.validation.ValidationResult
import kotlinx.coroutines.launch


// Pantalla de Registro/Edición de contacto. Permite crear uno nuevo o editar uno existente,
// incluyendo la selección de VARIAS imágenes para la galería del contacto.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRegistroContacto(
    viewModel: ContactViewModel,
    contactId: Long? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primaryColor = Color(0xFF1A56DB)

    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    // Imagen de portada (se usa como miniatura en el index)
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    // Todas las imágenes seleccionadas para la galería del contacto (pueden ser varias)
    val selectedImageUris = remember { mutableStateListOf<Uri>() }

    var isLoading by remember { mutableStateOf(true) }
    var showContent by remember { mutableStateOf(false) }
    var originalContact by remember { mutableStateOf<Contact?>(null) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }

    // Selector MÚLTIPLE de imágenes (hasta 10 a la vez, suficiente para el examen)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            selectedImageUris.addAll(uris)
            // La primera imagen elegida (o la primera de todas) sirve como miniatura del index
            if (photoUri == null) {
                photoUri = selectedImageUris.first()
            }
        }
    }

    LaunchedEffect(contactId) {
        if (contactId != null && contactId != -1L) {
            val contact = viewModel.getContactById(contactId)
            contact?.let {
                originalContact = it
                name = it.name
                surname = it.surname
                phone = it.phone
                email = it.email
                photoUri = it.photoUri?.toUri()
            }
        }
        isLoading = false
        showContent = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (contactId == null) "Registrar Contacto" else "Editar Contacto",
                        color = primaryColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = primaryColor)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val result = ContactValidator.validateContact(
                                name, email, phone, contactId, viewModel
                            )

                            when (result) {
                                is ValidationResult.Success -> {
                                    nameError = null
                                    emailError = null
                                    phoneError = null

                                    val contact = if (originalContact != null) {
                                        originalContact!!.copy(
                                            name = name.trim(),
                                            surname = surname.trim(),
                                            phone = phone,
                                            email = email,
                                            photoUri = photoUri?.toString()
                                        )
                                    } else {
                                        Contact(
                                            name = name.trim(),
                                            surname = surname.trim(),
                                            phone = phone,
                                            email = email,
                                            photoUri = photoUri?.toString()
                                        )
                                    }

                                    try {
                                        val localId = if (contactId == null) {
                                            viewModel.insertAndGetId(contact)
                                        } else {
                                            viewModel.update(contact)
                                            contact.id
                                        }

                                        // Guardamos localmente todas las imágenes seleccionadas;
                                        // quedan pendientes y el SyncWorker las sube en cuanto haya conexión
                                        if (selectedImageUris.isNotEmpty()) {
                                            viewModel.addLocalImages(localId, selectedImageUris.toList())
                                        }

                                        Toast.makeText(context, "Guardado con éxito", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }

                                is ValidationResult.Errors -> {
                                    nameError = result.fieldErrors["name"]
                                    emailError = result.fieldErrors["email"]
                                    phoneError = result.fieldErrors["phone"]
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Rounded.Check, contentDescription = "Guardar", tint = primaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        if (isLoading) {
            FullScreenLoading()
        } else {
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(600)) + slideInVertically(initialOffsetY = { 50 })
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, bottom = 12.dp), contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(110.dp),
                            shape = CircleShape,
                            color = Color(0xFFF3F4F6),
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            if (photoUri != null) {
                                AsyncImage(
                                    model = photoUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.AddAPhoto,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp),
                                        tint = primaryColor
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Toca para elegir una o varias imágenes",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    // Tira de miniaturas de todas las imágenes seleccionadas para la galería
                    if (selectedImageUris.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            selectedImageUris.forEachIndexed { index, uri ->
                                Box {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Botón para quitar esta imagen de la selección antes de guardar
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .clickable {
                                                val removed = selectedImageUris.removeAt(index)
                                                if (photoUri == removed) {
                                                    photoUri = selectedImageUris.firstOrNull()
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Quitar",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    Column(modifier = Modifier.padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Input(
                            value = name,
                            onValueChange = { name = it.take(50); nameError = null },
                            label = "Nombre",
                            isError = nameError != null,
                            errorMessage = nameError
                        )

                        Input(
                            value = surname,
                            onValueChange = { surname = it.take(50) },
                            label = "Apellidos (Opcional)"
                        )

                        SmartPhoneSelector(
                            phone = phone,
                            onPhoneChange = {
                                phone = it.filter { c -> c.isDigit() }.take(10)
                                phoneError = null
                            },
                            error = phoneError,
                            primaryColor = primaryColor
                        )

                        Input(
                            value = email,
                            onValueChange = { email = it; emailError = null },
                            label = "Correo electrónico",
                            isError = emailError != null,
                            errorMessage = emailError
                        )
                    }
                }
            }
        }
    }
}