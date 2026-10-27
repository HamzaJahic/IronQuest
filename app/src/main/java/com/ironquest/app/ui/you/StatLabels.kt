package com.ironquest.app.ui.you

import androidx.annotation.StringRes
import com.ironquest.app.R
import com.ironquest.app.domain.model.StatType

/** Display name of an attribute; the enum itself carries no text (CSV and the database use its name or code). */
@StringRes
fun StatType.nameRes(): Int = when (this) {
    StatType.CONSTITUTION -> R.string.stat_constitution
    StatType.STRENGTH -> R.string.stat_strength
    StatType.STAMINA -> R.string.stat_stamina
}
