package com.filo.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette is lifted verbatim from the "I love you forever" site's design tokens, so the
 * app and the site read as one thing:
 *
 *   --bg:#060606  --ink:#f4e8e5  --rose:#e9bfc2  --crimson:#c1121f
 *   --ruby:#9b0f1a  --scarlet:#e63946  --line:rgba(230,57,70,.22)
 */
val Ink = Color(0xFF060606)       // app background
val Surface = Color(0xFF120607)   // card fill, the dark end of the site's card gradient
val SurfaceHigh = Color(0xFF1A090C) // the light end of that gradient
val Crimson = Color(0xFFC1121F)   // primary
val Scarlet = Color(0xFFE63946)   // bright accent, active states, the heart
val Ruby = Color(0xFF9B0F1A)      // deep red, pressed states
val RoseAsh = Color(0xFFE9BFC2)   // dusty rose: labels and secondary accents
val Bone = Color(0xFFF4E8E5)      // primary text (the site's --ink)
val Ash = Color(0xFF9C8A8C)       // muted text, timestamps
val Ember = Color(0xFFFF5966)     // warnings (low battery)

/** The site's --line: scarlet at 22%, used for every hairline. */
val Line = Color(0x38E63946)

