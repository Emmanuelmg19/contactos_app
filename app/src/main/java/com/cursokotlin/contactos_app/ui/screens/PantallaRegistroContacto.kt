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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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


//Aqui es donde tenemos la Pantalla de Registro.
//Que nos sirve tanto para crear un contacto nuevo como para editar uno que ya existe.
//Es como un formulario de papel, pero digital y muy inteligente.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRegistroContacto(
    viewModel: ContactViewModel,
    contactId: Long? = null, // Si viene un ID, significa que estamos editando. Si es nulo, es uno nuevo.
    onBack: () -> Unit       // Función para cerrar esta pantalla y volver
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primaryColor =
        Color(0xFF1A56DB) // Implementamos el color azul a referencia de los diseño proporcionados por el profesor

    // Aquí guardamos temporalmente lo que el usuario escribe antes de guardarlo definitivamente

    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    // Para la foto del contacto

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showContent by remember { mutableStateOf(false) }

    // Guardamos el contacto original completo (con remoteId, syncStatus, etc.)
    // para no perder esos datos cuando editamos y guardamos de nuevo
    var originalContact by remember { mutableStateOf<Contact?>(null) }

    // Aquí guardamos los mensajes de error por si el usuario deja campos vacíos

    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }

    // Este es el buscador de fotos. Nos permite elegir una imagen de la galería del .dispositivo del usuario
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {

                // Pedimos permiso permanente para poder ver la foto siempre, no solo una vez
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                photoUri = it
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Si estamos editando un contacto, al abrir la pantalla vamos a buscar sus datos

    LaunchedEffect(contactId) {
        if (contactId != null && contactId != -1L) {
            val contact = viewModel.getContactById(contactId)
            contact?.let {
                originalContact = it
                android.util.Log.d("DEBUG_SYNC", "CARGADO -> remoteId = ${it.remoteId}, id = ${it.id}, syncStatus = ${it.syncStatus}")
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
            // La barra de arriba con el botón de X y el botón de Confirmar
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
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancelar",
                            tint = primaryColor
                        )
                    }
                },
                actions = {
                    // El botón de Palomita para guardar
                    IconButton(onClick = {
                        scope.launch {

                            // Primero le preguntamos a la señora muy amable que verifique que todo este correcto
                            val result = ContactValidator.validateContact(
                                name,
                                email,
                                phone,
                                contactId,
                                viewModel
                            )

                            when (result) {
                                is ValidationResult.Success -> {
                                    // Si todo está bien, limpiamos errores y guardamos
                                    nameError = null
                                    emailError = null
                                    phoneError = null

                                    // Si ya existía un contacto (edición), preservamos su remoteId,
                                    // syncStatus previo y demás campos, solo actualizando lo editado.
                                    // Si es nuevo, creamos el Contact desde cero.
                                    android.util.Log.d("DEBUG_SYNC", "ANTES DE GUARDAR -> originalContact?.remoteId = ${originalContact?.remoteId}, originalContact?.id = ${originalContact?.id}, contactId param = $contactId")
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
                                        if (contactId == null) viewModel.insert(contact) else viewModel.update(
                                            contact
                                        )
                                        Toast.makeText(
                                            context,
                                            "Guardado con éxito",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onBack()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Error: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }

                                is ValidationResult.Errors -> {

                                    // Si algo falló, mostramos los mensajes de error en los campos correspondientes como email, correo o el numero celular
                                    nameError = result.fieldErrors["name"]
                                    emailError = result.fieldErrors["email"]
                                    phoneError = result.fieldErrors["phone"]
                                }
                            }
                        }
                    }) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Guardar",
                            tint = primaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        if (isLoading) {
            FullScreenLoading() // Circulito de carga mientras buscamos los datos
        } else {

            // Agregamos la animación para que el formulario aparezca subiendo un poquito
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(600)) + slideInVertically(initialOffsetY = { 50 })
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color.White)
                        .verticalScroll(rememberScrollState()) // Permite bajar si el teclado tapa algo
                ) {
                    // El espacio para la foto de perfil
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp), contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(110.dp),
                            shape = CircleShape,
                            color = Color(0xFFF3F4F6),
                            onClick = {

                                // Al tocar el círculo con el icono de camara , se abre la galería
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
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

                                // Si no hay foto, ponemos un icono de cámara
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

                    // Estos son todos los cuadros donde el usuario escribe
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

                        // Selector especial para el teléfono que ponemos las banderas
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