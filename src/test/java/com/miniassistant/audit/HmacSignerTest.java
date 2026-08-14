package com.miniassistant.audit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class HmacSignerTest {

    @Test
    public void sameInputAndKeyProduceSameSignature() {
        HmacSigner signer = new HmacSigner("secret-key");

        String first = signer.sign("hello world");
        String second = signer.sign("hello world");

        assertEquals(first, second);
    }

    @Test
    public void differentKeysProduceDifferentSignaturesForSameInput() {
        HmacSigner signerA = new HmacSigner("key-a");
        HmacSigner signerB = new HmacSigner("key-b");

        assertNotEquals(signerA.sign("hello world"), signerB.sign("hello world"));
    }

    @Test
    public void differentInputProducesDifferentSignatureForSameKey() {
        HmacSigner signer = new HmacSigner("secret-key");

        assertNotEquals(signer.sign("hello"), signer.sign("world"));
    }
}
