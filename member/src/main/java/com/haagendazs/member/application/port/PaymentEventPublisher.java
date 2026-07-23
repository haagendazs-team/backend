package com.haagendazs.member.application.port;

public interface PaymentEventPublisher {

    void publishWorkspaceCreated(Long workspaceId);
}
