// Copyright (c) 2018, Yubico AB
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this
//    list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.yubico.webauthn.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yubico.internal.util.CollectionUtil;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;


/**
 * Parameters for a call to <code>navigator.credentials.create()</code>.
 *
 * @see <a href="https://www.w3.org/TR/2019/PR-webauthn-20190117/#dictdef-publickeycredentialcreationoptions">§5.4.
 * Options for Credential Creation (dictionary PublicKeyCredentialCreationOptions)</a>
 */
@Value
@Builder(toBuilder = true)
public class PublicKeyCredentialCreationOptions {

    /**
     * Contains data about the Relying Party responsible for the request.
     * <p>
     * Its value's {@link RelyingPartyIdentity#getId() id} member specifies the <a href="https://www.w3.org/TR/2019/PR-webauthn-20190117/#rp-id">RP
     * ID</a> the credential should be scoped to. If omitted, its value will be set by the client. See {@link
     * RelyingPartyIdentity} for further details.
     * </p>
     */
    @NonNull
    private final RelyingPartyIdentity rp;

    /**
     * Contains data about the user account for which the Relying Party is requesting attestation.
     */
    @NonNull
    private final UserIdentity user;

    /**
     * A challenge intended to be used for generating the newly created credential’s attestation object. See the <a
     * href="https://www.w3.org/TR/2019/PR-webauthn-20190117/#cryptographic-challenges">§13.1 Cryptographic
     * Challenges</a> security consideration.
     */
    @NonNull
    private final ByteArray challenge;

    /**
     * Information about the desired properties of the credential to be created.
     * <p>
     * The sequence is ordered from most preferred to least preferred. The client makes a best-effort to create the most
     * preferred credential that it can.
     * </p>
     */
    @NonNull
    private final List<PublicKeyCredentialParameters> pubKeyCredParams;

    /**
     * A time, in milliseconds, that the caller is willing to wait for the call to complete. This is treated as a hint,
     * and MAY be overridden by the client.
     */
    private final Long timeout;

    /**
     * Intended for use by Relying Parties that wish to limit the creation of multiple credentials for the same account
     * on a single authenticator. The client is requested to return an error if the new credential would be created on
     * an authenticator that also contains one of the credentials enumerated in this parameter.
     */
    private final Set<PublicKeyCredentialDescriptor> excludeCredentials;

    /**
     * Intended for use by Relying Parties that wish to select the appropriate authenticators to participate in the
     * create() operation.
     */
    private final AuthenticatorSelectionCriteria authenticatorSelection;

    /**
     * Intended for use by Relying Parties that wish to express their preference for attestation conveyance. The default
     * is {@link AttestationConveyancePreference#NONE}.
     */
    @NonNull
    private final AttestationConveyancePreference attestation;

    /**
     * Additional parameters requesting additional processing by the client and authenticator.
     * <p>
     * For example, the caller may request that only authenticators with certain capabilities be used to create the
     * credential, or that particular information be returned in the attestation object. Some extensions are defined in
     * <a href="https://www.w3.org/TR/2019/PR-webauthn-20190117/#extensions">§9 WebAuthn Extensions</a>; consult the
     * IANA "WebAuthn Extension Identifier" registry established by
     * <a href="https://tools.ietf.org/html/draft-hodges-webauthn-registries">[WebAuthn-Registries]</a> for an
     * up-to-date list of registered WebAuthn Extensions.
     * </p>
     */
    @NonNull
    private final RegistrationExtensionInputs extensions;

    @JsonCreator
    private PublicKeyCredentialCreationOptions(
        @NonNull @JsonProperty("rp") RelyingPartyIdentity rp,
        @NonNull @JsonProperty("user") UserIdentity user,
        @NonNull @JsonProperty("challenge") ByteArray challenge,
        @NonNull @JsonProperty("pubKeyCredParams") List<PublicKeyCredentialParameters> pubKeyCredParams,
        @JsonProperty("timeout") Long timeout,
        @JsonProperty("excludeCredentials") Set<PublicKeyCredentialDescriptor> excludeCredentials,
        @JsonProperty("authenticatorSelection") AuthenticatorSelectionCriteria authenticatorSelection,
        @JsonProperty("attestation") AttestationConveyancePreference attestation,
        @JsonProperty("extensions") RegistrationExtensionInputs extensions
    ) {
        this.rp = rp;
        this.user = user;
        this.challenge = challenge;
        this.pubKeyCredParams = CollectionUtil.immutableList(pubKeyCredParams);
        this.timeout = timeout;
        this.excludeCredentials = excludeCredentials == null ? null : CollectionUtil.immutableSortedSet(new TreeSet<>(excludeCredentials));
        this.authenticatorSelection = authenticatorSelection;
        this.attestation = attestation == null ? AttestationConveyancePreference.NONE : attestation;
        this.extensions = extensions == null ? RegistrationExtensionInputs.builder().build() : extensions;
    }

    public Optional<Long> getTimeout() {
        return Optional.ofNullable(timeout);
    }

    public Optional<Set<PublicKeyCredentialDescriptor>> getExcludeCredentials() {
        return Optional.ofNullable(excludeCredentials);
    }

    public Optional<AuthenticatorSelectionCriteria> getAuthenticatorSelection() {
        return Optional.ofNullable(authenticatorSelection);
    }

    public static PublicKeyCredentialCreationOptionsBuilder.MandatoryStages builder() {
        return new PublicKeyCredentialCreationOptionsBuilder.MandatoryStages();
    }

    public static class PublicKeyCredentialCreationOptionsBuilder {
        private Long timeout = null;
        private Set<PublicKeyCredentialDescriptor> excludeCredentials = null;
        private AuthenticatorSelectionCriteria authenticatorSelection = null;

        public static class MandatoryStages {
            private PublicKeyCredentialCreationOptionsBuilder builder = new PublicKeyCredentialCreationOptionsBuilder();

            /**
             * @see PublicKeyCredentialCreationOptions#getRp()
             */
            public Step2 rp(RelyingPartyIdentity rp) {
                builder.rp(rp);
                return new Step2();
            }

            /**
             * @see PublicKeyCredentialCreationOptions#getUser()
             */
            public class Step2 {
                public Step3 user(UserIdentity user) {
                    builder.user(user);
                    return new Step3();
                }
            }

            /**
             * @see PublicKeyCredentialCreationOptions#getChallenge()
             */
            public class Step3 {
                public Step4 challenge(ByteArray challenge) {
                    builder.challenge(challenge);
                    return new Step4();
                }
            }

            /**
             * @see PublicKeyCredentialCreationOptions#getPubKeyCredParams()
             */
            public class Step4 {
                public PublicKeyCredentialCreationOptionsBuilder pubKeyCredParams(List<PublicKeyCredentialParameters> pubKeyCredParams) {
                    return builder.pubKeyCredParams(pubKeyCredParams);
                }
            }
        }

        /**
         * A time, in milliseconds, that the caller is willing to wait for the call to complete. This is treated as a hint,
         * and MAY be overridden by the client.
         */
        public PublicKeyCredentialCreationOptionsBuilder timeout(@NonNull Optional<Long> timeout) {
            this.timeout = timeout.orElse(null);
            return this;
        }

        /*
         * Workaround, see: https://github.com/rzwitserloot/lombok/issues/2623#issuecomment-714816001
         * Consider reverting this workaround if Lombok fixes that issue.
         */
        private PublicKeyCredentialCreationOptionsBuilder timeout(Long timeout) {
            return this.timeout(Optional.ofNullable(timeout));
        }

        /**
         * A time, in milliseconds, that the caller is willing to wait for the call to complete. This is treated as a hint,
         * and MAY be overridden by the client.
         */
        public PublicKeyCredentialCreationOptionsBuilder timeout(long timeout) {
            return this.timeout(Optional.of(timeout));
        }

        /**
         * Intended for use by Relying Parties that wish to limit the creation of multiple credentials for the same account
         * on a single authenticator. The client is requested to return an error if the new credential would be created on
         * an authenticator that also contains one of the credentials enumerated in this parameter.
         */
        public PublicKeyCredentialCreationOptionsBuilder excludeCredentials(Optional<Set<PublicKeyCredentialDescriptor>> excludeCredentials) {
            return this.excludeCredentials(excludeCredentials.orElse(null));
        }

        /**
         * Intended for use by Relying Parties that wish to limit the creation of multiple credentials for the same account
         * on a single authenticator. The client is requested to return an error if the new credential would be created on
         * an authenticator that also contains one of the credentials enumerated in this parameter.
         */
        public PublicKeyCredentialCreationOptionsBuilder excludeCredentials(Set<PublicKeyCredentialDescriptor> excludeCredentials) {
            this.excludeCredentials = excludeCredentials;
            return this;
        }

        /**
         * Intended for use by Relying Parties that wish to select the appropriate authenticators to participate in the
         * create() operation.
         */
        public PublicKeyCredentialCreationOptionsBuilder authenticatorSelection(@NonNull Optional<AuthenticatorSelectionCriteria> authenticatorSelection) {
            return this.authenticatorSelection(authenticatorSelection.orElse(null));
        }

        /**
         * Intended for use by Relying Parties that wish to select the appropriate authenticators to participate in the
         * create() operation.
         */
        public PublicKeyCredentialCreationOptionsBuilder authenticatorSelection(AuthenticatorSelectionCriteria authenticatorSelection) {
            this.authenticatorSelection = authenticatorSelection;
            return this;
        }
    }

}