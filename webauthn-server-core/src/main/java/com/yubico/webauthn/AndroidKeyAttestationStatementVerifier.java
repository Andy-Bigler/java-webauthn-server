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

package com.yubico.webauthn;

import COSE.CoseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.upokecenter.cbor.CBORObject;
import com.yubico.internal.util.ExceptionUtil;
import com.yubico.webauthn.data.AttestationObject;
import com.yubico.webauthn.data.AttestationType;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.COSEAlgorithmIdentifier;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

// ToDo make sure https://www.w3.org/TR/2021/REC-webauthn-2-20210408/#sctn-android-key-attestation is implemented correctly
// Currently it's just copied from packed attestation statement verifier
@Slf4j
final class AndroidKeyAttestationStatementVerifier
    implements AttestationStatementVerifier, X5cAttestationStatementVerifier {

  @Override
  public AttestationType getAttestationType(AttestationObject attestation) {
    if (attestation.getAttestationStatement().hasNonNull("x5c")) {
      return AttestationType.BASIC;
    } else {
      return AttestationType.SELF_ATTESTATION;
    }
  }

  @Override
  public boolean verifyAttestationSignature(
      AttestationObject attestationObject, ByteArray clientDataJsonHash) {
    val signatureNode = attestationObject.getAttestationStatement().get("sig");

    if (signatureNode == null || !signatureNode.isBinary()) {
      throw new IllegalArgumentException("attStmt.sig must be set to a binary value.");
    }

    if (attestationObject.getAttestationStatement().has("x5c")) {
      return verifyX5cSignature(attestationObject, clientDataJsonHash);
    } else {
      return verifySelfAttestationSignature(attestationObject, clientDataJsonHash);
    }
  }

  private boolean verifySelfAttestationSignature(
      AttestationObject attestationObject, ByteArray clientDataJsonHash) {
    final PublicKey pubkey;
    try {
      pubkey =
          WebAuthnCodecs.importCosePublicKey(
              attestationObject
                  .getAuthenticatorData()
                  .getAttestedCredentialData()
                  .get()
                  .getCredentialPublicKey());
    } catch (IOException | CoseException | InvalidKeySpecException e) {
      throw ExceptionUtil.wrapAndLog(
          log,
          String.format(
              "Failed to parse public key from attestation data %s",
              attestationObject.getAuthenticatorData().getAttestedCredentialData()),
          e);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    final long keyAlgId =
        CBORObject.DecodeFromBytes(
                attestationObject
                    .getAuthenticatorData()
                    .getAttestedCredentialData()
                    .get()
                    .getCredentialPublicKey()
                    .getBytes())
            .get(CBORObject.FromObject(3))
            .AsNumber()
            .ToInt64IfExact();
    final COSEAlgorithmIdentifier keyAlg =
        COSEAlgorithmIdentifier.fromId(keyAlgId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unsupported COSE algorithm identifier: " + keyAlgId));

    final long sigAlgId = attestationObject.getAttestationStatement().get("alg").asLong();
    final COSEAlgorithmIdentifier sigAlg =
        COSEAlgorithmIdentifier.fromId(sigAlgId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unsupported COSE algorithm identifier: " + sigAlgId));

    if (!Objects.equals(keyAlg, sigAlg)) {
      throw new IllegalArgumentException(
          String.format(
              "Key algorithm and signature algorithm must be equal, was: Key: %s, Sig: %s",
              keyAlg, sigAlg));
    }

    ByteArray signedData =
        attestationObject.getAuthenticatorData().getBytes().concat(clientDataJsonHash);
    ByteArray signature;
    try {
      signature =
          new ByteArray(attestationObject.getAttestationStatement().get("sig").binaryValue());
    } catch (IOException e) {
      throw ExceptionUtil.wrapAndLog(log, ".binaryValue() of \"sig\" failed", e);
    }

    return Crypto.verifySignature(pubkey, signedData, signature, keyAlg);
  }

  private boolean verifyX5cSignature(
      AttestationObject attestationObject, ByteArray clientDataHash) {
    final Optional<X509Certificate> attestationCert;
    try {
      attestationCert = getX5cAttestationCertificate(attestationObject);
    } catch (CertificateException e) {
      throw ExceptionUtil.wrapAndLog(
          log,
          String.format(
              "Failed to parse X.509 certificate from attestation object: %s", attestationObject),
          e);
    }
    return attestationCert
        .map(
            attestationCertificate -> {
              JsonNode signatureNode = attestationObject.getAttestationStatement().get("sig");
              if (signatureNode == null) {
                throw new IllegalArgumentException(
                    "Packed attestation statement must have field \"sig\".");
              }

              if (signatureNode.isBinary()) {
                ByteArray signature;
                try {
                  signature = new ByteArray(signatureNode.binaryValue());
                } catch (IOException e) {
                  throw ExceptionUtil.wrapAndLog(
                      log,
                      "signatureNode.isBinary() was true but signatureNode.binaryValue() failed",
                      e);
                }

                JsonNode algNode = attestationObject.getAttestationStatement().get("alg");
                if (algNode == null) {
                  throw new IllegalArgumentException(
                      "Packed attestation statement must have field \"alg\".");
                }
                ExceptionUtil.assertTrue(
                    algNode.isIntegralNumber(),
                    "Field \"alg\" in packed attestation statement must be a COSEAlgorithmIdentifier.");
                final Long sigAlgId = algNode.asLong();
                final COSEAlgorithmIdentifier sigAlg =
                    COSEAlgorithmIdentifier.fromId(sigAlgId)
                        .orElseThrow(
                            () ->
                                new IllegalArgumentException(
                                    "Unsupported COSE algorithm identifier: " + sigAlgId));

                ByteArray signedData =
                    attestationObject.getAuthenticatorData().getBytes().concat(clientDataHash);

                final String signatureAlgorithmName = WebAuthnCodecs.getJavaAlgorithmName(sigAlg);
                Signature signatureVerifier;
                try {
                  signatureVerifier = Signature.getInstance(signatureAlgorithmName);
                } catch (NoSuchAlgorithmException e) {
                  throw ExceptionUtil.wrapAndLog(
                      log, "Failed to get a Signature instance for " + signatureAlgorithmName, e);
                }
                try {
                  signatureVerifier.initVerify(attestationCertificate.getPublicKey());
                } catch (InvalidKeyException e) {
                  throw ExceptionUtil.wrapAndLog(
                      log, "Attestation key is invalid: " + attestationCertificate, e);
                }
                try {
                  signatureVerifier.update(signedData.getBytes());
                } catch (SignatureException e) {
                  throw ExceptionUtil.wrapAndLog(
                      log, "Signature object in invalid state: " + signatureVerifier, e);
                }

                try {
                  return signatureVerifier.verify(signature.getBytes());
                } catch (SignatureException e) {
                  throw ExceptionUtil.wrapAndLog(
                      log, "Failed to verify signature: " + attestationObject, e);
                }
              } else {
                throw new IllegalArgumentException(
                    "Field \"sig\" in packed attestation statement must be a binary value.");
              }
            })
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "If \"x5c\" property is present in \"packed\" attestation format it must be an array containing at least one DER encoded X.509 cerficicate."));
  }
}
