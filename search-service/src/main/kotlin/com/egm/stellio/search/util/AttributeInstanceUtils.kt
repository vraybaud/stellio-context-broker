package com.egm.stellio.search.util

fun isAttributeOfMeasureType(value: Any): Boolean =
    value is Double || value is Int

fun valueToDoubleOrNull(value: Any): Double? =
    when (value) {
        is Double -> value
        is Int -> value.toDouble()
        else -> null
    }

fun valueToStringOrNull(value: Any): String? =
    when (value) {
        is String -> value
        else -> null
    }
