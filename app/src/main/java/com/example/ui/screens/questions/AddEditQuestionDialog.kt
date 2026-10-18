package com.example.ui.screens.questions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Importance
import com.example.data.model.QuestionItem
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Shared add/edit dialog for study questions, used by the Subject, Unit, and Topic
 * detail screens so validation rules (non-blank text, positive marks) stay consistent.
 */
@Composable
fun AddEditQuestionDialog(
    subjectId: String,
    initialQuestion: QuestionItem? = null,
    onDismiss: () -> Unit,
    onSave: (String, Int, Importance, String) -> Unit
) {
    var questionText by remember { mutableStateOf(initialQuestion?.questionText ?: "") }
    var marksStr by remember { mutableStateOf(initialQuestion?.marks?.toString() ?: "5") }
    var importance by remember { mutableStateOf(initialQuestion?.importance ?: Importance.MEDIUM) }
    var status by remember { mutableStateOf(initialQuestion?.status ?: "Not Prepared") }
    var error by remember { mutableStateOf<String?>(null) }

    val statuses = listOf("Not Prepared", "Learning", "Prepared", "High Priority")
    val importances = listOf(Importance.LOW, Importance.MEDIUM, Importance.HIGH)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialQuestion == null) "Add Question" else "Edit Question",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    label = { Text("Question Text") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = SurfaceElevated,
                        focusedLabelColor = PrimaryBlue,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = marksStr,
                    onValueChange = { marksStr = it },
                    label = { Text("Marks") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = SurfaceElevated,
                        focusedLabelColor = PrimaryBlue,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Importance", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    importances.forEach { imp ->
                        val selected = imp == importance
                        FilterChip(
                            selected = selected,
                            onClick = { importance = imp },
                            label = { Text(imp.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceElevated,
                                selectedContainerColor = PrimaryBlue,
                                labelColor = TextSecondary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Text(text = "Status", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    statuses.forEach { st ->
                        val selected = st.equals(status, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { status = st },
                            label = { Text(st, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceElevated,
                                selectedContainerColor = StatusGreen,
                                labelColor = TextSecondary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                error?.let {
                    Text(text = it, color = StatusRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = questionText.trim()
                    if (trimmed.isBlank()) {
                        error = "Question text cannot be blank."
                        return@Button
                    }
                    val marksInt = marksStr.toIntOrNull()
                    if (marksInt == null || marksInt <= 0) {
                        error = "Enter a valid positive number for marks."
                        return@Button
                    }
                    onSave(trimmed, marksInt, importance, status)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark
    )
}
