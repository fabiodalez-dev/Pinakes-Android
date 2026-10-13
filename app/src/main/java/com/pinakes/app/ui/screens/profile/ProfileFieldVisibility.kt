package com.pinakes.app.ui.screens.profile

import com.pinakes.app.data.model.ProfileFieldRule

/**
 * Which optional profile fields the library shows. The administrator can hide gender
 * (`sesso`) and tax code (`cod_fiscale`); a hidden field is neither rendered nor sent.
 * Sources, any of which hides the field: `GET /me` `hidden_fields`, the registration schema
 * `profile_fields.*.visible`, `/health` `registration.show_*`. Servers that send none of them
 * (before 0.8.5) keep every field visible. Pure, unit-tested.
 */
object ProfileFieldVisibility {
    const val GENDER = "sesso"
    const val TAX_CODE = "cod_fiscale"

    fun isVisible(
        field: String,
        hiddenFields: List<String> = emptyList(),
        profileFields: Map<String, ProfileFieldRule> = emptyMap(),
        healthShows: Boolean? = null,
    ): Boolean = field !in hiddenFields && profileFields[field]?.visible != false && healthShows != false
}
