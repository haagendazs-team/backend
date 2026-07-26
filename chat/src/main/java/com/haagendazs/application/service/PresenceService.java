package com.haagendazs.application.service;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PresenceService {
    private static final String ONLINE_USER_KEY = "online_user";
    private final StringRedisTemplate stringRedisTemplate;

    public void setOnline(Long memberId){
        stringRedisTemplate.opsForSet().add(ONLINE_USER_KEY, String.valueOf(memberId));
    }
    public void setOffline(Long memberId){
        stringRedisTemplate.opsForSet().remove(ONLINE_USER_KEY, String.valueOf(memberId));
    }
    public boolean isOnline(Long memberId){
        Boolean result = stringRedisTemplate.opsForSet().isMember(ONLINE_USER_KEY, String.valueOf(memberId));
        return Boolean.TRUE.equals(result);
    }

}
