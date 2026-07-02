package com.example

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.draw.alpha
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.Canvas

class MainActivity : ComponentActivity() {
    private lateinit var appViewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appViewModel = androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java]
        setContent {
            val settingsState by appViewModel.settings.collectAsState()

            val darkTheme = when (settingsState?.themeMode ?: "SYSTEM") {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationWrapper(viewModel = appViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            appViewModel.checkNotificationAccess()
            appViewModel.updateRepliesTodayCount()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun AppNavigationWrapper(viewModel: AppViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            viewModel.navigateTo("LOGIN")
        } else {
            viewModel.navigateTo("DASHBOARD")
        }
    }

    when (currentScreen) {
        "LOGIN" -> LoginScreen(viewModel)
        "REGISTER" -> RegisterScreen(viewModel)
        "FORGOT" -> ForgotPasswordScreen(viewModel)
        else -> {
            if (currentUser != null) {
                MainAppScaffold(viewModel = viewModel)
            } else {
                LoginScreen(viewModel)
            }
        }
    }
}

// ==========================================
// 1. AUTHENTICATION SCREENS
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val authMessage by viewModel.authStateMessage.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "App Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "COMMANDAR",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Command Your Day",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", style = MaterialTheme.typography.bodyMedium) },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = "Toggle password visibility", modifier = Modifier.size(20.dp))
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                authMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.login(email, password) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("login_button")
                ) {
                    Text("Log In", style = MaterialTheme.typography.labelLarge)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { viewModel.navigateTo("REGISTER") },
                    modifier = Modifier.testTag("go_to_register_button")
                ) {
                    Text(
                        "Create Account",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(
                    onClick = { viewModel.navigateTo("FORGOT") },
                    modifier = Modifier.testTag("forgot_password_button")
                ) {
                    Text(
                        "Forgot Password?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(viewModel: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val authMessage by viewModel.authStateMessage.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = "Register",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Create Account",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Establish a localized auto-reply schedule managed by AI.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.fillMaxWidth().testTag("name_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.fillMaxWidth().testTag("email_register_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", style = MaterialTheme.typography.bodyMedium) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("password_register_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                authMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.register(name, email, password) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("register_button")
                ) {
                    Text("Register", style = MaterialTheme.typography.labelLarge)
                }
            }

            TextButton(
                onClick = { viewModel.navigateTo("LOGIN") },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    "Already have an account? Log In",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(viewModel: AppViewModel) {
    var email by remember { mutableStateOf("") }
    val authMessage by viewModel.authStateMessage.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LockReset,
                    contentDescription = "Reset",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Reset Password",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enter your email to receive recovery instructions.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.fillMaxWidth().testTag("forgot_email_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                authMessage?.let {
                    Text(
                        text = it,
                        color = if (it.contains("Simulate") || it.contains("sent")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.resetPassword(email) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("reset_password_button")
                ) {
                    Text("Send Instructions", style = MaterialTheme.typography.labelLarge)
                }
            }

            TextButton(
                onClick = { viewModel.navigateTo("LOGIN") },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    "Back to Login",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ==========================================
// 2. MAIN APPLICATION WORKSPACE
// ==========================================

@Composable
fun MainAppScaffold(viewModel: AppViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    Scaffold(
        bottomBar = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    val screens = listOf(
                        Triple("DASHBOARD", "Dashboard", Icons.Filled.Dashboard),
                        Triple("FOCUS", "Focus", Icons.Filled.Timer),
                        Triple("HABITS", "Habits", Icons.Filled.Checklist),
                        Triple("ACTIVITY", "Activity", Icons.Filled.BarChart),
                        Triple("SETTINGS", "Settings", Icons.Filled.Settings)
                    )

                    screens.forEach { (screenId, label, icon) ->
                        val isSelected = currentScreen == screenId
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.navigateTo(screenId) },
                            icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.tertiary,
                                unselectedTextColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.testTag("nav_item_${screenId.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "DASHBOARD" -> DashboardScreen(viewModel)
                "FOCUS" -> FocusScreen(viewModel)
                "HABITS" -> HabitsScreen(viewModel)
                "ACTIVITY" -> ActivityScreen(viewModel)
                "SETTINGS" -> SettingsScreen(viewModel)
                else -> DashboardScreen(viewModel)
            }
        }
    }
}

// ==========================================
// SCREEN: DASHBOARD
// ==========================================

@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val isAccessGranted by viewModel.isNotificationAccessGranted.collectAsState()
    val isUsageGranted by viewModel.isUsageAccessGranted.collectAsState()
    val isOverlayGranted by viewModel.isOverlayAccessGranted.collectAsState()
    val repliesToday by viewModel.repliesTodayCount.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val todayLogs by viewModel.todayLogs.collectAsState()
    val focusSessions by viewModel.focusSessions.collectAsState()
    val history by viewModel.history.collectAsState()

    val isTimerActive by viewModel.isFocusActive.collectAsState()
    val remainingSeconds by viewModel.focusRemainingSeconds.collectAsState()
    val focusModeName by viewModel.focusMode.collectAsState()

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "COMMANDAR",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Responding When You Can't",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { viewModel.logout() }) {
                    Icon(
                        imageVector = Icons.Filled.ExitToApp,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Consolidated Permission Card
        if (!isAccessGranted || !isUsageGranted || !isOverlayGranted) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "COMMAND SHIELD INCOMPLETE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Grant the missing access permissions below to activate automated responses and strict app blocking.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                        
                        if (!isAccessGranted) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Notification Intercept Access")
                            }
                        }
                        
                        if (!isUsageGranted) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Usage Statistics Access")
                            }
                        }
                        
                        if (!isOverlayGranted) {
                            Button(
                                onClick = {
                                    val overlayIntent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(overlayIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Overlay Permission")
                            }
                        }
                    }
                }
            }
        }

        // Welcome Greeting
        item {
            Text(
                text = "$greeting, ${viewModel.currentUser.value?.name ?: "Tawfeeq"}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Current Active Timer Card (If Focus Session active)
        if (isTimerActive) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "CURRENT MISSION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = focusModeName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val mins = remainingSeconds / 60
                                val secs = remainingSeconds % 60
                                Text(
                                    text = String.format("%02d:%02d remaining", mins, secs),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF34C759))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Shield ACTIVE", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.stopFocusSession(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("End Focus Session")
                        }
                    }
                }
            }
        }

        // Today's Score Card
        item {
            val startOfToday = remember {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            val completedToday = focusSessions.filter { it.completed && it.timestamp >= startOfToday }
            val focusMinutes = completedToday.sumOf { it.durationMinutes }
            val hours = focusMinutes / 60
            val mins = focusMinutes % 60
            val focusTimeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            
            val totalHabitsCount = habits.size
            val completedHabitsCount = todayLogs.filter { it.isCompleted }.size

            val gymDone = habits.find { it.name.lowercase().contains("gym") }?.let { gymHabit ->
                todayLogs.find { it.habitId == gymHabit.id }?.isCompleted == true
            } ?: false

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "TODAY'S PROGRESS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Focus Time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(focusTimeStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Habits Done", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$completedHabitsCount/$totalHabitsCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Gym Status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (gymDone) "✅ Done" else "☐ Pending", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Gamification / Level & XP Card
        item {
            val userXp = settings?.currentXp ?: 2450
            val userLevel = settings?.userLevel ?: 12
            val progress = (userXp % 1000) / 1000f

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LEVEL $userLevel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$userXp XP Total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Filled.LocalFireDepartment, contentDescription = "Streak", tint = Color(0xFFFF9500))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("28 Day Streak", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        // AI Secretary Console
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "AI SECRETARY STATUS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (settings?.isAutoReplyEnabled == true) "ACTIVE" else "SUSPENDED",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (settings?.isAutoReplyEnabled == true) Color(0xFF34C759) else Color(0xFFFF9500)
                            )
                        }

                        Switch(
                            checked = settings?.isAutoReplyEnabled == true,
                            onCheckedChange = { viewModel.updateAutoReplyToggle(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Current Mode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(settings?.activeStatusName ?: "Study", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("Availability", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            val availText = if (isTimerActive) {
                                val calendar = Calendar.getInstance()
                                calendar.add(Calendar.SECOND, remainingSeconds)
                                SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
                            } else {
                                "Available Now"
                            }
                            Text(availText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Replies", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$repliesToday", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Recent Auto Replies
        item {
            Text(
                text = "Recent Auto Replies",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (history.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "No history records today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(history.take(4)) { log ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = log.contactName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val timeStr = remember(log.timestamp) {
                                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(log.timestamp))
                            }
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("RECEIVED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("\"${log.receivedMessage}\"", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("COMMANDAR RESPONSE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Text("\"${log.sentReply}\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN: FOCUS (TIMER & SETUP)
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val isTimerActive by viewModel.isFocusActive.collectAsState()
    val remainingSeconds by viewModel.focusRemainingSeconds.collectAsState()
    val focusTotalSeconds by viewModel.focusTotalSeconds.collectAsState()
    val focusModeName by viewModel.focusMode.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var selectedMode by remember { mutableStateOf("Study") }
    var selectedDurationMinutes by remember { mutableStateOf(25f) }
    var strictModeEnabled by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isTimerActive) {
            // Setup Screen
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Text(
                        text = "FOCUS ENGINE",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "Select your preset focus mode below.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Modes Preset Grid
                item {
                    val presets = listOf("Study", "Gym", "Prayer", "Work", "Sleep", "Meeting")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in presets.indices step 3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (j in 0 until 3) {
                                    if (i + j < presets.size) {
                                        val mode = presets[i + j]
                                        val isSelected = selectedMode == mode
                                        Card(
                                            onClick = { selectedMode = mode },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(64.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(mode, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Duration Selectors
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Session Length: ${selectedDurationMinutes.toInt()} minutes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(15f, 25f, 50f, 90f).forEach { mins ->
                                    val isSelected = selectedDurationMinutes == mins
                                    Card(
                                        onClick = { selectedDurationMinutes = mins },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("${mins.toInt()}m", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Strict Mode Block Toggle & Faded Icons
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Strict Mode App Blocking", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Instagram, YouTube, Facebook, and Chrome are locked when active.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = strictModeEnabled,
                                    onCheckedChange = { strictModeEnabled = it }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "BLOCKED APPS SHIELD LIST",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (strictModeEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val blockedAppsDisplay = listOf(
                                    "Instagram" to Icons.Filled.PhotoCamera,
                                    "YouTube" to Icons.Filled.PlayCircle,
                                    "Facebook" to Icons.Filled.People,
                                    "Chrome" to Icons.Filled.Web
                                )
                                blockedAppsDisplay.forEach { (name, icon) ->
                                    val alphaVal = if (strictModeEnabled) 1.0f else 0.35f
                                    val tintColor = if (strictModeEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .alpha(alphaVal)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(if (strictModeEnabled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = name,
                                                tint = tintColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = tintColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Start Button
                item {
                    Button(
                        onClick = {
                            viewModel.startFocusSession(
                                context,
                                selectedDurationMinutes.toInt(),
                                selectedMode,
                                strictModeEnabled
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Start Focus Session (+XP Rewards)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        } else {
            // Running Timer Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "FOCUS MODE ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = focusModeName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(240.dp)
                ) {
                    val progress = if (focusTotalSeconds > 0) remainingSeconds.toFloat() / focusTotalSeconds else 0f
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(220.dp),
                        strokeWidth = 10.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    val minutes = remainingSeconds / 60
                    val seconds = remainingSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Shield is blocking notification alerts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = { viewModel.stopFocusSession(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("End Session Early (Penalizes XP)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// SCREEN: HABITS CHECKLIST
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(viewModel: AppViewModel) {
    val habits by viewModel.habits.collectAsState()
    val todayLogs by viewModel.todayLogs.collectAsState()

    var showAddHabitDialog by remember { mutableStateOf(false) }
    var newHabitName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "DAILY ROUTINES",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "Track your routines and gain XP. Don't break the streak!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Habits Progress Card
            item {
                val total = habits.size
                val done = todayLogs.filter { it.isCompleted }.size
                val progress = if (total > 0) done.toFloat() / total else 0f

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Today's Score", fontWeight = FontWeight.Bold)
                            Text("${(progress * 100).toInt()}% Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            // Checklist items
            if (habits.isEmpty()) {
                item {
                    Text(
                        "No habits created yet. Click below to add your first routine!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                    )
                }
            } else {
                items(habits) { habit ->
                    val isChecked = todayLogs.find { it.habitId == habit.id }?.isCompleted == true
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                               ) {
                                   Icon(
                                       imageVector = when(habit.iconName.lowercase()) {
                                           "dumbbell", "gym" -> Icons.Filled.FitnessCenter
                                           "medication", "pill" -> Icons.Filled.MedicalServices
                                           "book", "read" -> Icons.Filled.Book
                                           "selfimprovement", "prayer" -> Icons.Filled.SelfImprovement
                                           "code", "coding" -> Icons.Filled.Code
                                           else -> Icons.Filled.TaskAlt
                                       },
                                       contentDescription = habit.iconName,
                                       tint = MaterialTheme.colorScheme.primary,
                                       modifier = Modifier.size(22.dp)
                                   )
                               }
                               Spacer(modifier = Modifier.width(16.dp))
                               Column {
                                   Text(
                                       text = habit.name,
                                       style = MaterialTheme.typography.titleMedium,
                                       fontWeight = FontWeight.Bold,
                                       color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                   )
                                   Spacer(modifier = Modifier.height(2.dp))
                                   Text(
                                       text = when(habit.name.lowercase()) {
                                           "gym" -> "Daily fitness & exercise • 12 Day Streak"
                                           "minoxidil" -> "Hair & skin routine • 28 Day Streak"
                                           "read 20 mins" -> "Daily reading habit • 7 Day Streak"
                                           "fajr" -> "Morning prayer routine • 112 Day Streak"
                                           "dhuhr" -> "Noon prayer routine • 112 Day Streak"
                                           else -> "Daily routine checklist"
                                       },
                                       style = MaterialTheme.typography.bodySmall,
                                       color = MaterialTheme.colorScheme.onSurfaceVariant
                                   )
                               }
                            }
                            
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.toggleHabit(habit.id, it) }
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { showAddHabitDialog = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+ Add Custom Habit", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showAddHabitDialog) {
            Dialog(onDismissRequest = { showAddHabitDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Add New Routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = newHabitName,
                            onValueChange = { newHabitName = it },
                            label = { Text("Habit Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        var selectedIcon by remember { mutableStateOf("Dumbbell") }
                        Text("Select Icon", fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val icons = listOf("Dumbbell", "Medication", "Book", "SelfImprovement", "Code")
                            icons.forEach { icon ->
                                FilterChip(
                                    selected = selectedIcon == icon,
                                    onClick = { selectedIcon = icon },
                                    label = { Text(icon) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showAddHabitDialog = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newHabitName.isNotBlank()) {
                                        viewModel.addHabit(newHabitName, selectedIcon)
                                        newHabitName = ""
                                        showAddHabitDialog = false
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN: ACTIVITY & FOCUS FOREST
// ==========================================

@Composable
fun ActivityScreen(viewModel: AppViewModel) {
    val forestCells by viewModel.forestCells.collectAsState()
    val focusSessions by viewModel.focusSessions.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "PRODUCTIVITY FOREST",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Complete focus missions to grow your forest city.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 5x5 grid map visualizer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Your Forest City", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    for (r in 0 until 5) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            for (c in 0 until 5) {
                                val cell = forestCells.find { it.row == r && it.col == c }
                                val cellType = cell?.cellType ?: "EMPTY"
                                
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when (cellType) {
                                                "TREE" -> Color(0xFF34C759).copy(alpha = 0.15f)
                                                "DEAD_TREE" -> Color(0xFFFF9500).copy(alpha = 0.15f)
                                                "BUILDING" -> Color(0xFF5856D6).copy(alpha = 0.15f)
                                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (cellType) {
                                            "TREE" -> Icons.Filled.NaturePeople
                                            "DEAD_TREE" -> Icons.Filled.Warning
                                            "BUILDING" -> Icons.Filled.HomeWork
                                            else -> Icons.Filled.Add
                                        },
                                        contentDescription = cellType,
                                        tint = when (cellType) {
                                            "TREE" -> Color(0xFF34C759)
                                            "DEAD_TREE" -> Color(0xFFFF9500)
                                            "BUILDING" -> Color(0xFF5856D6)
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Stats section
        item {
            val totalSessions = focusSessions.size
            val completedSessions = focusSessions.filter { it.completed }.size
            val totalDistractions = focusSessions.sumOf { it.distractionCount }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Analytics Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Sessions Started", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("$totalSessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Sessions Finished", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("$completedSessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Aborted Runs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("${totalSessions - completedSessions}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Distractions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("$totalDistractions times", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom Canvas Chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Focus Graph (Last 7 Days)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Canvas(modifier = Modifier.fillMaxSize().weight(1f)) {
                        val width = size.width
                        val height = size.height
                        val barCount = 7
                        val barSpacing = 16.dp.toPx()
                        val totalSpacing = barSpacing * (barCount - 1)
                        val barWidth = (width - totalSpacing) / barCount
                        
                        val data = listOf(2.5f, 4.0f, 1.2f, 3.5f, 6.2f, 2.0f, 4.5f)
                        val maxVal = data.maxOrNull() ?: 1.0f
                        
                        for (i in 0 until barCount) {
                            val x = i * (barWidth + barSpacing)
                            val barHeight = (data[i] / maxVal) * (height - 30.dp.toPx())
                            val y = height - barHeight
                            
                            drawRect(
                                color = Color(0xFF5856D6),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                topLeft = androidx.compose.ui.geometry.Offset(x, y)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN: SETTINGS & CONTROLS
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var showBlockedAppsDialog by remember { mutableStateOf(false) }
    var blockedAppsString by remember { mutableStateOf(settings?.blockedAppsList ?: "") }

    var activeConfigDialog by remember { mutableStateOf<String?>(null) } // "RULES", "SCHEDULES", "STATUSES"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Configure your AI Secretary, blocker lists, and schedules.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Auto Reply Tuning", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    
                    Text("Anti-Spam Frequency: ${settings?.autoReplyDelayMinutes} mins")
                    Slider(
                        value = (settings?.autoReplyDelayMinutes ?: 5).toFloat(),
                        onValueChange = { viewModel.updateDelayMinutes(it.toInt()) },
                        valueRange = 0f..30f,
                        steps = 6
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Max Replies per Contact (Anti-Spam Threshold)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (count in 1..5) {
                            val isSelected = settings?.maxRepliesPerContact == count
                            Card(
                                onClick = { viewModel.updateSettingsConfig(settings?.autoReplyDelayMinutes ?: 5, count, settings?.themeMode ?: "SYSTEM") },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("$count", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Blocked Apps List", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Manage which package IDs strict mode blocks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { 
                        blockedAppsString = settings?.blockedAppsList ?: ""
                        showBlockedAppsDialog = true 
                    }) {
                        Text("Edit")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Rules & Preset Configurations", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    
                    Button(
                        onClick = { activeConfigDialog = "STATUSES" },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("Manage Status Mode Presets")
                    }
                    
                    Button(
                        onClick = { activeConfigDialog = "RULES" },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("Configure Contact Whitelists / Rules")
                    }

                    Button(
                        onClick = { activeConfigDialog = "SCHEDULES" },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("Configure Automated Scheduler")
                    }
                }
            }
        }
    }

    if (showBlockedAppsDialog) {
        Dialog(onDismissRequest = { showBlockedAppsDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Configure Apps Blocker List", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Enter comma-separated package names:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = blockedAppsString,
                        onValueChange = { blockedAppsString = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showBlockedAppsDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.updateBlockedApps(blockedAppsString)
                                showBlockedAppsDialog = false
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    if (activeConfigDialog == "RULES") {
        Dialog(onDismissRequest = { activeConfigDialog = null }) {
            Card(
                modifier = Modifier.fillMaxWidth().height(500.dp).padding(8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    val rules by viewModel.rules.collectAsState()
                    var contactName by remember { mutableStateOf("") }
                    var action by remember { mutableStateOf("AUTO_REPLY") }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Contact Rules Whitelist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { activeConfigDialog = null }) { Text("Close") }
                        }
                        
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rules.forEach { rule ->
                                item(key = rule.id) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(rule.contactName, fontWeight = FontWeight.Bold)
                                                Text("Action: ${rule.action}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            IconButton(onClick = { viewModel.deleteContactRule(rule.id) }) {
                                                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(value = contactName, onValueChange = { contactName = it }, label = { Text("Contact Name") }, modifier = Modifier.fillMaxWidth())
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("AUTO_REPLY", "NO_REPLY").forEach { act ->
                                FilterChip(
                                    selected = action == act,
                                    onClick = { action = act },
                                    label = { Text(act) }
                                )
                            }
                        }
                        Button(
                            onClick = {
                                if (contactName.isNotBlank()) {
                                    viewModel.addContactRule(contactName, action, null)
                                    contactName = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Rule")
                        }
                    }
                }
            }
        }
    }

    if (activeConfigDialog == "SCHEDULES") {
        Dialog(onDismissRequest = { activeConfigDialog = null }) {
            Card(
                modifier = Modifier.fillMaxWidth().height(500.dp).padding(8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    val schedules by viewModel.schedules.collectAsState()
                    var start by remember { mutableStateOf("09:00") }
                    var end by remember { mutableStateOf("17:00") }
                    var mode by remember { mutableStateOf("Study") }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Automated Schedules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { activeConfigDialog = null }) { Text("Close") }
                        }

                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            schedules.forEach { sch ->
                                item(key = sch.id) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Time: ${sch.startTime} - ${sch.endTime}", fontWeight = FontWeight.Bold)
                                                Text("Mode: ${sch.modeName}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            IconButton(onClick = { viewModel.deleteSchedule(sch.id) }) {
                                                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start HH:MM") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End HH:MM") }, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(value = mode, onValueChange = { mode = it }, label = { Text("Mode Name") }, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                if (start.isNotBlank() && end.isNotBlank() && mode.isNotBlank()) {
                                    viewModel.addSchedule(start, end, mode)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Schedule")
                        }
                    }
                }
            }
        }
    }

    if (activeConfigDialog == "STATUSES") {
        Dialog(onDismissRequest = { activeConfigDialog = null }) {
            Card(
                modifier = Modifier.fillMaxWidth().height(500.dp).padding(8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    val statuses by viewModel.statuses.collectAsState()
                    var name by remember { mutableStateOf("") }
                    var msg by remember { mutableStateOf("") }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Custom Status Presets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { activeConfigDialog = null }) { Text("Close") }
                        }

                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            statuses.forEach { status ->
                                item(key = status.id) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(status.name, fontWeight = FontWeight.Bold)
                                                Text("Reply: ${status.replyMessage}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            if (status.isCustom) {
                                                IconButton(onClick = { viewModel.deleteStatus(status.id) }) {
                                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Preset Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = msg, onValueChange = { msg = it }, label = { Text("Auto-Reply Message") }, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                if (name.isNotBlank() && msg.isNotBlank()) {
                                    viewModel.addCustomStatus(name, msg)
                                    name = ""
                                    msg = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Custom Preset")
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// UTILS & HELPERS
// ==========================================

fun getStatusIcon(iconName: String): ImageVector {
    return when (iconName.lowercase()) {
        "school", "study" -> Icons.Filled.School
        "fitness", "gym" -> Icons.Filled.FitnessCenter
        "selfimprovement", "prayer" -> Icons.Filled.SelfImprovement
        "meetingroom", "meeting" -> Icons.Filled.MeetingRoom
        "bedtime", "sleep" -> Icons.Filled.Bedtime
        "directionscar", "driving" -> Icons.Filled.DirectionsCar
        "work" -> Icons.Filled.Work
        "autoawesome", "ai assistant", "ai" -> Icons.Filled.AutoAwesome
        else -> Icons.Filled.School
    }
}
