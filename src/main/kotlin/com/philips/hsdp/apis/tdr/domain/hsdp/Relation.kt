/**
 * Copyright (c) 2020-2021, Koninklijke Philips N.V., https://www.philips.com
 * SPDX-License-Identifier: MIT
 */
package com.philips.hsdp.apis.tdr.domain.hsdp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Description of the type of link, see also [Link][com.philips.hsdp.apis.tdr.domain.hsdp.Link].
 */
@Serializable
enum class Relation {
    @SerialName("next") Next,
}
