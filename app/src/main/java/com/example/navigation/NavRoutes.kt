package com.example.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object ForgotPassword : Screen("forgot_password")
    object Main : Screen("main")
    object Home : Screen("home")
    object Subjects : Screen("subjects")
    object Study : Screen("study")
    object Exams : Screen("exams")
    object Profile : Screen("profile")

    object SubjectDetail : Screen("subject_detail/{subjectId}") {
        fun createRoute(subjectId: String): String = "subject_detail/$subjectId"
    }

    object SubjectQuestions : Screen("subject_questions/{subjectId}") {
        fun createRoute(subjectId: String): String = "subject_questions/$subjectId"
    }

    object UnitDetail : Screen("unit_detail/{subjectId}/{unitId}") {
        fun createRoute(subjectId: String, unitId: String): String = "unit_detail/$subjectId/$unitId"
    }

    object TopicDetail : Screen("topic_detail/{topicId}") {
        fun createRoute(topicId: String): String = "topic_detail/$topicId"
    }
}
