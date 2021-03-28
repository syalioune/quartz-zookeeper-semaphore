package fr.syalioune.scheduling.quartz.semaphore;

import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.quartz.impl.jdbcjobstore.LockException;
import org.quartz.impl.jdbcjobstore.Semaphore;
import org.quartz.impl.jdbcjobstore.TablePrefixAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz semaphore using Zookeeper's LeaderLatch to periodically elect a leader.
 */
public class ZookeeperSemaphore implements Semaphore, TablePrefixAware {

  /**
   * Logger.
   */
  private static Logger LOG = LoggerFactory.getLogger(ZookeeperSemaphore.class);

  /**
   * Lock wait time.
   */
  private static final int LOCK_WAIT_TIME = 1000;

  /**
   * Zookeeper connect string.
   */
  private String zookeeperConnectString;

  /**
   * Zookeeper Auth username.
   */
  private String zookeeperAuthUsername;

  /**
   * Zookeeper Auth password.
   */
  private String zookeeperAuthPassword;

  /**
   * Zookeeper Auth scheme.
   */
  private String zookeeperAuthScheme;

  /**
   * Zookeeper ZNode used for leader election.
   */
  private String zookeeperLockPath;

  /**
   * Zookeeper connection retry period.
   */
  private int zookeeperRetryMs = 1000;

  /**
   * The current node identifier.
   */
  private String nodeId;

  /**
   * The period for leadership holding.
   */
  private long leadershipPeriodMs = -1;

  /**
   * The scheduler name.
   */
  private String schedName;

  /**
   * Leadership flag.
   */
  private AtomicBoolean isLeader = new AtomicBoolean(false);

  /**
   * Scheduler service for the renewal thread.
   */
  private ScheduledExecutorService scheduler;

  /**
   * Leadership renewal thread.
   */
  private ZkSemaphoreLeaderhsipRenewalThread leadershipRenewalThread;

  /**
   * Constructor.
   */
  public ZookeeperSemaphore(){
    scheduler = Executors.newSingleThreadScheduledExecutor();
    leadershipRenewalThread = new ZkSemaphoreLeaderhsipRenewalThread(this);
    scheduler.scheduleAtFixedRate(leadershipRenewalThread, 5, 5, TimeUnit.SECONDS);
  }

  /**
   * Constructor.
   */
  public ZookeeperSemaphore(long initialDelay, long renewalCheckPeriod){
    scheduler = Executors.newSingleThreadScheduledExecutor();
    leadershipRenewalThread = new ZkSemaphoreLeaderhsipRenewalThread(this);
    scheduler.scheduleAtFixedRate(leadershipRenewalThread, initialDelay, renewalCheckPeriod, TimeUnit.SECONDS);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean obtainLock(Connection conn, String lockName) throws LockException {
    LOG.info("Trying to obtain lock {}", lockName);
    try {
      leadershipRenewalThread.awaitInitialization();
      while(!isLeader.get()) {
        LOG.debug("Lock was not granted");
        Thread.sleep(LOCK_WAIT_TIME);
      }
      LOG.info("Lock was granted");
      return true;
    } catch (InterruptedException e) {
      throw new LockException(e.getMessage(), e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void releaseLock(String lockName) throws LockException {
  }

  @Override
  public boolean requiresConnection() {
    return false;
  }

  /**
   * Predicate to assert if the semaphore has the current leadership.
   *
   * @return true if the node is the current leader, false otherwise.
   */
  public boolean hasLeadership() {
    return isLeader.get();
  }

  /**
   * Swith the leadership flag on.
   */
  void enableLeadership() {
    isLeader.compareAndSet(false, true);
  }

  /**
   * Swith the leadership flag off.
   */
  void disableLeadership() {
    isLeader.compareAndSet(true, false);
  }

  /**
   * Shutdown executor service.
   */
  void shutdown() {
    leadershipRenewalThread.close();
    scheduler.shutdownNow();
  }

  public String getZookeeperConnectString() {
    return zookeeperConnectString;
  }

  public void setZookeeperConnectString(String zookeeperConnectString) {
    this.zookeeperConnectString = zookeeperConnectString;
  }

  public String getZookeeperLockPath() {
    return zookeeperLockPath;
  }

  public void setZookeeperLockPath(String zookeeperLockPath) {
    this.zookeeperLockPath = zookeeperLockPath;
  }

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public long getLeadershipPeriodMs() {
    return leadershipPeriodMs;
  }

  public void setLeadershipPeriodMs(long leadershipPeriodMs) {
    this.leadershipPeriodMs = leadershipPeriodMs;
  }

  @Override
  public void setTablePrefix(String tablePrefix) {
    // Nothing to be done  - Only the scheduler name is needed
  }

  @Override
  public void setSchedName(String schedName) {
    this.schedName = schedName;
  }

  public String getSchedName() {
    return schedName;
  }

  public int getZookeeperRetryMs() {
    return zookeeperRetryMs;
  }

  public void setZookeeperRetryMs(int zookeeperRetryMs) {
    this.zookeeperRetryMs = zookeeperRetryMs;
  }

  public String getZookeeperAuthUsername() {
    return zookeeperAuthUsername;
  }

  public void setZookeeperAuthUsername(String zookeeperAuthUsername) {
    this.zookeeperAuthUsername = zookeeperAuthUsername;
  }

  public String getZookeeperAuthPassword() {
    return zookeeperAuthPassword;
  }

  public void setZookeeperAuthPassword(String zookeeperAuthPassword) {
    this.zookeeperAuthPassword = zookeeperAuthPassword;
  }

  public String getZookeeperAuthScheme() {
    return zookeeperAuthScheme;
  }

  public void setZookeeperAuthScheme(String zookeeperAuthScheme) {
    this.zookeeperAuthScheme = zookeeperAuthScheme;
  }
}
