package com.example

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
      MotoTheme {
        androidx.compose.material3.Surface(
          color = androidx.compose.ui.graphics.Color(0xFF121212)
        ) {
          androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.padding(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
          ) {
            androidx.compose.material3.Text(
              text = "MotoCare Assist",
              fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
              fontSize = 24.sp,
              color = androidx.compose.ui.graphics.Color.White
            )
            androidx.compose.material3.Text(
              text = "Garage Dashboard",
              fontSize = 14.sp,
              color = androidx.compose.ui.graphics.Color.LightGray
            )
          }
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
