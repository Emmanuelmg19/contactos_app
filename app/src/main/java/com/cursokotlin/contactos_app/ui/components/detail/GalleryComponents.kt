package com.cursokotlin.contactos_app.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.cursokotlin.contactos_app.data.model.ContactImage
import com.cursokotlin.contactos_app.data.network.RetrofitInstance

fun resolveImageModel(image: ContactImage): Any? {
    return when {
        !image.remoteUrl.isNullOrBlank() -> RetrofitInstance.IMAGE_BASE_URL + image.remoteUrl
        !image.localPath.isNullOrBlank() -> image.localPath
        else -> null
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ContactImageGallery(
    images: List<ContactImage>,
    primaryColor: Color,
    onDeleteImage: (ContactImage) -> Unit
) {
    if (images.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { images.size })
    var showFullScreen by remember { mutableStateOf(false) }
    var fullScreenStartPage by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) { page ->
            AsyncImage(
                model = resolveImageModel(images[page]),
                contentDescription = "Imagen ${page + 1} de ${images.size}",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        fullScreenStartPage = page
                        showFullScreen = true
                    },
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (images.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(images.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (selected) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (selected) primaryColor else Color(0xFFE0E0E0))
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (images.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                images.forEachIndexed { index, image ->
                    val isSelected = pagerState.currentPage == index
                    AsyncImage(
                        model = resolveImageModel(image),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isSelected) Modifier.background(primaryColor.copy(alpha = 0.15f))
                                else Modifier
                            )
                            .clickable {
                                fullScreenStartPage = index
                                showFullScreen = true
                            },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }

    if (showFullScreen) {
        FullScreenImageViewer(
            images = images,
            initialPage = fullScreenStartPage,
            onDismiss = { showFullScreen = false },
            onDeleteImage = { image ->
                onDeleteImage(image)
                // Si borramos la última imagen visible, cerramos el visor; si quedan más, se sigue viendo
                if (images.size <= 1) {
                    showFullScreen = false
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FullScreenImageViewer(
    images: List<ContactImage>,
    initialPage: Int,
    onDismiss: () -> Unit,
    onDeleteImage: (ContactImage) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
        pageCount = { images.size }
    )
    var imageToDelete by remember { mutableStateOf<ContactImage?>(null) }

    // Si la lista de imágenes cambia (por ejemplo, se eliminó una) y ya no queda ninguna, cerramos
    LaunchedEffect(images) {
        if (images.isEmpty()) onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page >= images.size) return@HorizontalPager
                var scale by remember { mutableStateOf(1f) }

                AsyncImage(
                    model = resolveImageModel(images[page]),
                    contentDescription = "Imagen ampliada ${page + 1} de ${images.size}",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                scale = if (scale > 1f) 1f else 2.5f
                            })
                        },
                    contentScale = ContentScale.Fit
                )
            }

            // Barra superior: cerrar, contador, y eliminar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (images.size > 1) {
                    Text(
                        text = "${(pagerState.currentPage + 1).coerceAtMost(images.size)} / ${images.size}",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val currentImage = images.getOrNull(pagerState.currentPage)
                    if (currentImage != null) imageToDelete = currentImage
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar imagen", tint = Color.White)
                }
            }
        }
    }

    // Confirmación antes de borrar, para evitar borrados accidentales
    imageToDelete?.let { image ->
        AlertDialog(
            onDismissRequest = { imageToDelete = null },
            title = { Text("¿Eliminar esta imagen?") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteImage(image)
                    imageToDelete = null
                }) {
                    Text("Eliminar", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { imageToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}