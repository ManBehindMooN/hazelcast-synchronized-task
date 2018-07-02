This util helps to synchronize task over several nodes. It integrates [Hazelcast](https://hazelcast.com) for server clustering.

# Configuration

In order to guarantee the full functionality of the hz-util these following configurations must be provided
 
## Maven 
The hz-util can be injected via Maven.

    <dependency>
        <groupId>hz-util</groupId>
        <artifactId>synchronized-task</artifactId>
        <version>${project.version}</version>
    </dependency>
    


## Hazelcast configuration
It needs an valid (cluster) hazelcast configuration. There are several ways (Java, XML, Cluster/Client, only Cluster etc.) to create a Hazelcast instance. Make yourself familiar with the Hazelcast principles.

### Hazelcast XML configuration (only cluster)

    
    // Java code to parse a XML hz config file
    public HazelcastInstance hazelcastCluster(String hazelcastFile) throws Exception {
        try {
            ClassPathResource res = new ClassPathResource(hazelcastFile);
            XmlConfigBuilder configBuilder = new XmlConfigBuilder(res.getURL());
            return Hazelcast.newHazelcastInstance(configBuilder.build());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "No Hazelcast instance could not have been established", e);
            throw e;
        }
    }



    <!-- XML skeleton file -->
    <hazelcast xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
                xsi:schemaLocation="http://www.hazelcast.com/schema/config 
                                    http://www.hazelcast.com/schema/config/hazelcast-config-3.9.xsd" 
                xmlns="http://www.hazelcast.com/schema/config"> 
     
        <group>
            <name>{GROUP-NAME}</name>
            <password>{GROUP-PWD}</password>
        </group>
        <network> 
            <port>{GROUP-COMMUNICATION-PORT}</port>
            <join>
                <multicast enabled="false"/> <!-- Default is true -->
                    
                <!--  TCP/IP is the host discovery mechanism -->
                <tcp-ip enabled="true">
                    <member>{GROUP-MEMBER-1}</member> <!-- Name of host belonging to cluster  -->
                    ....
                    <member>{GROUP-MEMBER-N}</member> <!-- Name of host belonging to cluster  -->
                </tcp-ip>
             </join> 
         </network> 
    </hazelcast> 





# AbstractSynchronizedTask

This implementation allows to synchronize services or task over several instances. The threads are synchronized with the Hazelcast implementation. The `AbstractSynchronizedTask` has an interface `SynchronizedTask` and can be used to inject implementations. 

This implementation allows only instances with a max lease time for a thread. It has been done intentionally to avoid implementations which could cause a blocking thread. Optionally a minimum lease time could be set as the thread will be synchronized over the network it might take a couple of milliseconds.

The final method `runSynchronizedTask` synchronizes the threads on each node. Its default behavior allows that only one task can run at the time and all others will be aborted. This behavior can be overwritten by taking responsibility over the lock. For further information please refer to the below examples and the java documentation.


## Spring examples with default behavior


### Service example

The service can be injected with the `SynchronizedTask` interface like:

    @Autowired
    @Qualifier("MySyncService")
    private SynchronizedTask syncService;
    
    ....
    
    ... {
        ...
        syncService.runSynchronizedTask();
        ...
        }
    

The service implementation could look like:


    @Service("MySyncService")
    public class SynchronizedService extends AbstractSynchronizedTask {
    
        //use external configurations whenever possible!
        public SynchronizedService(@Autowirded HazelcastInstance hzInstance, long minLeaseTime) {
            super(hzInstance, "uniqueId", minLeaseTime, 30L, TimeUnit.SECONDS);
        }
    
        @Override
        public void task() {
            System.out.println("SynchronizedService: TEST");
        }
    }


### Task example

    @Component
    public class SynchronizedTask extends AbstractSynchronizedTask {
    
        public SynchronizedTask(@Autowired HazelcastInstance hzInstance) {
            super(hzInstance, "uniqueId", 5_000L, 30L, TimeUnit.SECONDS);
        }
    
        @Scheduled(cron = "0 * * * * *")
        public void scheduler() {
            runSynchronizedTask();
        }
    
        @Override
        public void task() {
            System.out.println("SynchronizedTask: TEST");
        }
    }


## Spring example with active waiting

    @Component
    public class SynchronizedWaitTask extends AbstractSynchronizedTask {
    
        public SynchronizedWaitTask(@Autowired HazelcastInstance hzInstance) {
            super(hzInstance, "uniqueId", 10_000L, 25L, TimeUnit.SECONDS);
        }
        
        //use external configurations whenever possible!
        @Scheduled(cron = "0 * * * * *")
        public void scheduler() {
            runSynchronizedTask();
        }
    
        @Override
        public void task() {
            System.out.println("SynchronizedWaitTask: TEST");
        }
    
        @Override
        protected boolean isWaitingActive() {
            return true;
        }
    
        @Override
        protected void waiting(ILock lock) throws Exception {
            try {
                if (!lock.tryLock(lock.getRemainingLeaseTime() + 2_000, TimeUnit.MILLISECONDS, getLeaseTime(), getLeaseUnit())) {
                    throw new Exception("Could not acquire lock after 30 seconds.");
                }
            } catch (InterruptedException e) {
                throw new Exception("InterruptedException: ....");
            }
        }
    }

