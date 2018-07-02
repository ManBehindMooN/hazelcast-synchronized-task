package com.github.manbehindmoon.hazelcast.util.sync.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.manbehindmoon.hazelcast.util.sync.task.AbstractSynchronizedTask;
import com.github.manbehindmoon.hazelcast.util.sync.task.SynchronizedTask;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ILock;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;

public class ActiveWaitingSynchronizedTaskTest {

	private static final CountDownLatch latch = new CountDownLatch(2);

	private static TestHazelcastInstanceFactory hazelcastFactory = new TestHazelcastInstanceFactory(1);

    private SynchronizedTask synchronizedService1;
    
    private SynchronizedTask synchronizedService2;

    @Before
    public void setup() {

        HazelcastInstance node = hazelcastFactory.newHazelcastInstance();
        HazelcastTestSupport.warmUpPartitions(node);
        String uuid =  HazelcastTestSupport.generateKeyOwnedBy(node);
        synchronizedService1 = new ActiveWaitingSynchronizedService(node, uuid);
        synchronizedService2 = new ActiveWaitingSynchronizedService(node, uuid);
    }


	@After
	public void tearDown() {

		hazelcastFactory.shutdownAll();
	}
	
	 
    @Test
    public void testWaitWithTwoInstancesThread() throws Exception {

        assertEquals(2, latch.getCount());
        
        new Thread() {
        	
        	@Override
        	public void run() {
        		 synchronizedService1.runSynchronizedTask();
        	};
        	
        	
        }.start();
        
        Thread.sleep(500);
        
        synchronizedService2.runSynchronizedTask();
      
        assertTrue(latch.await(10, TimeUnit.SECONDS));

    }


	private static class ActiveWaitingSynchronizedService extends AbstractSynchronizedTask {

		public ActiveWaitingSynchronizedService(HazelcastInstance hzInstance, String key) {

			super(hzInstance, key, 2000L, 5L, TimeUnit.SECONDS);
		}

		@Override
		public void task() {

			assertTrue(latch.getCount() > 0);
			latch.countDown();
			System.out.println(String.format("[%s] %s: actual count '%d'", getClass().getSimpleName(), getKey(),
					latch.getCount()));
		}

		@Override
		protected boolean isWaitingActive() {

			return true;
		}

		@Override
		protected void waiting(ILock lock) throws Exception {

			try {
				if (!lock.tryLock(lock.getRemainingLeaseTime() + 1_000, TimeUnit.MILLISECONDS, getLeaseTime(),
						getLeaseUnit())) {
					throw new Exception("Could not acquire lock after 30 seconds.");
				}
			} catch (InterruptedException e) {
				throw new Exception("InterruptedException: Could not acquire lock after 30 seconds.");
			}

		}

	}

}
