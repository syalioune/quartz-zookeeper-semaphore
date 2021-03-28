package fr.syalioune.scheduling.quartz.semaphore;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.impl.jdbcjobstore.LockException;

/**
 * Zookeeper semaphore test.
 */
public class ZookeeperSemaphoreTest {

  /**
   * Zookeeper semaphore under test.
   */
  private ZookeeperSemaphore zookeeperSemaphore;

  /**
   * Semaphores to shutdown.
   */
  private List<ZookeeperSemaphore> semaphoreToShutdown = new LinkedList<>();

  /**
   * Zookeeper test server.
   */
  private static TestingServer zookeeperServer;

  /**
   * Leadership renewal period.
   */
  private static final long LEADERSHIP_RENEWAL_PERIOD = 15_000;

  /**
   * Leadership renewal wait time for checking.
   */
  private static final long WAIT_TIME = 5_000;

  @BeforeAll
  public static void init() throws Exception {
    zookeeperServer = new TestingServer(true);
  }

  @BeforeEach
  public void setup() {
    zookeeperSemaphore = new ZookeeperSemaphore(1, 1);
    initializeSemaphore(zookeeperSemaphore, "mainNode");
    semaphoreToShutdown.add(zookeeperSemaphore);
  }

  private void initializeSemaphore(ZookeeperSemaphore semaphore, String nodeId) {
    semaphore.setNodeId(nodeId);
    semaphore.setSchedName("testScheduler");
    semaphore.setZookeeperConnectString(zookeeperServer.getConnectString());
    semaphore.setZookeeperLockPath("/lock");
    semaphore.setLeadershipPeriodMs(LEADERSHIP_RENEWAL_PERIOD);
  }

  @AfterEach
  public void teardown() {
    semaphoreToShutdown.forEach(semaphore -> semaphore.shutdown());
  }

  @AfterAll
  public static void close() throws IOException {
    zookeeperServer.close();
  }

  @Test
  public void shouldNotRequireConnection() {
    // Arrange
    // Nothing to be done

    // Act
    boolean requiresConnection = zookeeperSemaphore.requiresConnection();

    // Assert
    assertFalse(requiresConnection);
  }

  @Test
  public void shouldBeLeaderWhenThereIsOnlyOneNode() throws LockException {
    // Arrange
    // Nothing to be done

    // Act
    boolean isLeader = zookeeperSemaphore.obtainLock(null, "lock");

    // Assert
    assertTrue(isLeader);
  }

  @Test
  public void shouldOnlyHaveOneLeaderAtATimeWhenThereAreMultipleNodes() throws LockException {
    // Arrange
    List<String> nodeIds = Arrays.asList("secondNode", "thirdNode");
    List<ZookeeperSemaphore> semaphores = nodeIds.stream().map(id -> {
      ZookeeperSemaphore semaphore = new ZookeeperSemaphore(1, 1);
      initializeSemaphore(semaphore, id);
      semaphoreToShutdown.add(semaphore);
      return semaphore;
    }).collect(Collectors.toList());


    // Act
    zookeeperSemaphore.obtainLock(null, "lock");
    // obtainLock is not called on the other semaphores to avoid blocking call - A specific test will be done for that

    // Assert
    assertTrue(zookeeperSemaphore.hasLeadership(), zookeeperSemaphore.getNodeId()+ " should have leadership");
    assertAll(semaphores.stream().map(semaphore -> {
      return () -> assertFalse(semaphore.hasLeadership(), semaphore.getNodeId()+" should not have leadership");
    }));
  }

  @Test
  public void shouldPeriodicallyGiveLeadershipToNodeInFIFOMode() throws LockException, InterruptedException {
    // Arrange
    List<String> nodeIds = Arrays.asList("secondNode", "thirdNode");
    List<ZookeeperSemaphore> semaphores = nodeIds.stream().map(id -> {
      ZookeeperSemaphore semaphore = new ZookeeperSemaphore(1, 1);
      initializeSemaphore(semaphore, id);
      semaphoreToShutdown.add(semaphore);
      return semaphore;
    }).collect(Collectors.toList());


    // Act
    zookeeperSemaphore.obtainLock(null, "lock");
    // obtainLock is not called on the other semaphores to avoid blocking call - A specific test will be done for that

    // Assert
    assertTrue(zookeeperSemaphore.hasLeadership(), zookeeperSemaphore.getNodeId()+ " should have leadership");
    for(int i=0;i<nodeIds.size(); i++) {
      ZookeeperSemaphore semaphore = semaphores.get(i);
      Thread.sleep(LEADERSHIP_RENEWAL_PERIOD+WAIT_TIME);
      assertTrue(semaphore.hasLeadership(), semaphore.getNodeId()+" should have leadership");
      assertFalse(zookeeperSemaphore.hasLeadership(), zookeeperSemaphore.getNodeId()+ " should not have leadership");
      semaphores.stream().filter(s -> !s.getNodeId().equals(semaphore.getNodeId())).forEach(s -> {
        assertFalse(s.hasLeadership(), s.getNodeId()+ " should not have leadership");
      });
    }
  }

  @Test
  public void shouldBlockSemaphoreUntilItHasTheLeadership() throws LockException {
    // Arrange
    List<String> nodeIds = Arrays.asList("secondNode", "thirdNode");
    List<ZookeeperSemaphore> semaphores = nodeIds.stream().map(id -> {
      ZookeeperSemaphore semaphore = new ZookeeperSemaphore(1, 1);
      initializeSemaphore(semaphore, id);
      semaphoreToShutdown.add(semaphore);
      return semaphore;
    }).collect(Collectors.toList());

    // Act
    boolean result = zookeeperSemaphore.obtainLock(null, "lock");

    // Assert
    assertTrue(zookeeperSemaphore.hasLeadership(), zookeeperSemaphore.getNodeId()+ " should have leadership");
    for(int i=0;i<nodeIds.size(); i++) {
      ZookeeperSemaphore semaphore = semaphores.get(i);
      result = semaphore.obtainLock(null, "lock");
      assertTrue(result, semaphore.getNodeId()+" should have leadership");
      assertFalse(zookeeperSemaphore.hasLeadership(), zookeeperSemaphore.getNodeId()+ " should not have leadership");
      semaphores.stream().filter(s -> !s.getNodeId().equals(semaphore.getNodeId())).forEach(s -> {
        assertFalse(s.hasLeadership(), s.getNodeId()+ " should not have leadership");
      });
    }
  }
}
