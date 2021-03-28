package fr.syalioune.scheduling.quartz.semaphore.demo.configuration;

import fr.syalioune.scheduling.quartz.semaphore.demo.job.NoOpJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfiguration {

  @Bean
  public JobDetail noOpJob() {
    return JobBuilder.newJob(NoOpJob.class).withIdentity("noOp","cron").storeDurably().build();
  };

  @Bean
  public Trigger noOpJobTrigger() {
    return TriggerBuilder.newTrigger().forJob(noOpJob()).withIdentity("noOp", "cron").withSchedule(CronScheduleBuilder.cronSchedule("0/5 * * ? * * *")).build();
  };

}
