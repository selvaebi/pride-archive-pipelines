package uk.ac.ebi.pride.archive.pipeline.tasklets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import uk.ac.ebi.pride.archive.pipeline.utility.BackupUtil;
import uk.ac.ebi.pride.data.io.SubmissionFileParser;
import uk.ac.ebi.pride.mongodb.archive.service.projects.PrideProjectMongoService;
import uk.ac.ebi.pride.mongodb.molecules.model.peptide.PrideMongoPeptideEvidence;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static uk.ac.ebi.pride.archive.pipeline.tasklets.LaunchIndividualEbeyeXmlTasklet.launchIndividualEbeyeXmlGenerationForProjectAcc;

@StepScope
@Component
public class GenerateEbeyeXmlTasklet extends AbstractTasklet {

    public static final Logger logger = LoggerFactory.getLogger(GenerateEbeyeXmlTasklet.class);

    @Value("${pride.data.backup.path}")
    String backupPath;

    @Value("file:${pride.repo.data.base.dir}/#{jobExecutionContext['public.path.fragment']}/internal/${submission.file.name}")
    private File submissionFile;

    @Value("${pride.ebeye.dir}")
    private File outputDirectory;

    @Value("${project.accession}")
    private String projectAccession;

    @Autowired
    private PrideProjectMongoService prideProjectMongoService;

    @Value("${process.all}")
    private boolean processAll;

    private List<String> exceptionsLaunchingProjects;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        exceptionsLaunchingProjects = Collections.synchronizedList(new ArrayList<String>());
        if (processAll) {
            prideProjectMongoService.getAllProjectAccessions().parallelStream().forEach(projAcc -> {
                launchIndividualEbeyeXmlGenerationForProjectAcc(projAcc, prideProjectMongoService, exceptionsLaunchingProjects);
            });
            if (!CollectionUtils.isEmpty(exceptionsLaunchingProjects)) {
                exceptionsLaunchingProjects.parallelStream().forEach(s -> logger.error("Problems launching EBeye generation for: " + s));
                throw new JobExecutionException("Unable to launch individual EBeye generation jobs");
            }
        } else {
            generateEBeye(projectAccession, submissionFile);
        }
        logger.info("Finished generating EBeye XML.");
        return RepeatStatus.FINISHED;
    }

    /**
     * This method looks up a project from the provided accession number, and if it is not partial, i.e. with protein IDs,
     * then these are mapped to Uniprot and Ensembl. EBeye XML is then generated for this project and saved to an output file
     * in the PRIDE Archive's EBeye XML directory.
     *
     * @param projectAcc the project's accession number to generate EBeye XML for
     * @throws Exception any problem during the EBeye generation process
     */
    private void generateEBeye(String projectAcc, File submissionFile) throws Exception {
        Set<String> proteins = restoreFromFile(projectAcc);
        Map<String, String> proteinMapping = new HashMap<>();
        if (proteins.size() > 0) {
            //TODO protein mapping

            proteinMapping.putAll(getProteinMapping(proteins));
        }

        GenerateEBeyeXMLNew generateEBeyeXMLNew = new GenerateEBeyeXMLNew(prideProjectMongoService.findByAccession(projectAcc).get(),
                SubmissionFileParser.parse(submissionFile), outputDirectory, proteinMapping, true);
        generateEBeyeXMLNew.generate();
    }

    public Map<String,String> getProteinMapping(Set<String> proteins) {
        return null;
    }


    public Set<String> restoreFromFile(String projectAccession) throws Exception {
        String dir = backupPath + projectAccession;
        Set<String> proteinAccessions = new HashSet<>();
        for (Path f : Files.newDirectoryStream(Paths.get(dir), path -> path.toFile().isFile())) {
            if (f.getFileName().toString().endsWith(PrideMongoPeptideEvidence.class.getSimpleName() + BackupUtil.JSON_EXT)) {
                List<PrideMongoPeptideEvidence> objs = BackupUtil.getObjectsFromFile(f, PrideMongoPeptideEvidence.class);
                objs.forEach(o -> {
                    proteinAccessions.add(o.getProteinAccession());
                });
            }
        }
        return proteinAccessions;
    }

    public void setBackupPath(String backupPath) {
        this.backupPath = backupPath;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(prideProjectMongoService,"prideProjectMongoService should not be null");
        Assert.notNull(outputDirectory, "Output directory cannot be null.");
    }
}

