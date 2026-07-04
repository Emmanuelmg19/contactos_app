package com.cursokotlin.contactos_app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cursokotlin.contactos_app.data.model.Contact
import com.cursokotlin.contactos_app.ui.ContactViewModel
import com.cursokotlin.contactos_app.ui.components.detail.ProfessionalDeleteDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


// Aquí es donde el usuario puede ver todos sus contactos, buscarlos por nombre o teléfono,
// y puede    deslizar para borrar si lo necesita.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PantallaDeContactos(
    viewModel: ContactViewModel,
    onContactClick: (Long) -> Unit,   // Qué pasa al tocar un contacto  se dirije  a  detalle
    onAddContactClick: () -> Unit,   // Qué pasa al tocar el botón "+" ir al registro
    onFavoritesClick: () -> Unit      // Ir a la sección de favoritos
) {
    // Obtenemos la lista de contactos desde el "cerebro" de la app
    val contacts by viewModel.allContacts.collectAsState()

    // Al entrar a esta pantalla, pedimos al servidor la lista actualizada de contactos
    // (index ligero: sin galería completa, solo datos básicos + miniatura/contador)
    LaunchedEffect(Unit) {
        viewModel.syncFromApi()
    }

    // Aquí guardamos lo que el usuario escribe en la barrita de búsqueda
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Para saber a quién queremos borrar
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }
    val deletingIds = remember { mutableStateListOf<Long>() }

    // Filtramos la lista en tiempo real según lo que el usuario escribe
    val filteredContacts = remember(contacts, searchQuery) {
        contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery)
        }.sortedBy { it.name.lowercase() }
    }

    // Agrupamos los contactos por su letra inicial (A, B, C...) ecetera
    val grouped = remember(filteredContacts) {
        filteredContacts.groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }
    }

    val primaryBrand = Color(0xFF1A56DB)
    val successColor = Color(0xFF10B981)

    Scaffold(
        topBar = {
            // La parte de arriba con el título y la barra de búsqueda
            Column(
                modifier = Modifier
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Mis Contactos",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF111827)
                    )
                }

                // La barrita de búsqueda con el icono de lupa
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Buscar contactos...",
                                color = Color(0xFF9CA3AF),
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(4.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = primaryBrand
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF9FAFB),
                            unfocusedContainerColor = Color(0xFFF9FAFB),
                            focusedBorderColor = primaryBrand.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color(0xFFE5E7EB)
                        ),
                        singleLine = true
                    )
                }
            }
        },
        bottomBar = {
            // La barrita de navegación de abajo de Contactos y Favoritos
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp,
                modifier = Modifier.shadow(24.dp)
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    label = { Text("Contactos", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryBrand,
                        selectedTextColor = primaryBrand,
                        indicatorColor = primaryBrand.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onFavoritesClick,
                    icon = { Icon(Icons.Rounded.Star, contentDescription = null) },
                    label = { Text("Favoritos") }
                )
            }
        },
        floatingActionButton = {

            // El botón redondo flotante azul para agregar nuevos contactos

            FloatingActionButton(
                onClick = onAddContactClick,
                containerColor = primaryBrand,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(64.dp)
                    .offset(y = 8.dp)
                    .shadow(12.dp, CircleShape, spotColor = primaryBrand)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Agregar",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // La lista de contactos que se puede deslizar

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFF8FAFF)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                grouped.forEach { (initial, contactsInGroup) ->

                    //Agregamos el  Encabezado con la letra (A, B, C...)

                    stickyHeader(key = initial) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFF).copy(alpha = 0.9f))
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = initial.toString(),
                                fontWeight = FontWeight.Black,
                                color = primaryBrand,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }

                    itemsIndexed(contactsInGroup, key = { _, it -> it.id }) { index, contact ->
                        val isDeleting = deletingIds.contains(contact.id)

                        // Detectamos cuando el usuario desliza para borrar

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    contactToDelete =

                                        contact // Mostramos el diálogo de confirmar eliminar contacto
                                    false
                                } else false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {

                                //Agregamos el color de fondo rojo que sale al deslizar
                                val color by animateColorAsState(
                                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Color(
                                        0xFFEF4444
                                    ) else Color.Transparent,
                                    label = "bg"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(color)
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            },
                            modifier = Modifier.animateItem()
                        ) {
                            // Animación suave de entrada cuando aparece la lista
                            AnimatedEntranceItem(index = index) {
                                // Animación de salida cuando se borra el contacto
                                SpectacularExitItem(
                                    isDeleting = isDeleting,
                                    onAnimationFinished = {
                                        viewModel.delete(contact)
                                        deletingIds.remove(contact.id)
                                    }
                                ) {
                                    // Agregamos la tarjetita visual de cada contacto
                                    ProfessionalContactItem(
                                        contact = contact,
                                        searchQuery = searchQuery,
                                        primaryColor = primaryBrand,
                                        highlightColor = successColor,
                                        onClick = { onContactClick(contact.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            //Agregamos la  barrita lateral de letras para saltar rápido a una sección
            AlphabetScroller(
                initials = remember(grouped) { grouped.keys.toList() },
                onLetterClick = { char ->
                    val index = filteredContacts.indexOfFirst {
                        it.name.startsWith(
                            char,
                            ignoreCase = true
                        )
                    }
                    if (index != -1) {
                        scope.launch { listState.animateScrollToItem(index) }
                    }
                },
                primaryColor = primaryBrand
            )
        }

        // Si el usuario deslizó para borrar, sale este aviso preguntando si está seguro
        contactToDelete?.let { contact ->
            ProfessionalDeleteDialog(
                contactName = contact.name,
                onConfirm = {
                    deletingIds.add(contact.id) // Iniciamos la animación de borrado
                    contactToDelete = null
                },
                onDismiss = { contactToDelete = null }
            )
        }
    }
}


//En esta seccion lo que hace esta  función es que los contactos aparezcan uno por uno de abajo hacia arriba
//de forma elegante cuando abres la app.

@Composable
fun AnimatedEntranceItem(
    index: Int,
    content: @Composable () -> Unit
) {
    val state = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 40L) // Hace que vayan saliendo en forma de  cascada
        state.value = true
    }

    AnimatedVisibility(
        visible = state.value,
        enter = slideInVertically(
            initialOffsetY = { 40 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(animationSpec = tween(400)),
        exit = fadeOut()
    ) {
        content()
    }
}


//Esta función hace que cuando borras un contacto, este vuele fuera de la pantalla
//mientras se hace pequeño, dándole un toque.

@Composable
fun SpectacularExitItem(
    isDeleting: Boolean,
    onAnimationFinished: () -> Unit,
    content: @Composable () -> Unit
) {
    val transition = updateTransition(targetState = isDeleting, label = "spectacularExit")

    val scale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 500, easing = FastOutSlowInEasing) },
        label = "scale"
    ) { if (it) 0.4f else 1f }

    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400) },
        label = "alpha"
    ) { if (it) 0f else 1f }

    val translationX by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 600, easing = CubicBezierEasing(0.36f, 0f, 0.66f, -0.56f))
        },
        label = "flyOut"
    ) { if (it) 1200f else 0f }

    val rotation by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 500) },
        label = "rotate"
    ) { if (it) 15f else 0f }

    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            delay(600)
            onAnimationFinished()
        }
    }

    Box(
        modifier = Modifier.graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            alpha = alpha,
            translationX = translationX,
            rotationZ = rotation
        )
    ) {
        content()
    }
}


//La tarjetita individual de cada contacto. Muestra la foto,
//el nombre y el teléfono.

@Composable
fun ProfessionalContactItem(
    contact: Contact,
    searchQuery: String,
    primaryColor: Color,
    highlightColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    // Agregamos para obtener  las iniciales si no hay foto
    val initials = remember(contact.name) {
        contact.name.split(" ").filter { it.isNotBlank() }
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
    }

    // Este bloque sirve para resaltar en color lo que el usuario está buscando

    val annotatedName = remember(contact.name, searchQuery) {
        buildAnnotatedString {
            val name = contact.name
            if (searchQuery.isEmpty()) append(name)
            else {
                val index = name.lowercase().indexOf(searchQuery.lowercase())
                if (index == -1) append(name)
                else {
                    append(name.substring(0, index))
                    withStyle(
                        SpanStyle(
                            color = highlightColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    ) { append(name.substring(index, index + searchQuery.length)) }
                    append(name.substring(index + searchQuery.length))
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(28.dp), spotColor = primaryColor.copy(alpha = 0.25f))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier
            .padding(14.dp)
            .fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

            // El circulito de la foto o iniciales
            Box(modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.8f),
                            primaryColor
                        )
                    )
                ), contentAlignment = Alignment.Center
            ) {
                if (contact.photoUri != null) {
                    AsyncImage(
                        model = Uri.parse(contact.photoUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = annotatedName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF111827)
                )
                Text(
                    text = contact.phone,
                    color = Color(0xFF6B7280),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // Botoncito rápido para llamar sin entrar al detalle

            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = Color(0xFFF3F4F6),
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:${contact.phone}")
                        )
                    )
                }) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Call,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


//La barrita lateral de la A a la Z para navegar rápido por la lista.
@Composable
fun AlphabetScroller(initials: List<Char>, onLetterClick: (Char) -> Unit, primaryColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(end = 8.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        initials.forEach { char ->
            Text(
                text = char.toString(),
                modifier = Modifier
                    .clickable { onLetterClick(char) }
                    .padding(vertical = 2.dp, horizontal = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor.copy(alpha = 0.7f))
        }
    }
}