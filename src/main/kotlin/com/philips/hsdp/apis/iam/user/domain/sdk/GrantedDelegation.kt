/**
 * Copyright (c) 2020-2021, Koninklijke Philips N.V., https://www.philips.com
 * SPDX-License-Identifier: MIT
 */
package com.philips.hsdp.apis.iam.user.domain.sdk

import kotlinx.serialization.Serializable

/**
 * Granted Delegation object.
 */
@Serializable
data class GrantedDelegation(
    val delegateeId: String,
    val validFrom: String,
    val validUntil: String,
)
