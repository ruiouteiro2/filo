package com.filo.app.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filo.app.ui.components.Avatar
import com.filo.app.ui.components.CardValue
import com.filo.app.ui.components.FiloCard
import com.filo.app.ui.components.SectionLabel
import com.filo.app.ui.components.StaggeredEntrance
import com.filo.app.ui.components.Timestamp
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.Ember
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.Ink
import com.filo.app.ui.theme.Bone
import java.time.LocalTime

/**
 * Throwaway gallery from build phase 3. It exists so every component can be eyeballed on a
 * real device in one screen. Reachable only from the settings screen in debug builds.
 */
@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Gallery", style = FiloType.Title, color = Bone)

        StaggeredEntrance(index = 0) {
            FiloCard {
                SectionLabel("Faces")
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar("Rui", null, size = 84.dp)
                    Avatar("Ada", null, size = 84.dp)
                    Avatar(null, null, size = 84.dp)
                }
            }
        }

        StaggeredEntrance(index = 1) {
            FiloCard {
                SectionLabel("Countdown numeral")
                Spacer(Modifier.height(8.dp))
                Text("42", style = FiloType.Numeral, color = Crimson)
                Text("days until the next visit", style = FiloType.Body, color = Bone)
                Timestamp("14 September 2026")
            }
        }

        StaggeredEntrance(index = 2) {
            FiloCard {
                SectionLabel("Type scale")
                Spacer(Modifier.height(8.dp))
                Text("Screen title 32", style = FiloType.Title, color = Bone)
                CardValue("Card value 20")
                Text("Body 15. This is the reading size for notes and moods.", style = FiloType.Body, color = Bone)
                SectionLabel("Label 13")
                Timestamp("Timestamp 11 - 4 minutes ago")
            }
        }

        StaggeredEntrance(index = 3) {
            FiloCard {
                SectionLabel("States")
                Spacer(Modifier.height(8.dp))
                CardValue("78%")
                CardValue("11%", color = Ember)
                Text("Ash secondary text", style = FiloType.Body, color = Ash)
                Text("Crimson numerals and hairlines", style = FiloType.Body, color = Crimson)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
