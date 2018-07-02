package com.github.manbehindmoon.hazelcast.util.sync.task;

import static java.lang.String.format;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ILock;

/**
 * This abstract implementation of interface {@link SynchronizedTask} implements
 * the synchronized task with Hazelcast over all nodes within the same
 * cluster/key
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
	public AbstractSynchronizedTask(final HazelcastInstance hzInstance, final String key, final long minLeaseTimeInMillis,
			final long maxLeaseTime, final TimeUnit maxLeaseTimeUnit) {

		Assert.notNull(hzInstance, "hazelcast instance is not allowed to be null.");
		Assert.notNull(key, "key is not allowed to be null.");
		Assert.isTrue(maxLeaseTime > 0, "maxLeaseTime must be greater than zero");
		Assert.notNull(maxLeaseTimeUnit, "maxLeaseTimeUnit is not allowed to be null.");
		Assert.isTrue(minLeaseTimeInMillis > 0, "minLeaseTime must be null or greater than zero");
		Assert.isTrue(minLeaseTimeInMillis < maxLeaseTimeUnit.toMillis(maxLeaseTime),
				"minLeaseTime must be smaller than maxLeaseTime.");

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
			logInfo("Thread is locked.");
			if (isWaitingActive()) {
				logInfo("Active waiting...");
				try {
					waiting(lock);
					if (lock.isLockedByCurrentThread()) {
						logInfo("Lock acquired after active waiting.");
					} else {
						logWarn("Lock is not acquired by current thread after active waiting.");
						return;
					}
				} catch (Exception e) {
					logError("Unexpected error while active waiting.", e);
					return;
				}
			} else {
				logInfo("Lock not acquired. No active waiting.");
				return;
			}
		} else {
			logInfo("Lock directly acquired.");
			lock.lock(leaseTime, leaseUnit);
		}

		final long startTime = System.currentTimeMillis();

		try {

			logInfo("Call task()");
			task();
			logInfo("Finished task()");

			// calculate min lease time
			final long runTime = System.currentTimeMillis() - startTime;
			if (runTime < minLeaseTimeInMillis) {
				logInfo(format("Runtime was '%d' and therefore minimum lease time [%d] is active.", runTime,
						minLeaseTimeInMillis));
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
				logWarn("Thread is not locked anymore by current thread.");
			}
		}

	}

	/**
	 * If a thread is locked and this method returns false then the synchronized run
	 * method will be aborted. This is the default behavior.
	 *
	 * This method must be manually overwritten for each implementation and the
	 * {@link #waiting(ILock)} must be implemented as well.
	 *
	 * @return boolean (default false)
	 */
	protected boolean isWaitingActive() {

		return false;
	}

	/**
	 * If the {@link #isWaitingActive()} returns true then the responsibility of the
	 * lock lies within the implementation. This method must acquire the lock or
	 * throw a meaningful exception.
	 *
	 * Default implementation throws a {@link IllegalArgumentException} exception.
	 *
	 * @param lock {@link ILock}
	 * @throws Exception with a meaningful message as it will be logged.
	 */
	protected void waiting(final ILock lock) throws Exception {
		throw new IllegalArgumentException("Default waiting method implementation");
	}

	/**
	 * This method is being called by the {@link #runSynchronizedTask()} when a lock
	 * could have been acquired. This method has the task workload implementation.
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

	private void logInfo(String msg) {

		LOG.info(logPrefix + msg);
	}

	private void logWarn(String msg) {

		LOG.warn(logPrefix + msg);
	}

	private void logError(String msg, Throwable e) {
		LOG.error(logPrefix + "Unexpected error.", e);
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
