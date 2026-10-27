package com.ironquest.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ironquest.app.R

/** Bundled static fonts (OFL, licences in assets/licenses). The app stays offline. */
object IronFonts {
    val Inter = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

    /** Diary titles: Lora, a sturdy warm serif (variable font, weight picked per use; API 26+). */
    @OptIn(ExperimentalTextApi::class)
    val Lora = FontFamily(
        Font(R.font.lora_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.lora_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    val Archivo = FontFamily(
        Font(R.font.archivo_bold, FontWeight.Bold),
        Font(R.font.archivo_extrabold, FontWeight.ExtraBold),
    )
    val Nunito = FontFamily(
        Font(R.font.nunito_medium, FontWeight.Medium),
        Font(R.font.nunito_bold, FontWeight.Bold),
        Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    )
}
