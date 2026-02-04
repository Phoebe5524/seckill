package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


@Component
public class RedisIdWorker {
    // 生成redis唯一id（用于优惠券秒杀业务）

    // 开始时间戳
    private static final long BEGIN_TIMESTAMP = 1767225600L;

    // 序列号的位数
    private static final int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1获取当天日期，精确到天作为key（避免序列号超过32位上限，并且方便做统计）
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // redis自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3.拼接并返回（不能直接拼接因为拼接就变成string格式了，需要保留long格式）
        return timeStamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("Second: " + second);
    }

}
