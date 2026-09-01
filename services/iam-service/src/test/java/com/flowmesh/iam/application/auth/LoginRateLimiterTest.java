package com.flowmesh.iam.application.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.flowmesh.iam.config.LoginRateLimitProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 验证 Redis 登录尝试限流器的阈值和故障降级行为。
 */
@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private LoginRateLimiter limiter;

    /** 初始化 Redis 限流器模拟对象。 */
    @BeforeEach
    void setUp() {
        limiter = new LoginRateLimiter(
            redisTemplate,
            new LoginRateLimitProperties(true, 5, 30, Duration.ofMinutes(1))
        );
    }

    /** 验证账号或客户端达到阈值后拒绝登录。 */
    @Test
    void shouldRejectWhenAccountOrClientLimitIsReached() {
        doReturn(0L).when(redisTemplate).execute(
            any(RedisScript.class), anyList(), anyString(), anyString(), anyString()
        );

        assertThatThrownBy(() -> limiter.check(
            new LoginRateLimiter.LoginAttempt("tenant-a", "applicant-a", "127.0.0.1")
        )).isInstanceOf(LoginRateLimitExceededException.class);
    }

    /**
     * 验证 Redis 故障时限流器降级放行，不阻断认证链路。
     */
    @Test
    void shouldFailOpenWhenRedisIsUnavailable() {
        doThrow(new RedisConnectionFailureException("redis unavailable"))
            .when(redisTemplate).execute(
                any(RedisScript.class), anyList(), anyString(), anyString(), anyString()
            );

        assertThatCode(() -> limiter.check(
            new LoginRateLimiter.LoginAttempt("tenant-a", "applicant-a", "127.0.0.1")
        )).doesNotThrowAnyException();
    }
}
