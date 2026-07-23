package com.haagendazs.member.application.port;

import com.haagendazs.member.domain.model.Member;

public interface MemberEventPublisher {

    void publishCreated(Member member);

    void publishUpdated(Member member);

    void publishDeactivated(Member member);
}
