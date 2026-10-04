package com.xnvalabs.xnai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = XnaiRepository(applicationContext)
        setContent {
            XnaiApp(repository)
        }
    }
}
