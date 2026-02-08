package com.hmdp.utils;

public interface ILock {
    /**
     * try to get lock
     * @param timeoutSec the expiration time of lock, it expired, it will release
     * @return true -> get lock successfully, false -> get lock unsuccessfully
     */
    boolean tryLock(long timeoutSec);

    void unlock();
}
