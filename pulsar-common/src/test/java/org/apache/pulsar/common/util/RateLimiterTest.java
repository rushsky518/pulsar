/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.common.util;

import static org.testng.Assert.fail;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.testng.annotations.Test;

public class RateLimiterTest {

    @Test
    public void testInvalidRenewTime() {
        try {
            new RateLimiter(0, 100, TimeUnit.SECONDS);
            fail("should have thrown exception: invalid rate, must be > 0");
        } catch (IllegalArgumentException ie) {
            // Ok
        }

        try {
            new RateLimiter(10, 0, TimeUnit.SECONDS);
            fail("should have thrown exception: invalid rateTime, must be > 0");
        } catch (IllegalArgumentException ie) {
            // Ok
        }
    }

    @Test
    public void testclose() throws Exception {
        RateLimiter rate = new RateLimiter(1, 1000, TimeUnit.MILLISECONDS);
        assertFalse(rate.isClosed());
        rate.close();
        assertTrue(rate.isClosed());
        try {
            rate.acquire();
            fail("should have failed, executor is already closed");
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    @Test
    public void testAcquireBlock() throws Exception {
        final long rateTimeMSec = 1000;
        RateLimiter rate = new RateLimiter(1, rateTimeMSec, TimeUnit.MILLISECONDS);
        rate.acquire();
        assertEquals(rate.getAvailablePermits(), 0);
        long start = System.currentTimeMillis();
        rate.acquire();
        long end = System.currentTimeMillis();
        // no permits are available: need to wait on acquire
        assertTrue((end - start) > rateTimeMSec / 2);
        rate.close();
    }

    @Test
    public void testAcquire() throws Exception {
        // 间隔 1000ms 清空
        final long rateTimeMSec = 1000;
        // 100 个令牌
        final int permits = 100;
        RateLimiter rate = new RateLimiter(permits, rateTimeMSec, TimeUnit.MILLISECONDS);
        long start = System.currentTimeMillis();
        for (int i = 0; i < permits; i++) {
            // 获取令牌
            rate.acquire();
        }
        // 没到间隔时间 rateTimeMSec，所以已发出去的令牌还没有清除
        long end = System.currentTimeMillis();
        assertTrue((end - start) < rateTimeMSec);
        assertEquals(rate.getAvailablePermits(), 0);
        rate.close();
    }

    @Test
    public void testMultipleAcquire() throws Exception {
        // 每过 1000ms 重置令牌数
        final long rateTimeMSec = 1000;
        // 令牌总数为 100
        final int permits = 100;
        final int acquirePermist = 50;
        RateLimiter rate = new RateLimiter(permits, rateTimeMSec, TimeUnit.MILLISECONDS);
        long start = System.currentTimeMillis();
        for (int i = 0; i < permits / acquirePermist; i++) {
            // 1 次获取 50 个令牌，2 次申请完 100 个令牌
            rate.acquire(acquirePermist);
        }
        long end = System.currentTimeMillis();
        assertTrue((end - start) < rateTimeMSec);
        // 时间还不到 1000ms，令牌没有重置，则可用令牌仍为 0
        assertEquals(rate.getAvailablePermits(), 0);
        rate.close();
    }

    @Test
    public void testTryAcquireNoPermits() throws Exception {
        final long rateTimeMSec = 1000;
        RateLimiter rate = new RateLimiter(1, rateTimeMSec, TimeUnit.MILLISECONDS);
        // 只有 1 个令牌，第一次获取成功，第二次获取失败
        assertTrue(rate.tryAcquire());
        assertFalse(rate.tryAcquire());
        assertEquals(rate.getAvailablePermits(), 0);
        rate.close();
    }

    @Test
    public void testTryAcquire() throws Exception {
        final long rateTimeMSec = 1000;
        final int permits = 100;
        RateLimiter rate = new RateLimiter(permits, rateTimeMSec, TimeUnit.MILLISECONDS);
        for (int i = 0; i < permits; i++) {
            rate.tryAcquire();
        }
        assertEquals(rate.getAvailablePermits(), 0);
        rate.close();
    }

    @Test
    public void testMultipleTryAcquire() throws Exception {
        final long rateTimeMSec = 1000;
        final int permits = 100;
        final int acquirePermist = 50;
        RateLimiter rate = new RateLimiter(permits, rateTimeMSec, TimeUnit.MILLISECONDS);
        for (int i = 0; i < permits / acquirePermist; i++) {
            rate.tryAcquire(acquirePermist);
        }
        assertEquals(rate.getAvailablePermits(), 0);
        rate.close();
    }

    @Test
    public void testResetRate() throws Exception {
        final long rateTimeMSec = 1000;
        final int permits = 100;
        RateLimiter rate = new RateLimiter(permits, rateTimeMSec, TimeUnit.MILLISECONDS);
        // 共有 100 个令牌，获取 100 个，剩余令牌为 0
        rate.tryAcquire(permits);
        assertEquals(rate.getAvailablePermits(), 0);
        // check after a rate-time: permits must be renewed
        // 等待 2 倍 rateTimeMSec 时间后，令牌重置了
        Thread.sleep(rateTimeMSec * 2);
        assertEquals(rate.getAvailablePermits(), permits);

        // change rate-time from 1sec to 5sec
        rate.setRate(permits, 5 * rateTimeMSec, TimeUnit.MILLISECONDS, null);
        assertEquals(rate.getAvailablePermits(), 100);
        assertEquals(rate.tryAcquire(permits), true);
        assertEquals(rate.getAvailablePermits(), 0);
        // check after a rate-time: permits can't be renewed
        Thread.sleep(rateTimeMSec);
        assertEquals(rate.getAvailablePermits(), 0);

        rate.close();
    }

    @Test
    public void testRateLimiterWithPermitUpdater() throws Exception{
        long permits = 10;
        long rateTime = 1;
        long newUpdatedRateLimit = 100L;
        Supplier<Long> permitUpdater = () -> newUpdatedRateLimit;
        RateLimiter limiter = new RateLimiter(null, permits , 1, TimeUnit.SECONDS, permitUpdater);
        limiter.acquire();
        Thread.sleep(rateTime*3*1000);
        assertEquals(limiter.getAvailablePermits(), newUpdatedRateLimit);
    }
}
