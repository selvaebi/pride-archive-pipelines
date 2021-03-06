package uk.ac.ebi.pride.archive.pipeline.jobs.projects;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.ac.ebi.pride.archive.pipeline.configuration.JobRunnerTestConfiguration;
import uk.ac.ebi.pride.archive.pipeline.utility.SubmissionPipelineConstants;

/**
 * @author Suresh Hewapathirana
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {SyncMissingProjectsWithMongoJob.class, JobRunnerTestConfiguration.class})
@TestPropertySource(value = "classpath:application-test.properties")
@Slf4j
public class SyncMissingProjectsWithMongoJobTest {

    @Autowired
    SyncMissingProjectsWithMongoJob syncMissingProjectsWithMongoJob;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    /**
     * This test should be run during the development process
     * @throws Exception
     */
    @Test
    public void syncMissingProjectsOracleToMongoDB() throws Exception {
        JobParameters param = new JobParametersBuilder()
                .addString("submissionType", SubmissionPipelineConstants.SubmissionsType.PUBLIC.name())
                .toJobParameters();
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(param);
        Assert.assertEquals(BatchStatus.COMPLETED.name(), jobExecution.getExitStatus().getExitCode());
    }
}