This util helps to synchronize tasks over several nodes. It integrates [Hazelcast](https://hazelcast.com) for server clustering.

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
It needs an valid (cluster) Hazelcast configuration. There are several ways (Java, XML, Cluster/Client, only Cluster etc.) to create a Hazelcast instance. Make yourself familiar with the Hazelcast principles.

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
                xsi:schemaLocation="http://www..com/schema/config 
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

This implementation allows to synchronize services or tasks over several instances. The threads are synchronized with the Hazelcast implementation. The `AbstractSynchronizedTask` has an interface `SynchronizedTask` and can be used to inject implementations. 

This implementation allows only instances with a minimum and maximum lease time for a thread. It has been done intentionally to avoid implementations which could cause a blocking thread. A minimum lease time has to be set as the tasks will be synchronized over the network it might take a couple of milliseconds.

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
        public SynchronizedService(@Autowirded HazelcastInstance hzInstance) {
            super(hzInstance, "uniqueId", 5_000L, 30L, TimeUnit.SECONDS);
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
    
    	  //use external configurations whenever possible!
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
		
			final String msg = format("Could %sacquire lock after %d seconds.", "%s", getLeaseUnit().toSeconds(getLeaseTime()));
			final String not = "not ";
			
			// a loop to try to acquire the lock might be another solution
			try {
				if (lock.tryLock(lock.getRemainingLeaseTime() + 1_000, TimeUnit.MILLISECONDS, getLeaseTime(), getLeaseUnit())) {
					logInfo(format(msg, ""));
				} else {
					final String warnStr = format(msg, not);
					logWarn(warnStr);
					throw new RuntimeException(warnStr);
				}
			} catch (InterruptedException e) {
				logError(format("InterruptedException: " + msg, not), e);
				throw e;
			}
		}

