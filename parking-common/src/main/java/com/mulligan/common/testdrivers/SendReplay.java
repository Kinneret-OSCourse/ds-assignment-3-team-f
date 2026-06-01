package com.mulligan.common.testdrivers;

import com.mulligan.common.security.MessageSigner;
import com.mulligan.common.security.SecureMessage;

public final class SendReplay {

    private SendReplay() {
    }

    public static void main(String[] args) throws Exception {
        MessageSigner signer = new MessageSigner();
        String envelope = SecureMessage.wrap("{\"probe\":\"replay\"}", signer).toJson();
        AttackPublisherSupport.publishTransaction(envelope);
        AttackPublisherSupport.publishTransaction(envelope);
    }
}
