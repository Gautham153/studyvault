package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuthState
import com.example.ui.components.CardWithSubtleBorder
import com.example.ui.components.ProgressBarWithPercent
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.theme.BackgroundNavy
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusRed
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSignOutSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel
) {
    var showSignOutDialog by remember { mutableStateOf(false) }
    var isSigningOut by remember { mutableStateOf(false) }
    var signOutErrorMessage by remember { mutableStateOf<String?>(null) }

    val authState by authViewModel.authState.collectAsState()
    val repository = com.example.data.repository.StudyVaultRepositoryProvider.repository
    val overallProgress by repository.overallProgress.collectAsState()
    val upcomingExams by repository.upcomingExams.collectAsState()

    val userEmail = when (val state = authState) {
        is AuthState.Authenticated -> state.user.email
        else -> overallProgress.studentEmail
    }
    val userName = when (val state = authState) {
        is AuthState.Authenticated -> state.user.displayName.ifBlank { overallProgress.studentName.ifBlank { "Student" } }
        else -> overallProgress.studentName.ifBlank { "Student" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundNavy)
            .testTag("settings_screen")
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Profile & Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BackgroundNavy
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Profile Card displaying authenticated user details
            item {
                CardWithSubtleBorder(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SurfaceDark
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated)
                                .border(1.5.dp, PrimaryBlueLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Avatar",
                                tint = PrimaryBlueLight,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Academic Details (informational)
            item {
                SettingsInfoItem(
                    icon = Icons.Default.School,
                    title = "Academic Details",
                    subtitle = "${overallProgress.semester} • Computer Science"
                )
            }

            item {
                SettingsInfoItem(
                    icon = Icons.Default.EventNote,
                    title = "Exam Dates",
                    subtitle = "${upcomingExams.size} upcoming exams scheduled"
                )
            }

            // Appearance Toggle
            item {
                CardWithSubtleBorder(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = PrimaryBlueLight,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Appearance",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Dark mode (always on)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = true,
                            onCheckedChange = null,
                            enabled = false,
                            colors = SwitchDefaults.colors(
                                disabledCheckedThumbColor = TextPrimary,
                                disabledCheckedTrackColor = PrimaryBlueLight
                            )
                        )
                    }
                }
            }

            item {
                SettingsInfoItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Revision reminders & exam alerts"
                )
            }

            // Cloud Storage Indicator
            item {
                CardWithSubtleBorder(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = PrimaryBlueLight,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Cloud Storage",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "Usage unavailable",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }

            item {
                SettingsInfoItem(
                    icon = Icons.Default.HelpOutline,
                    title = "Help & Support",
                    subtitle = "FAQs & feedback"
                )
            }

            item {
                SettingsInfoItem(
                    icon = Icons.Default.Info,
                    title = "About StudyVault",
                    subtitle = "Version 1.1.0 • Supabase Auth"
                )
            }

            // Sign Out Option
            item {
                Spacer(modifier = Modifier.height(8.dp))
                CardWithSubtleBorder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_out_button"),
                    onClick = { showSignOutDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out",
                            tint = StatusRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.titleSmall,
                            color = StatusRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = {
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to sign out of StudyVault?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSigningOut = true
                        signOutErrorMessage = null
                        authViewModel.signOut(
                            onSuccess = {
                                isSigningOut = false
                                showSignOutDialog = false
                                onSignOutSuccess()
                            },
                            onError = { error ->
                                isSigningOut = false
                                showSignOutDialog = false
                                signOutErrorMessage = error
                            }
                        )
                    },
                    enabled = !isSigningOut,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSigningOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TextPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Sign Out", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignOutDialog = false },
                    enabled = !isSigningOut
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (signOutErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { signOutErrorMessage = null },
            title = {
                Text(
                    text = "Sign Out Failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = StatusRed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = signOutErrorMessage ?: "Unable to sign out.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { signOutErrorMessage = null }) {
                    Text("OK", color = PrimaryBlueLight, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    CardWithSubtleBorder(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryBlueLight,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}
