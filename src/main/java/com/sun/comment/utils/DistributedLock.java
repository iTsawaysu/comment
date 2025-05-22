package com.sun.comment.utils;

public interface DistributedLock {
    /**
     * 获取锁（只有一个线程能够获取到锁）
     * @param timeout   锁的超时时间，过期后自动释放
     * @return          true 代表获取锁成功；false 代表获取锁失败
     */
    boolean tryLock(long timeout);

    /**
     * 释放锁
     */
    void unlock();
}
