package com.example.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.exams.ExamsScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.study.StudyScreen
import com.example.ui.screens.subjects.SubjectsScreen
import com.example.ui.theme.BackgroundNavy
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

sealed class BottomTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    object Home : BottomTab("Home", Icons.Filled.Home, Icons.Outlined.Home, "tab_home")
    object Subjects : BottomTab("Subjects", Icons.Filled.CollectionsBookmark, Icons.Outlined.CollectionsBookmark, "tab_subjects")
    object Study : BottomTab("Study", Icons.Filled.LocalFireDepartment, Icons.Outlined.LocalFireDepartment, "tab_study")
    object Exams : BottomTab("Exams", Icons.Filled.Assignment, Icons.Outlined.Assignment, "tab_exams")
}

@Composable
fun MainScaffold(
    onNavigateToSubjectDetail: (String) -> Unit,
    onNavigateToTopicDetail: (String) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(BottomTab.Home, BottomTab.Subjects, BottomTab.Study, BottomTab.Exams)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundNavy,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
            ) {
                // Subtle top border divider for bottom bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderSubtle)
                )

                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("bottom_nav_bar"),
                    containerColor = SurfaceDark,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTabIndex == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTabIndex = index },
                            modifier = Modifier.testTag(tab.testTag),
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryBlueLight,
                                selectedTextColor = PrimaryBlueLight,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextMuted,
                                indicatorColor = SurfaceElevated
                            )
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
            when (selectedTabIndex) {
                0 -> HomeScreen(
                    onNavigateToSubjectDetail = onNavigateToSubjectDetail,
                    onNavigateToTopicDetail = onNavigateToTopicDetail,
                    onNavigateToSubjectsTab = { selectedTabIndex = 1 },
                    onNavigateToStudyTab = { selectedTabIndex = 2 },
                    onOpenProfile = onOpenProfile
                )
                1 -> SubjectsScreen(
                    onNavigateToSubjectDetail = onNavigateToSubjectDetail
                )
                2 -> StudyScreen(
                    onNavigateToTopicDetail = onNavigateToTopicDetail
                )
                3 -> ExamsScreen(
                    onNavigateToSubjectDetail = onNavigateToSubjectDetail
                )
            }
        }
    }
}
