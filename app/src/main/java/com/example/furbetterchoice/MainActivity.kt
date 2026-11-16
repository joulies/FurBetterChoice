package com.example.furbetterchoice

import android.app.Activity
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.furbetterchoice.ui.*
import com.example.furbetterchoice.ui.SuitableFoodScreen
import com.example.furbetterchoice.ui.theme.FurBetterChoiceTheme
import android.content.pm.ApplicationInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            FirebaseApp.initializeApp(this)
            runCatching {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(50L * 1024L * 1024L)
                            .build()
                    )
                    .build()
                FirebaseFirestore.getInstance().firestoreSettings = settings
            }
            initFirebaseAppCheck()
            viewModel.initOfflinePersistence(applicationContext)
            viewModel.initAuth()
        } catch (e: Exception) {
        }

        // Disable navigation bar contrast to make it truly transparent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            
            DisposableEffect(isDarkMode) {
                val barStyle = if (isDarkMode) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
                onDispose { }
            }

            Crossfade(targetState = isDarkMode, animationSpec = tween(300), label = "themeFade") { dark ->
                FurBetterChoiceTheme(darkTheme = dark) {
                    AppMainContainer(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }

    private fun initFirebaseAppCheck() {
        val appCheck = FirebaseAppCheck.getInstance()
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val factory: AppCheckProviderFactory = if (isDebuggable) {
            runCatching {
                Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                    .getMethod("getInstance")
                    .invoke(null) as? AppCheckProviderFactory
            }.getOrNull() ?: PlayIntegrityAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        appCheck.installAppCheckProviderFactory(factory)
    }
}

@Composable
fun AppMainContainer(viewModel: AppViewModel) {
    UserAppContainer(viewModel = viewModel)
}

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun UserAppContainer(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current

    // Offline status detection
    var isOffline by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                isOffline = true
            }
            override fun onAvailable(network: Network) {
                isOffline = false
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback)
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
            val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            if (!connected) {
                isOffline = true
            }
        } catch (e: Exception) {}

        onDispose {
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {}
        }
    }

    if (isOffline) {
        OfflineDialog(onDismiss = { isOffline = false })
    }

    // Exit confirmation state
    var showExitDialog by remember { mutableStateOf(false) }

    // Handle back press for app exit
    BackHandler(enabled = true) {
        if (navController.previousBackStackEntry == null) {
            showExitDialog = true
        } else {
            navController.popBackStack()
        }
    }

    if (showExitDialog) {
        CustomConfirmationDialog(
            title = "Exit App",
            message = "Are you sure you want to close FurBetterChoice?",
            confirmText = "Exit",
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            onDismiss = { showExitDialog = false },
            onConfirm = {
                (context as? Activity)?.finish()
            }
        )
    }

    // Hide bottom bar for sub-screens like scanner, details, my_pets, saved_products
    val showBottomBar = currentRoute in listOf("home", "feedback", "profile", "settings")

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    
    // Scroll state for nav bar shrinking
    var isShrunk by remember { mutableStateOf(false) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -10) { // Scrolling down
                    isShrunk = true
                } else if (available.y > 10) { // Scrolling up
                    isShrunk = false
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        containerColor = Color.Transparent, // Allow global background to show through
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400, easing = EaseOutCubic)) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(350, easing = EaseOutCubic)) + fadeOut()
            ) {
                FloatingBottomNav(
                    currentRoute = currentRoute,
                    isDark = isDarkMode,
                    isShrunk = isShrunk,
                    onExpand = { isShrunk = false },
                    onNavigate = { route ->
                        if (route != currentRoute) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // Disable default Scaffold insets
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AppNavigation(
                navController = navController,
                viewModel = viewModel,
                modifier = Modifier.padding(
                    top = 0.dp,
                    bottom = 0.dp // Allow content to flow behind bottom bar
                )
            )
        }
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val startDestination = "home"
    val bottomNavRoutes = listOf("home", "profile", "feedback", "settings")

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route
            val initialIndex = bottomNavRoutes.indexOf(initialRoute)
            val targetIndex = bottomNavRoutes.indexOf(targetRoute)

            if (initialIndex != -1 && targetIndex != -1) {
                if (targetIndex > initialIndex) {
                    slideInHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> width }
                } else {
                    slideInHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> -width }
                }
            } else {
                slideInHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> width }
            }
        },
        exitTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route
            val initialIndex = bottomNavRoutes.indexOf(initialRoute)
            val targetIndex = bottomNavRoutes.indexOf(targetRoute)

            if (initialIndex != -1 && targetIndex != -1) {
                if (targetIndex > initialIndex) {
                    slideOutHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> -width }
                } else {
                    slideOutHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> width }
                }
            } else {
                slideOutHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> -width }
            }
        },
        popEnterTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route
            val initialIndex = bottomNavRoutes.indexOf(initialRoute)
            val targetIndex = bottomNavRoutes.indexOf(targetRoute)

            if (initialIndex != -1 && targetIndex != -1) {
                if (targetIndex > initialIndex) {
                    slideInHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> width }
                } else {
                    slideInHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> -width }
                }
            } else {
                slideInHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> -width }
            }
        },
        popExitTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route
            val initialIndex = bottomNavRoutes.indexOf(initialRoute)
            val targetIndex = bottomNavRoutes.indexOf(targetRoute)

            if (initialIndex != -1 && targetIndex != -1) {
                if (targetIndex > initialIndex) {
                    slideOutHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> -width }
                } else {
                    slideOutHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> width }
                }
            } else {
                slideOutHorizontally(animationSpec = tween(350, easing = EaseInOutCubic)) { width -> width }
            }
        }
    ) {
        composable("login") {
            LoginScreen(
                onNavigateToSignUp = { navController.navigate("signup") },
                onNavigateToForgotPassword = { navController.navigate("forgot_password") },
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("signup") {
            SignUpScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onRegistrationSuccess = { email ->
                    navController.navigate("verification/$email") {
                        popUpTo("login")
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "verification/{email}",
            arguments = listOf(navArgument("email") { type = NavType.StringType })
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: ""
            EmailVerificationScreen(
                email = email,
                onVerified = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBackToLogin = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("forgot_password") {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("home") {
            MainDashboard(
                viewModel = viewModel,
                onScanClick = { navController.navigate("scanner") },
                onSeeAllClick = { navController.navigate("recent_history") },
                onProductClick = { barcode -> navController.navigate("details/$barcode") }
            )
        }
        composable("profile") {
            ProfileScreen(
                viewModel = viewModel,
                onNavigateToSaved = { navController.navigate("saved_products") },
                onNavigateToPets = { navController.navigate("my_pets") },
                onNavigateToLogin = { navController.navigate("login") }
            )
        }
        composable(
            route = "saved_products?compareMode={compareMode}&compareBarcodes={compareBarcodes}",
            arguments = listOf(
                navArgument("compareMode") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("compareBarcodes") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val isCompareMode = backStackEntry.arguments?.getBoolean("compareMode") ?: false
            val rawCompare = backStackEntry.arguments?.getString("compareBarcodes") ?: ""
            val decodedCompare = if (isCompareMode || rawCompare.isNotEmpty()) {
                android.net.Uri.decode(rawCompare)
            } else {
                null
            }
            SavedScreen(
                viewModel = viewModel,
                compareBarcodesParam = decodedCompare,
                onBack = { navController.popBackStack() },
                onProductClick = { barcode -> navController.navigate("details/$barcode") },
                onCompareClick = { barcodes ->
                    val encoded = android.net.Uri.encode(barcodes)
                    if (decodedCompare != null) {
                        navController.navigate("compare?barcodes=$encoded") {
                            popUpTo("compare?barcodes={barcodes}") { inclusive = true }
                        }
                    } else {
                        navController.navigate("compare?barcodes=$encoded")
                    }
                },
                onNavigateToLogin = { navController.navigate("login") }
            )
        }
        composable("my_pets") {
            MyPetsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onFindFood = { petId -> navController.navigate("suitable_food/$petId") },
                onNavigateToLogin = { navController.navigate("login") }
            )
        }
        composable("feedback") {
            FeedbackScreen(
                viewModel = viewModel,
                onNavigateToLogin = { navController.navigate("login") }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateToLogin = { navController.navigate("login") },
                onSignOut = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("recent_history") {
            RecentHistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onProductClick = { barcode -> navController.navigate("details/$barcode") }
            )
        }
        composable("scanner") {
            ProductScanner(
                viewModel = viewModel,
                onClose = { navController.popBackStack() },
                onProductClick = { code ->
                    val trimmed = code.trim()
                    if (trimmed.isNotEmpty()) {
                        navController.navigate("details/${android.net.Uri.encode(trimmed)}")
                    }
                },
                onCompareClick = { barcodes ->
                    val encoded = android.net.Uri.encode(barcodes)
                    navController.navigate("compare?barcodes=$encoded")
                },
                onNavigateToLogin = { navController.navigate("login") }
            )
        }
        composable(
            route = "details/{barcode}",
            arguments = listOf(navArgument("barcode") { type = NavType.StringType })
        ) { backStackEntry ->
            val rawBarcode = backStackEntry.arguments?.getString("barcode") ?: ""
            val barcode = remember(rawBarcode) { android.net.Uri.decode(rawBarcode) }
            ProductAnalysisScreen(
                barcode = barcode,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onHome = {
                    navController.navigate("home") {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSuggestProduct = {
                    navController.navigate("feedback") {
                        popUpTo("home") {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                onProductClick = { code ->
                    val trimmed = code.trim()
                    if (trimmed.isNotEmpty()) {
                        navController.navigate("details/${android.net.Uri.encode(trimmed)}")
                    }
                },
                onCompareClick = { barcodes ->
                    val encoded = android.net.Uri.encode(barcodes)
                    navController.navigate("compare?barcodes=$encoded")
                },
                onNavigateToLogin = { navController.navigate("login") }
            )
        }
        composable(
            route = "suitable_food/{petId}",
            arguments = listOf(navArgument("petId") { type = NavType.StringType })
        ) { backStackEntry ->
            val petId = backStackEntry.arguments?.getString("petId") ?: ""
            SuitableFoodScreen(
                petId = petId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onProductClick = { code: String -> navController.navigate("details/$code") }
            )
        }
        composable(
            route = "compare?barcodes={barcodes}",
            arguments = listOf(navArgument("barcodes") {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            })
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("barcodes") ?: ""
            val decoded = android.net.Uri.decode(raw)
            CompareScreen(
                barcodesParam = decoded,
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onProductClick = { code -> navController.navigate("details/$code") },
                onAddProductClick = { currentActiveBarcodes ->
                    val encodedBarcodes = android.net.Uri.encode(currentActiveBarcodes)
                    navController.navigate("saved_products?compareMode=true&compareBarcodes=$encodedBarcodes")
                }
            )
        }
    }
}

@Composable
fun OfflineDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "You're Offline",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Please check your internet connection. Some features may be limited while offline.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "Dismiss",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AppNavigationPreview() {
    val viewModel = AppViewModel()
    FurBetterChoiceTheme(darkTheme = false) {
        AppMainContainer(viewModel)
    }
}
