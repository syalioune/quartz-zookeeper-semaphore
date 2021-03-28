package fr.syalioune.scheduling.quartz.util;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.apache.curator.utils.CloseableUtils;

/**
 * Utility class to model a Zookeeper @see LeaderLatch with an expiry date.
 */
public class ExpiringLeaderLatch {

  /**
   * The LeaderLatch activity start date.
   */
  private Long startTime;

  /**
   * Curator LeaderLatch.
   */
  private LeaderLatch leaderLatch;

  /**
   * Curator client
   */
  private CuratorFramework curatorFramework;

  public ExpiringLeaderLatch(LeaderLatch leaderLatch, CuratorFramework curatorFramework) {
    this.leaderLatch = leaderLatch;
    this.startTime = System.currentTimeMillis();
    this.leaderLatch.addListener(new LeaderLatchListener() {
      public void isLeader() {
        startTime = System.currentTimeMillis();
      }
      public void notLeader() {
      }
    });
    this.curatorFramework = curatorFramework;
  }

  /**
   * Predicate to check if the LeaderLatch has expired.
   *
   * @param delay
   *          The delay to base the expiration check on
   *
   * @return True if the LeaderLatch has expired, false otherwise
   */
  public boolean hasExpired(long delay) {
    return System.currentTimeMillis() - this.startTime > delay;
  }

  /**
   * LeaderLatch && Zookeeper client closing.
   */
  public void close() {
    CloseableUtils.closeQuietly(leaderLatch);
    CloseableUtils.closeQuietly(curatorFramework);
  }

  public Long getStartTime() {
    return startTime;
  }

  public void setStartTime(Long startTime) {
    this.startTime = startTime;
  }

  public LeaderLatch getLeaderLatch() {
    return leaderLatch;
  }

  public void setLeaderLatch(LeaderLatch leaderLatch) {
    this.leaderLatch = leaderLatch;
  }
}
