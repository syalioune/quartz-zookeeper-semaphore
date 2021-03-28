package fr.syalioune.scheduling.quartz.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.io.IOException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for expiring LeaderLatch object.
 */
public class ExpiringLeaderLatchTest {

  /**
   * Latch under test.
   */
  private ExpiringLeaderLatch expiringLeaderLatch;

  /**
   * Zookeeper LeaderLatch mock.
   */
  private LeaderLatch leaderLatchMock;

  /**
   * Curator client mock.
   */
  private CuratorFramework curatorFrameworkMock;

  @BeforeEach
  public void setup() {
    leaderLatchMock = mock(LeaderLatch.class);
    curatorFrameworkMock = mock(CuratorFramework.class);
    expiringLeaderLatch = new ExpiringLeaderLatch(leaderLatchMock, curatorFrameworkMock);
  }

  @Test
  public void shouldAssertThatTheLatchHasExpired() throws InterruptedException {
    // Arrange
    expiringLeaderLatch.setStartTime(System.currentTimeMillis());
    Thread.sleep(50);

    // Act
    boolean hasExpired = expiringLeaderLatch.hasExpired(30);

    // Assert
    assertTrue(hasExpired, "The LeaderLatch should have expired");
  }

  @Test
  public void shouldAssertThatTheLatchHasNotExpired() throws InterruptedException {
    // Arrange
    expiringLeaderLatch.setStartTime(System.currentTimeMillis());

    // Act
    boolean hasExpired = expiringLeaderLatch.hasExpired(10000);

    // Assert
    assertFalse(hasExpired, "The LeaderLatch should not have expired");
  }

  @Test
  public void shouldCloseInnerRessourcesWhenLatchIsClosed() throws IOException {
    // Arrange
    // Nothing to be done

    // Act
    expiringLeaderLatch.close();

    // Assert
    Mockito.verify(leaderLatchMock, times(1)).close();
    Mockito.verify(curatorFrameworkMock, times(1)).close();
  }

}
