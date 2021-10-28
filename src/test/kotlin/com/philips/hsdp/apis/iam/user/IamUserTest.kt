/**
 * Copyright (c) 2020-2021, Koninklijke Philips N.V., https://www.philips.com
 * SPDX-License-Identifier: MIT
 */
package com.philips.hsdp.apis.iam.user

import com.philips.hsdp.apis.iam.oauth2.domain.sdk.Token
import com.philips.hsdp.apis.iam.user.domain.sdk.*
import com.philips.hsdp.apis.support.HttpClient
import com.philips.hsdp.apis.support.HttpException
import com.philips.hsdp.apis.support.TokenRefresher
import com.philips.hsdp.apis.support.logging.MockLoggerFactory
import com.philips.hsdp.apis.support.logging.PlatformLoggerFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.InterruptedIOException
import java.time.Duration
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class IamUserTest {

    init {
        PlatformLoggerFactory.registerConcreteFactory(MockLoggerFactory)
    }

    private val server = MockWebServer().apply {
        start()
    }
    private val tokenRefresherMock = mockk<TokenRefresher>().apply {
        every { token } returns Token(accessToken = "22a34a6e-214c-4e3e-b85f-b4bbd1448613")
    }
    val httpClient = HttpClient(callTimeout = Duration.ofMillis(200)).apply {
        tokenRefresher = tokenRefresherMock
    }
    val iamUser = IamUser(server.url("").toString(), httpClient)

    @AfterAll
    fun afterAll() {
        server.shutdown()
    }

    @Nested
    inner class SearchUser {
        private val id = UUID.randomUUID()
        private val loginId = "johndoe"
        private val managingOrganization: UUID = UUID.randomUUID()
        private val validHsdpResponse = """{
            "total": 1,
            "entry": [
                {
                    "preferredLanguage": "en-US",
                    "preferredCommunicationChannel": "SMS",
                    "emailAddress": "john.doe@example.com",
                    "phoneNumber": "06-12345678",
                    "id": "$id",
                    "loginId": "$loginId",
                    "name": {
                        "family": "family name",
                        "given": "given names"
                     },
                    "managingOrganization": "$managingOrganization",
                    "passwordStatus": {
                        "passwordChangedOn": "2021-10-01T12:13:14Z",
                        "passwordExpiresOn": "2021-12-01T12:13:14Z"
                    },
                    "memberships": [
                        {
                            "organizationId": "organization id",
                            "organizationName": "organization name",
                            "roles": ["role1", "role2"],
                            "groups": ["group1", "group2"]
                        }
                    ],
                    "accountStatus": {
                        "lastLoginTime": "2021-10-03T12:13:14Z",
                        "mfaStatus": "mfaStatus",
                        "phoneVerified": true,
                        "emailVerified": false,
                        "mustChangePassword": true,
                        "disabled": false,
                        "accountLockedOn": "2021-10-05T14:15:16Z",
                        "accountLockedUntil": "2021-11-05T14:15:16Z",
                        "numberOfInvalidAttempt": 3,
                        "lastInvalidAttemptedOn": "2021-10-02T13:14:15Z"
                    },
                    "consentedApps": ["foo", "bar"],
                    "delegations": {
                        "granted": [
                            {
                                "delegateeId": "delegatee1",
                                "validFrom": "2021-10-05T14:15:16Z",
                                "validUntil": "2021-10-05T14:15:16Z"
                            }
                        ],
                        "received": [
                            {
                                "delegatorId": "delegator2",
                                "validFrom": "2021-10-05T14:15:16Z",
                                "validUntil": "2021-10-05T14:15:16Z"
                            }
                        ]
                    }
                }
            ]
        }"""
        private val expectedSearchResult = listOf(
            User(
                preferredLanguage = "en-US",
                preferredCommunicationChannel = "SMS",
                emailAddress = "john.doe@example.com",
                phoneNumber = "06-12345678",
                id = id,
                loginId = loginId,
                name = UserName(family = "family name", given = "given names"),
                managingOrganization = managingOrganization,
                passwordStatus = PasswordStatus(
                    passwordChangedOn = "2021-10-01T12:13:14Z",
                    passwordExpiresOn = "2021-12-01T12:13:14Z",
                ),
                memberships = listOf(
                    Membership(
                        organizationId = "organization id",
                        organizationName = "organization name",
                        roles = listOf("role1", "role2"),
                        groups = listOf("group1", "group2"),
                    )
                ),
                accountStatus = AccountStatus(
                    lastLoginTime = "2021-10-03T12:13:14Z",
                    mfaStatus = "mfaStatus",
                    phoneVerified = true,
                    emailVerified = false,
                    mustChangePassword = true,
                    disabled = false,
                    accountLockedOn = "2021-10-05T14:15:16Z",
                    accountLockedUntil = "2021-11-05T14:15:16Z",
                    numberOfInvalidAttempt = 3,
                    lastInvalidAttemptedOn = "2021-10-02T13:14:15Z",
                ),
                consentedApps = listOf("foo", "bar"),
                delegations = Delegations(
                    granted = listOf(
                        GrantedDelegation(
                            delegateeId = "delegatee1",
                            validFrom = "2021-10-05T14:15:16Z",
                            validUntil = "2021-10-05T14:15:16Z"
                        )
                    ),
                    received = listOf(
                        ReceivedDelegation(
                            delegatorId = "delegator2",
                            validFrom = "2021-10-05T14:15:16Z",
                            validUntil = "2021-10-05T14:15:16Z"
                        )
                    ),
                )
            )
        )

        @Test
        fun `Should return a valid user when the one can be found`(): Unit = runBlocking {
            // Given
            val mockedResponse = MockResponse()
                .setResponseCode(200)
                .setBody(validHsdpResponse)

            server.enqueue(mockedResponse)

            // When
            val result = iamUser.searchUser(loginId)
            val request = server.takeRequest()

            // Then
            request.requestUrl?.encodedPath shouldBe "/authorize/identity/User"
            request.requestUrl?.encodedQuery shouldBe "userId=$loginId&profileType=all"
            request.method shouldBe "GET"
            request.headers.toMultimap() shouldContainAll mapOf(
                "authorization" to listOf("Bearer ${httpClient.token.accessToken}"),
                "Api-Version" to listOf("2"),
                "accept" to listOf("application/json; charset=utf-8"),
            )
            result shouldBe expectedSearchResult
        }

        @Test
        fun `Should throw an HttpException when the returned status is not 2xx`(): Unit = runBlocking {
            val mockedResponse = MockResponse()
                .setResponseCode(404)
                .setBody(validHsdpResponse)

            server.enqueue(mockedResponse)

            // When/Then
            val exception = shouldThrow<HttpException> {
                iamUser.searchUser(loginId)
            }
            server.takeRequest()

            exception.message shouldBe validHsdpResponse
        }

        @Test
        fun `Should throw a SerializationException when the returned JSON is invalid`(): Unit = runBlocking {
            val mockedResponse = MockResponse()
                .setResponseCode(200)
                .setBody("""{"invalid json"}""")

            server.enqueue(mockedResponse)

            // When/Then
            val exception = shouldThrow<SerializationException> {
                iamUser.searchUser(loginId)
            }
            server.takeRequest()

            exception.message shouldStartWith "Unexpected JSON token at offset 14: Expected semicolon"
        }

        @Test
        fun `Should throw a InterruptedIOException when the server does not respond`(): Unit = runBlocking {
            val mockedResponse = MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)

            server.enqueue(mockedResponse)

            // When/Then
            val result = shouldThrow<InterruptedIOException> {
                iamUser.searchUser(loginId)
            }
            server.takeRequest()

            result.message shouldBe "timeout"
        }
    }
}