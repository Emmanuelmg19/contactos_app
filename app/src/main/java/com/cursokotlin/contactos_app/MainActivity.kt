package com.cursokotlin.contactos_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cursokotlin.contactos_app.data.local.AppDatabase
import com.cursokotlin.contactos_app.data.repository.ContactRepository
import com.cursokotlin.contactos_app.data.worker.NetworkConnectivityObserver
import com.cursokotlin.contactos_app.ui.ContactViewModel
import com.cursokotlin.contactos_app.ui.ContactViewModelFactory
import com.cursokotlin.contactos_app.ui.screens.PantallaDetalleContacto
import com.cursokotlin.contactos_app.ui.screens.PantallaRegistroContacto
import com.cursokotlin.contactos_app.ui.screens.PantallaDeContactos
import com.cursokotlin.contactos_app.ui.screens.PantallaFavoritos
import com.cursokotlin.contactos_app.ui.screens.PantallaDeBienvenida
import com.cursokotlin.contactos_app.ui.theme.Contactos_appTheme

class MainActivity : ComponentActivity() {
    // El database es nuestro almacén de datos
    private val database by lazy { AppDatabase.getDatabase(this) }

    // El repository es el administrador que maneja los pedidos a la base de datos
    private val repository by lazy { ContactRepository(database.contactDao(), this) }

    // El viewModel es el cerebro que conecta la lógica con lo que ves en pantalla
    private val viewModel: ContactViewModel by viewModels {
        ContactViewModelFactory(repository)
    }

    // Observador que detecta cuando vuelve la conexión a internet
    // y dispara la sincronización automática de la cola pendiente
    private val networkObserver by lazy { NetworkConnectivityObserver(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Hace que la app use toda la pantalla hasta los bordes
        setContent {
            Contactos_appTheme {
                ContactAppNavigation(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Empezamos a escuchar cambios de conectividad mientras la app está visible
        networkObserver.startObserving()
    }

    override fun onStop() {
        super.onStop()
        // Dejamos de escuchar cuando la app no está visible, para no gastar recursos
        networkObserver.stopObserving()
    }
}


//Este es el Mapa de Navegación de la app.
//Aquí definimos a qué pantalla ir cuando el usuario hace clic en los  botones.

@Composable
fun ContactAppNavigation(viewModel: ContactViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {

        //  Portada de inicio
        composable("splash") {
            PantallaDeBienvenida(onAnimationFinished = {
                navController.navigate("contact_list") {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }

        // Lista principal de contactos
        composable("contact_list") {
            PantallaDeContactos(
                viewModel = viewModel,
                onContactClick = { id -> navController.navigate("contact_detail/$id") },
                onAddContactClick = { navController.navigate("contact_form/-1") },
                onFavoritesClick = { navController.navigate("favorites") }
            )
        }

        // Sección de contactos favoritos
        composable("favorites") {
            PantallaFavoritos(
                viewModel = viewModel,
                onContactClick = { id -> navController.navigate("contact_detail/$id") },
                onBack = { navController.popBackStack() },
                onContactsClick = {
                    navController.navigate("contact_list") {
                        popUpTo("contact_list") { inclusive = true }
                    }
                }
            )
        }

        // Ver los detalles de una persona específica
        composable(
            route = "contact_detail/{contactId}",
            arguments = listOf(navArgument("contactId") { type = NavType.LongType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getLong("contactId") ?: return@composable
            PantallaDetalleContacto(
                viewModel = viewModel,
                contactId = contactId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate("contact_form/$id") }
            )
        }

        //Pantalla para registrar o editar contactos
        composable(
            route = "contact_form/{contactId}",
            arguments = listOf(navArgument("contactId") { type = NavType.LongType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getLong("contactId") ?: -1L
            PantallaRegistroContacto(
                viewModel = viewModel,
                contactId = if (contactId == -1L) null else contactId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}