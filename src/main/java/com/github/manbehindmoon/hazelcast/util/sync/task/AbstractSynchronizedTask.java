package com.github.manbehindmoon.hazelcast.util.sync.task;

import static java.lang.String.format;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ILock;

/**
 * This abstract implementation of the interface {@link SynchronizedTask} implements
 * the synchronized task with Hazelcast over all nodes within the same cluster/key
 */
public abstract class AbstractSynchronizedTask implements SynchronizedTask {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractSynchronizedTask.class);

	private final HazelcastInstance hzInstance;

	private final String logPrefix;

	private final String key;

	private final Long minLeaseTimeInMillis;

	private final long leaseTime;

	private final TimeUnit leaseUnit;

	/**
	 * Constructor
	 *
	 * @param hzInstance           {@link HazelcastInstance}
	 * @param key                  this id must be unique for each implementation
	 * @param minLeaseTimeInMillis a minimum lease time can be applied for a lock
	 * @param maxLeaseTime         this is the max lease time a thread can be locked
	 * @param maxLeaseTimeUnit     the time unit for the max lease time
	 */
	public AbstractSynchronizedTask(final HazelcastInstance hzInstance, final String key, final long minLeaseTimeInMillis, final long maxLeaseTime, final TimeUnit maxLeaseTimeUnit) {

		Assert.notNull(hzInstance, "hazelcast instance is not allowed to be null.");
		Assert.notNull(key, "key is not allowed to be null.");
		Assert.isTrue(maxLeaseTime > 0, "maxLeaseTime must be greater than zero");
		Assert.notNull(maxLeaseTimeUnit, "maxLeaseTimeUnit is not allowed to be null.");
		Assert.isTrue(minLeaseTimeInMillis > 0, "minLeaseTime must be null or greater than zero");
		Assert.isTrue(minLeaseTimeInMillis < maxLeaseTimeUnit.toMillis(maxLeaseTime), "minLeaseTime must be smaller than maxLeaseTime.");

		this.hzInstance = hzInstance;
		this.key = key;
		this.minLeaseTimeInMillis = minLeaseTimeInMillis;
		this.leaseTime = maxLeaseTime;
		this.leaseUnit = maxLeaseTimeUnit;
		this.logPrefix = format("[sync task key: %s] ", this.key);
	}

	@Override
	public final void runSynchronizedTask() {

		final ILock lock = hzInstance.getLock(key);

		if (lock.isLocked()) {
			logInfo("The lock is already locked by another thread.");
			if (isWaitingActive()) {
				logInfo("Active waiting...");
				try {
					waiting(lock);
					if (lock.isLockedByCurrentThread()) {
						logInfo("The lock was acquired by the current thread after active waiting.");
					} else {
						logWarn("The lock could not have been acquired by the current thread after active waiting.");
						return;
					}
				} catch (Exception e) {
					logError("Unexpected error while active waiting.", e);
					return;
				}
			} else {
				logInfo("The lock was not acquired by the current thread. No active waiting.");
				return;
			}
		} else {
			logInfo("The lock was directly acquired by the current thread.");
			lock.lock(leaseTime, leaseUnit);
		}

		final long startTime = System.currentTimeMillis();

		try {

			logInfo("Call task()");
			task();
			logInfo("Finished task()");

			// calculate if the minimum lease time has elapsed 
			final long runTime = System.currentTimeMillis() - startTime;
			if (runTime < minLeaseTimeInMillis) {
				logInfo(format("The task's runtime was %d ms and therefore minimum lease time [%d ms] is still active.", runTime, minLeaseTimeInMillis));
				Thread.sleep(minLeaseTimeInMillis - runTime);
			}

		} catch (Exception e) {
			logError("Unexpected error while executing task.", e);
		} finally {
			logInfo("Try unlock.");
			if (lock.isLockedByCurrentThread()) {
				lock.unlock();
				logInfo("Unlock successful.");
			} else {
				// this might be an indication that the min and max lease time in combination
				// with the task (run time) and schedule time is not optimized
				logWarn("The lock is not locked anymore by the current thread.");
			}
		}

	}

	/**
	 * If a thread is locked and this method returns {@code false} then the synchronized run
	 * method will be aborted. This is the default behavior.
	 *
	 * This method must be manually overwritten for each implementation and the
	 * {@link #waiting(ILock)} must be implemented as well.
	 *
	 * @return boolean (default {@code false})
	 */
	protected boolean isWaitingActive() {

		return false;
	}

	/**
	 * If the {@link #isWaitingActive()} returns {@code true} then the responsibility of the
	 * lock lies within this method implementation. This method must acquire the lock or
	 * throw a meaningful exception.
	 *
	 * Default implementation throws a {@link IllegalArgumentException} exception.
	 *
	 * @param lock {@link ILock}
	 * @throws Exception with a meaningful message as it will be logged.
	 */
	protected void waiting(final ILock lock) throws Exception {
		throw new IllegalArgumentException("Default waiting method implementation. Please overwrite.");
	}

	/**
	 * This method is being called by the {@link #runSynchronizedTask()} when a lock
	 * could have been acquired. This method contains the task workload implementation.
	 */
	public abstract void task();

	public final String getKey() {

		return key;
	}

	public final Long getMinLeaseTimeInMillis() {

		return minLeaseTimeInMillis;
	}

	public final long getLeaseTime() {

		return leaseTime;
	}

	public final TimeUnit getLeaseUnit() {

		return leaseUnit;
	}

	public final void logInfo(String msg) {

		LOG.info(logPrefix + msg);
	}

	public final  void logWarn(String msg) {

		LOG.warn(logPrefix + msg);
	}

	public final  void logError(String msg, Throwable e) {
		LOG.error(logPrefix + msg, e);
	}

	private static class Assert {

		private static void notNull(Object object, String message) {

			if (object == null)
				throw new IllegalArgumentException(message);
		}

		private static void isTrue(boolean expression, String message) {

			if (!(expression))
				throw new IllegalArgumentException(message);
		}
	}

}
