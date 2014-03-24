package org.iso.registry.api.initialization;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.step.tasklet.MethodInvokingTaskletAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableBatchProcessing
public class InitializationConfiguration
{
	@Autowired private JobBuilderFactory jobs;
	@Autowired private StepBuilderFactory steps;

//	@Bean
//	public Job job() {
//		return jobs.get("myJob").start(step1()).next(step2()).build();
//	}

//	@Bean
//	protected Step step1() {
//		MethodInvokingTaskletAdapter a = new MethodInvokingTaskletAdapter();
//		a.se
//		return steps.get("step1").tasklet()
//	}
//
//	@Bean
//	protected Step step2() {
//	}
	
	
}
