package com.my.base.features.ratelimit.manager;

import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.Collections;


@Component
public class TokenBucketManager {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "rate:token:";

    private static final String LUA_CREATE_BUCKET =
            "local key=KEYS[1] \n" +
            "local t = redis.call('TIME') \n" +
            "local now = t[1]*1000 + math.floor(t[2]/1000) \n" +
            "if redis.call('EXISTS', key) == 0 then \n" +
            "  redis.call('HSET', key, 'capacity', ARGV[1], 'refillRate', ARGV[2], 'tokens', ARGV[1], 'lastRefillTime', now) \n" +
            "  return 1 \n" +
            "else \n" +
            "  return 0 \n" +
            "end";

    private static final String LUA_TRY_ACQUIRE =
            "local key=KEYS[1] \n" +
            "local permits=tonumber(ARGV[1]) \n" +
            "local t = redis.call('TIME') \n" +
            "local now = t[1]*1000 + math.floor(t[2]/1000) \n" +
            "if redis.call('EXISTS', key) == 0 then \n" +
            "  return 1 \n" +
            "end \n" +
            "local capacity=tonumber(redis.call('HGET', key, 'capacity')) \n" +
            "local refillRate=tonumber(redis.call('HGET', key, 'refillRate')) \n" +
            "local tokens=tonumber(redis.call('HGET', key, 'tokens')) \n" +
            "local lastRefillTime=tonumber(redis.call('HGET', key, 'lastRefillTime')) \n" +
            "if not capacity or not refillRate or not tokens or not lastRefillTime then \n" +
            "  return 1 \n" +
            "end \n" +
            "local elapsed=(now - lastRefillTime)/1000.0 \n" +
            "if elapsed > 0 then \n" +
            "  local tokensToAdd = elapsed * refillRate \n" +
            "  tokens = math.min(capacity, tokens + tokensToAdd) \n" +
            "  lastRefillTime = now \n" +
            "end \n" +
            "redis.call('HSET', key, 'tokens', tokens, 'lastRefillTime', lastRefillTime) \n" +
            "if tokens >= permits then \n" +
            "  return 0 \n" +
            "else \n" +
            "  return 1 \n" +
            "end";

    private static final String LUA_DEDUCTION_TOKEN =
            "local key=KEYS[1] \n" +
            "local permits=tonumber(ARGV[1]) \n" +
            "if redis.call('EXISTS', key) == 0 then return 0 end \n" +
            "local tokens=tonumber(redis.call('HGET', key, 'tokens')) \n" +
            "if not tokens then return 0 end \n" +
            "if tokens >= permits then \n" +
            "  tokens = tokens - permits \n" +
            "  redis.call('HSET', key, 'tokens', tokens) \n" +
            "  return 1 \n" +
            "else \n" +
            "  return 0 \n" +
            "end";

    public TokenBucketManager(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void createTokenBucket(String key, long capacity, double refillRate) {
        String bucketKey = KEY_PREFIX + key;
        RedisScript<Long> script = new DefaultRedisScript<>(LUA_CREATE_BUCKET, Long.class);
        stringRedisTemplate.execute(script, Collections.singletonList(bucketKey), String.valueOf(capacity), String.valueOf(refillRate));
    }

    public void removeTokenBucket(String key) {
        String bucketKey = KEY_PREFIX + key;
        stringRedisTemplate.delete(bucketKey);
    }

    public boolean tryAcquire(String key, int permits) {
        String bucketKey = KEY_PREFIX + key;
        RedisScript<Long> script = new DefaultRedisScript<>(LUA_TRY_ACQUIRE, Long.class);
        Long res = stringRedisTemplate.execute(script, Collections.singletonList(bucketKey), String.valueOf(permits));
        return res != null && res == 1;
    }

    public void deductionToken(String key, int permits) {
        String bucketKey = KEY_PREFIX + key;
        RedisScript<Long> script = new DefaultRedisScript<>(LUA_DEDUCTION_TOKEN, Long.class);
        stringRedisTemplate.execute(script, Collections.singletonList(bucketKey), String.valueOf(permits));
    }
}
