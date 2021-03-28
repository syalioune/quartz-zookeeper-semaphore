package fr.syalioune.scheduling.quartz.semaphore.demo.job;

import io.micrometer.core.instrument.MeterRegistry;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

public class NoOpJob extends QuartzJobBean {

  private static Logger LOG = LoggerFactory.getLogger(NoOpJob.class);

  private MeterRegistry meterRegistry;

  public NoOpJob(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  protected void executeInternal(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    LOG.info("NoOpJob is executing");
    meterRegistry.counter("noOpJob.execution").increment();
  }
}
