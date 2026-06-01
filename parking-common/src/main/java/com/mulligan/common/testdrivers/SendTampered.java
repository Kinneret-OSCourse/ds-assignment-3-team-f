package com.mulligan.common.testdrivers;

import com.mulligan.common.security.MessageSigner;
import com.mulligan.common.security.SecureMessage;

public final class SendTampered {

    private SendTampered() {
    }

    public static void main(String[] args) throws Exception {
        MessageSigner signer = new MessageSigner();
        String payload = "{\"probe\":\"tampered\",\"amount\":10}";
        String envelope = SecureMessage.wrap(payload, signer).toJson()
                .replace("\\\"amount\\\":10", "\\\"amount\\\":999");
        AttackPublisherSupport.publishTransaction(envelope);
    }
}
