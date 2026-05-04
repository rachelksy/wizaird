package com.wizaird.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Detects if the app is running on a cover screen (e.g., Samsung Flip front display).
 * 
 * Cover screens have significantly smaller height than main screens.
 * We check if height is less than half of a typical phone screen (~800dp / 2 = 400dp).
 * 
 * This detection ensures UI adaptations ONLY apply to cover screens,
 * leaving the full-screen experience unchanged.
 */
@Composable
fun rememberIsCoverScreen(): Boolean {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        val height = configuration.screenHeightDp
        
        // Cover screen detection: height less than 500dp
        // Normal phones are typically 800-900dp tall
        // Cover screens are much shorter (e.g., 373dp on Flip 6)
        height < 500
    }
}
