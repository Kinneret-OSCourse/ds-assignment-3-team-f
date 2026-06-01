package com.mulligan.common.testdrivers;

import com.mulligan.common.security.MessageSigner;
import com.mulligan.common.security.SecureMessage;

import java.time.Instant;
import java.util.UUID;

public final class SendStale {

    private SendStale() {
    }

    public static void main(String[] args) throws Exception {
        MessageSigner signer = new MessageSigner();
        String nonce = UUID.randomUUID().toString();
        long timestamp = Instant.now().getEpochSecond() - (SecureMessage.MAX_AGE_SECONDS + 30);
        String payload = "{\"probe\":\"stale\"}";
        String hmac = signer.sign(SecureMessage.canonical(nonce, timestamp, payload));
        String envelope = "{\"nonce\":\"" + nonce + "\",\"timestamp\":" + timestamp
                + ",\"payload\":\"{\\\"probe\\\":\\\"stale\\\"}\",\"hmac\":\"" + hmac + "\"}";
        AttackPublisherSupport.publishTransaction(envelope);
    }
}
