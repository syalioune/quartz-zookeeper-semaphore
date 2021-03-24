package fr.syalioune.dds.scheduling.quartz;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatch.CloseMode;
import org.apache.curator.framework.recipes.leader.Participant;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.RetryForever;
import org.quartz.impl.jdbcjobstore.LockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background thread to periodically renew Zookeeper leader latch.
 */
public class ZkSemaphoreLeaderhsipRenewalThread implements Runnable {

  /**
   * Logger.
   */
  private static Logger LOG = LoggerFactory.getLogger(ZkSemaphoreLeaderhsipRenewalThread.class);

  /**
   * Zookeeper Quartz semaphore.
   */
  private ZookeeperSemaphore zkSemaphore;

  /**
   * Latch to signal Zookeeper semaphore that the thread is initialized.
   */
  private CountDownLatch initializationLatch;

  /**
   * Expiring Leader latch.
   */
  private ExpiringLeaderLatch expiringLeaderLatch;

  /**
   * Leader latch initialization time.
   */
  private static final Long LEADER_LATCH_INIT_TIME = 500L;

  /**
   * Constructor.
   *
   * @param zkSemaphore
   *          The semaphore related to this renewal thread.
   */
  public ZkSemaphoreLeaderhsipRenewalThread(ZookeeperSemaphore zkSemaphore) {
    this.initializationLatch = new CountDownLatch(1);
    this.zkSemaphore = zkSemaphore;
  }

  /**
   * Allow client to wait for the thread initialization.
   *
   * @throws LockException
   *          In case the calling thread is interrupted
   */
  public void awaitInitialization() throws LockException {
    try {
      LOG.debug("Waiting for latch initialization");
      initializationLatch.await();
    } catch (InterruptedException e) {
      LOG.warn("The thread {} has been interrupted", Thread.currentThread().getName());
      throw new LockException(e.getMessage(),e);
    }
  }

  @Override
  public void run() {
    try {
      LOG.debug("Starting semaphore leadership check loop for Quartz scheduler {}", zkSemaphore.getSchedName());

      if (expiringLeaderLatch == null) {
        LOG.debug("Renewing the zookeeper expiring leader latch");
        expiringLeaderLatch = buildLatch();
      }

      Collection<Participant> participants = expiringLeaderLatch.getLeaderLatch().getParticipants();
      Optional<Participant> leader = participants.stream().filter(p -> p.isLeader()).findFirst();

      LOG.info("Quartz cluster currently has {} participants", participants.size());
      if (nodeIsTheCurrentLeader(participants, leader)) {
        LOG.info("{} is the current leader", zkSemaphore.getNodeId());
        zkSemaphore.enableLeadership();
      } else {
        LOG.info("{} is not the leader", zkSemaphore.getNodeId());
        zkSemaphore.disableLeadership();
      }

      if (zkSemaphore.hasLeadership() && expiringLeaderLatch.hasExpired(zkSemaphore.getLeadershipPeriodMs())) {
        LOG.debug("Leader Latch has expired : {} - {} = {}", System.currentTimeMillis(),
            expiringLeaderLatch
                .getStartTime(), System.currentTimeMillis() - expiringLeaderLatch.getStartTime());
        expiringLeaderLatch.close();
        zkSemaphore.disableLeadership();
        expiringLeaderLatch = null;
      } else {
        LOG.debug("Leader Latch has not expired : {} - {} = {}", System.currentTimeMillis(),
            expiringLeaderLatch
                .getStartTime(), System.currentTimeMillis() - expiringLeaderLatch.getStartTime());
      }

      LOG.debug("Ending semaphore leadership check loop for Quartz scheduler {}", zkSemaphore.getSchedName());

      if (initializationLatch.getCount() > 0) {
        initializationLatch.countDown();
      }
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  /**
   * Predicate to assess the node is the current leader.
   *
   * @param participants
   *          The participants of the Zookeeper LeaderLatch recipes
   *
   * @param leader
   *          The plausible leader
   *
   * @return true if the current node is the current leader, false otherwise
   */
  private boolean nodeIsTheCurrentLeader(Collection<Participant> participants, Optional<Participant> leader) {
    return participants.size() == 1 || (leader.isPresent() && zkSemaphore.getNodeId().equals(leader.get().getId()));
  }

  private ExpiringLeaderLatch buildLatch() {
    CuratorFrameworkFactory.Builder builder =  CuratorFrameworkFactory.builder().namespace(zkSemaphore.getSchedName()).connectString(zkSemaphore.getZookeeperConnectString()).retryPolicy(new RetryForever(zkSemaphore.getZookeeperRetryMs()));
    if(zkSemaphore.getZookeeperAuthScheme() != null) {
      builder.authorization(zkSemaphore.getZookeeperAuthScheme(), zkSemaphore.getZookeeperAuthDigest().getBytes());
    }
    CuratorFramework client = builder.build();
    LeaderLatch latch = new LeaderLatch(client, zkSemaphore.getZookeeperLockPath(), zkSemaphore.getNodeId(), CloseMode.NOTIFY_LEADER);
    client.start();
    client.getConnectionStateListenable().addListener((CuratorFramework curator, ConnectionState newState) -> {
      connectionStateHandler(newState);
    });
    try {
      latch.start();
      Thread.sleep(LEADER_LATCH_INIT_TIME);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new ExpiringLeaderLatch(latch, client);
  }

  private void connectionStateHandler(ConnectionState newState) {
    switch (newState) {
      case CONNECTED:
      case RECONNECTED:
        break;
      case LOST:
      case SUSPENDED:
        if (zkSemaphore.hasLeadership()) {
          zkSemaphore.disableLeadership();
        }
        break;
      default:
        break;
    }
  }
}
