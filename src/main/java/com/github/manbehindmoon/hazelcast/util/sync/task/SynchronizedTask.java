package com.github.manbehindmoon.hazelcast.util.sync.task;

/**
 * This interface defines the synchronized task with Hazelcast over all nodes within the same cluster/key
 */
public interface SynchronizedTask {

    /**
     * This method starts the synchronized run. For detailed implementation see {@link AbstractSynchronizedTask#runSynchronizedTask())
     */
    void runSynchronizedTask();

}
