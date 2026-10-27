package com.ironquest.app.domain.model

/** Inclusive target range. For [TrackingType.DURATION] exercises the unit is minutes. */
data class RepRange(val min: Int, val max: Int) {
    init {
        require(min in 1..max) { "Invalid rep range $min-$max" }
    }
}
