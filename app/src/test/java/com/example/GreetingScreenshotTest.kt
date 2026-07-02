package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        androidx.compose.material3.Surface(
          modifier = Modifier.fillMaxSize(),
          color = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) {
          Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
          ) {
            androidx.compose.material3.Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = "App Logo",
              tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Text(
              text = "Personal AI Status Assistant",
              style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
              fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
              color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            androidx.compose.material3.Text(
              text = "Smart status-based auto-responses with Gemini AI",
              style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
              color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
