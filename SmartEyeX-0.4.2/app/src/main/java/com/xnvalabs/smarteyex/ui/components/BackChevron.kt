package com.xnvalabs.smarteyex.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnvalabs.smarteyex.ui.theme.TextPrimaryLight

/** The "‹" back control shared by every screen header. */
@Composable
fun BackChevron(onBack: () -> Unit) {
    Text(
        "‹",
        fontSize = 26.sp,
        color = TextPrimaryLight,
        modifier = Modifier
            .clickable(onClickLabel = "Kembali", role = Role.Button, onClick = onBack)
            .padding(end = 12.dp),
    )
}
