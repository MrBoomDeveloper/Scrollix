package com.mrboomdev.scrollix.features.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon

@Composable
fun LoginScreen(
    onBack: () -> Unit
) {
    val phone = rememberTextFieldState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        IconButton(
            modifier = Modifier.align(Alignment.Start),
            onClick = onBack
        ) {
            Icon(
                tint = MaterialTheme.colorScheme.secondary,
                imageVector = arrow_back_icon,
                contentDescription = null
            )
        }
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Text(
                style = MaterialTheme.typography.headlineMedium,
                text = "Your phone number"
            )
            
            Text(
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                text = "Please enter and confirm your phone number"
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 2.dp),
                state = phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    
                }
            ) { 
                Text("Next")
            }
        }
    }
}