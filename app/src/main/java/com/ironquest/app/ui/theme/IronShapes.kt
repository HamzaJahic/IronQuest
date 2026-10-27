package com.ironquest.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ironquest.app.domain.appearance.AppStyle

@Immutable
data class IronShapes(
    val button: Shape,
    /** Row tiles (Soft) and other raised containers. */
    val tile: Shape,
    val chip: Shape,
    /** Dialogs and sheets. */
    val sheet: Shape,
    /** Thin bars (progress line). */
    val bar: Shape,
) {
    companion object {
        fun of(style: AppStyle): IronShapes = when (style) {
            AppStyle.DIARY -> IronShapes(
                button = RoundedCornerShape(10.dp),
                tile = RoundedCornerShape(10.dp),
                chip = RoundedCornerShape(8.dp),
                sheet = RoundedCornerShape(16.dp),
                bar = RoundedCornerShape(3.dp),
            )
            AppStyle.BOLD -> IronShapes(
                button = RoundedCornerShape(2.dp),
                tile = RoundedCornerShape(2.dp),
                chip = RoundedCornerShape(2.dp),
                sheet = RoundedCornerShape(4.dp),
                bar = RoundedCornerShape(0.dp),
            )
            AppStyle.SOFT -> IronShapes(
                button = CircleShape,
                tile = RoundedCornerShape(22.dp),
                chip = CircleShape,
                sheet = RoundedCornerShape(28.dp),
                bar = CircleShape,
            )
        }
    }
}
