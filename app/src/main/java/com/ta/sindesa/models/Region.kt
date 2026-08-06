package com.ta.sindesa.models

data class Region(
    val code: String,
    val name: String
) {
    override fun toString(): String {
        return name
    }
}
