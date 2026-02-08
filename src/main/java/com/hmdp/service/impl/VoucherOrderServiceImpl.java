package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2. 判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        // 3. 判断秒杀是否已结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }

        // 4. 判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足

            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        // create lock object
        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);

        // get lock
        boolean isLock = lock.tryLock(1200);

        // decide wheather get the lock successfully
        if (!isLock) {
            // get lock failed, return fault or retry
            return Result.fail("不允许重复下单");
        }
        // get proxy object(transaction)
        try {
            // to avoid spring transaction failure
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // the transaction should take effect before release the lock
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // release lock
            lock.unlock();
        }
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // one order per person
        Long userId = UserHolder.getUser().getId();

        // every userId should be a unique value, toString() still will create a new object(no)
        // intern will make sure find a value from the string pool instead of creating a new object
        // 6.1 order inquiry
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 6.2 determine whether the order exists
        if (count > 0) {
            // user already bought one order
            return Result.fail("该用户已经购买过了");
        }

        // 5. 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? stock > 0
                .update();

        if (!success) {
            return Result.fail("库存不足");
        }

        // 6. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        long id = redisIdWorker.nextId("order");
        voucherOrder.setId(id);
        // 用户id
        voucherOrder.setUserId(userId);
        // 代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        // 7. 返回订单id
        return Result.ok(id);
    }
}
