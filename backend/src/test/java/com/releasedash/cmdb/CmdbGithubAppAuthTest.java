package com.releasedash.cmdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CmdbGithubAppAuthTest {

    @Test
    void appJwtIsRs256AndIssuedByAppId() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String pem = pkcs8Pem(pair.getPrivate().getEncoded());
        CmdbGithubAppAuth auth = new CmdbGithubAppAuth(new ObjectMapper());

        String compact = auth.createAppJwt("123456", pem);
        SignedJWT jwt = SignedJWT.parse(compact);

        assertEquals(JWSObject.State.SIGNED, jwt.getState());
        assertEquals("123456", jwt.getJWTClaimsSet().getIssuer());
        assertTrue(jwt.verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic())));
    }

    @Test
    void pkcs1PemParses() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        byte[] pkcs8 = pair.getPrivate().getEncoded();
        byte[] pkcs1 = unwrapPkcs8Octet(pkcs8);
        String pem = "-----BEGIN RSA PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(pkcs1)
            + "\n-----END RSA PRIVATE KEY-----\n";

        CmdbGithubAppAuth.parsePrivateKey(pem);
        CmdbGithubAppAuth.parsePrivateKey(pem.replace("\n", "\\n"));
    }

    @Test
    void bearerFallsBackToPatWhenAppUnset() {
        CmdbProperties.Github github = new CmdbProperties.Github();
        github.setToken("github_pat_example");
        String token = new CmdbGithubAppAuth(new ObjectMapper()).bearerToken(github);
        assertEquals("github_pat_example", token);
    }

    private static String pkcs8Pem(byte[] der) {
        return "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(der)
            + "\n-----END PRIVATE KEY-----\n";
    }

    /** PKCS#8 PrivateKeyInfo → inner OCTET STRING (PKCS#1). */
    private static byte[] unwrapPkcs8Octet(byte[] pkcs8) {
        int i = 0;
        if (pkcs8[i++] != 0x30) {
            throw new IllegalArgumentException("not a sequence");
        }
        i += lengthSize(pkcs8[i]);
        i += 1;
        while (i < pkcs8.length) {
            int tag = pkcs8[i] & 0xff;
            i++;
            int len = pkcs8[i] & 0xff;
            i++;
            if (len > 127) {
                int count = len & 0x7f;
                len = 0;
                for (int n = 0; n < count; n++) {
                    len = (len << 8) | (pkcs8[i++] & 0xff);
                }
            }
            if (tag == 0x04) {
                byte[] out = new byte[len];
                System.arraycopy(pkcs8, i, out, 0, len);
                return out;
            }
            i += len;
        }
        throw new IllegalArgumentException("no octet string");
    }

    private static int lengthSize(byte first) {
        int b = first & 0xff;
        return b < 128 ? 1 : 1 + (b & 0x7f);
    }
}
