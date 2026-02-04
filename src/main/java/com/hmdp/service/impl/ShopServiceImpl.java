package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class,  this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if(shop == null) {
            return Result.fail("店铺不存在！");
        }

        // 7. 返回
        return Result.ok(shop);
    }

    // 缓存击穿：互斥锁
//    public Shop queryWithMutex(Long id) {
//        // 1. 从redis中查询商铺缓存
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        // 2. 判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 把shopJson从字符串转成对象（反序列化）
//            // 3. 存在，直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        // 判断是否是空值（对应缓存穿透逻辑）
//        if(shopJson != null) {
//            return null;
//        }
//        // 4. 不存在，实现缓存重建
//        // 4.1 获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            // 4.2 判断是否获取成功
//            if (isLock) {
//                // 4.3 如果失败，则休眠并重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            // 4.4 成功，根据id查询数据库
//            shop = getById(id);
//            // 模拟重建的延时
//            Thread.sleep(200);
//            // 5. 不存在，返回错误
//            if (shop == null) {
//                // 解决缓存穿透，将空值写入redis
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//
//                return null;
//            }
//            // 6. 存在，写入redis，并添加超时剔除策略
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            // 7. 释放互斥锁
//            unLock(lockKey);
//        }
//
//        // 8. 返回
//        return shop;
//    }

    // 用线程池实现缓存重建
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    // 缓存击穿：逻辑过期
//    public Shop queryWithLogicalExpire(Long id) {
//        // 1. 从redis中查询商铺缓存
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        // 2. 判断缓存是否命中
//        if (StrUtil.isBlank(shopJson)) {
//            // 3. 未命中，返回空
//            return null;
//        }
//        // 如果命中，需要把json反序列化成对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 判断缓存是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 未过期，直接返回店铺信息
//            return shop;
//        }
//        // 已过期，需要缓存重建
//        // 获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        // 判断是否获取锁成功
//        if (isLock) {
//            // 成功，开启独立线程，实现缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                // 重建缓存
//                try {
//                    String latest = stringRedisTemplate.opsForValue().get(key);
//                    RedisData latestData = JSONUtil.toBean(latest, RedisData.class);
//                    if (latestData.getExpireTime().isAfter(LocalDateTime.now())) {
//                        return; // 已经被重建过
//                    }
//                    this.saveShop2Redis(id, 20L);
//
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    // 释放锁
//                    unLock(lockKey);
//                }
//            });
//        }
//        //成功与失败都返回逻辑时间过期的商铺信息
//        return shop;
//    }
//
//    public Shop queryWithPassThrough(Long id) {
//        // 1. 从redis中查询商铺缓存
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        // 2. 判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 把shopJson从字符串转成对象（反序列化）
//            // 3. 存在，直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        // 判断是否是空值（对应缓存穿透逻辑）
//        if(shopJson != null) {
//            return null;
//        }
//        // 4. 不存在，根据id查询数据库
//        Shop shop = getById(id);
//        // 5. 不存在，返回错误
//        if (shop == null) {
//            // 解决缓存穿透，将空值写入redis
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//
//            return null;
//        }
//        // 6. 存在，写入redis，并添加超时剔除策略
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        // 7. 返回
//        return shop;
//    }

    // 获取锁
//    private boolean tryLock(String key) {
//        // 设置互斥锁防止缓存击穿
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    // 释放锁
//    private void unLock(String key) {
//        stringRedisTemplate.delete(key);
//    }
//
//    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
//        // 1. 查询店铺数据
//        Shop shop = getById(id);
//        Thread.sleep(200);
//        // 2. 封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        // 3. 更新redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//
//    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 增加主动更新策略
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
