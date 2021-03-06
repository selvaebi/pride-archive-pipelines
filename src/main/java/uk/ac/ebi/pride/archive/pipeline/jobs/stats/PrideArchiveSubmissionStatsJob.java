package uk.ac.ebi.pride.archive.pipeline.jobs.stats;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import uk.ac.ebi.pride.archive.dataprovider.param.CvParam;
import uk.ac.ebi.pride.archive.dataprovider.param.ParamProvider;
import uk.ac.ebi.pride.archive.dataprovider.common.Tuple;
import uk.ac.ebi.pride.archive.pipeline.configuration.DataSourceConfiguration;
import uk.ac.ebi.pride.archive.pipeline.jobs.AbstractArchiveJob;
import uk.ac.ebi.pride.archive.pipeline.utility.SubmissionPipelineConstants;
import uk.ac.ebi.pride.mongodb.archive.model.projects.MongoPrideProject;
import uk.ac.ebi.pride.mongodb.archive.model.stats.MongoPrideStats;
import uk.ac.ebi.pride.mongodb.archive.model.stats.PrideStatsKeysConstants;
import uk.ac.ebi.pride.mongodb.archive.service.projects.PrideProjectMongoService;
import uk.ac.ebi.pride.mongodb.archive.service.stats.CategoryStats;
import uk.ac.ebi.pride.mongodb.archive.service.stats.PrideStatsMongoService;
import uk.ac.ebi.pride.mongodb.configs.ArchiveMongoConfig;
import uk.ac.ebi.pride.mongodb.configs.MoleculesMongoConfig;
import uk.ac.ebi.pride.mongodb.molecules.service.molecules.PrideMoleculesMongoService;
import uk.ac.ebi.pride.utilities.term.CvTermReference;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This code is licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * ==Overview==
 * <p>
 * This class
 * <p>
 * Created by ypriverol (ypriverol@gmail.com) on 27/06/2018.
 */
@Configuration
@Slf4j
@EnableBatchProcessing
@Import({ArchiveMongoConfig.class, DataSourceConfiguration.class, ArchiveMongoConfig.class, MoleculesMongoConfig.class})
public class PrideArchiveSubmissionStatsJob extends AbstractArchiveJob {

    private final
    PrideProjectMongoService prideProjectMongoService;

    private final
    PrideStatsMongoService prideStatsMongoService;

    final PrideMoleculesMongoService moleculesService;

    private Date date;

    @Autowired
    public PrideArchiveSubmissionStatsJob(PrideProjectMongoService prideProjectMongoService, PrideStatsMongoService prideStatsMongoService, PrideMoleculesMongoService moleculesService) {
        this.prideProjectMongoService = prideProjectMongoService;
        this.prideStatsMongoService = prideStatsMongoService;
        this.moleculesService = moleculesService;
    }

    /**
     * All the stats are compute at an specific time 00:00:00
     *
     */
    @Autowired
    public void initDate() {
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.HOUR_OF_DAY, 0);
        this.date = now.getTime();
    }

    /**
     * This method estimate the number of submissions per year. The method stored in the database the final results.
     * @return @{@link Step}
     */
    @Bean
    Step estimateSubmissionByYearStep() {
        return stepBuilderFactory
                .get(SubmissionPipelineConstants.PrideArchiveStepNames.PRIDE_ARCHIVE_SUBMISSION_STATS_YEAR.name())
                .tasklet((stepContribution, chunkContext) -> {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("YYYY");
                    List<Tuple<String, Integer>> submissionsByDate = prideProjectMongoService
                            .findAllStream()
                            .collect(Collectors
                                    .groupingBy(item -> simpleDateFormat.format(item.getSubmissionDate()).toUpperCase()))
                            .entrySet()
                            .stream()
                            .map( x-> new Tuple<>(x.getKey(), x.getValue().size()))
                            .sorted(Comparator.comparingInt(x -> Integer.parseInt(x.getKey())))
                            .collect(Collectors.toList());
                    prideStatsMongoService.updateSubmissionCountStats(date, PrideStatsKeysConstants.SUBMISSIONS_PER_YEAR, submissionsByDate);
                    return RepeatStatus.FINISHED;

                })
                .build();
    }

    @Bean
    public Step computeMoleculesStats() {
        return stepBuilderFactory
                .get("computeMoleculesStats")
                .tasklet((stepContribution, chunkContext) -> {
                    long proteinEvidences = moleculesService.getNumberProteinEvidences();
                    long peptideEvidecnes = moleculesService.getNumberPeptideEvidences();
                    long psmEvidences     = moleculesService.getNumberPSMEvidecnes();

                    List<Tuple<String, Integer>> moleculesStats = new ArrayList<>();
                    moleculesStats.add(new Tuple<String, Integer>("Number protein evidences", Math.toIntExact(proteinEvidences)));
                    moleculesStats.add(new Tuple<String, Integer>("Number peptide evidences",  Math.toIntExact(peptideEvidecnes)));
                    moleculesStats.add(new Tuple<String, Integer>("Number psm evidences",  Math.toIntExact(psmEvidences)));
                    prideStatsMongoService.updateSubmissionCountStats(date, PrideStatsKeysConstants.EVIDENCES_IN_ARCHIVE, moleculesStats);

                    return RepeatStatus.FINISHED;
                }).build();
    }

    /**
     * This method estimate the number of submissions per month. The method stored in the database the final results of the
     * metrics.
     * @return @{@link Step}
     */
    @Bean
    public Step estimateSubmissionByMonthStep() {
        return stepBuilderFactory
                .get(SubmissionPipelineConstants.PrideArchiveStepNames.PRIDE_ARCHIVE_SUBMISSION_STATS_MONTH.name())
                .tasklet((stepContribution, chunkContext) -> {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("YYYY-MM");
                    List<Tuple<String, Integer>> submissionsByDate = prideProjectMongoService
                            .findAllStream()
                            .collect(Collectors
                                    .groupingBy(item -> simpleDateFormat.format(item.getSubmissionDate()).toUpperCase()))
                            .entrySet()
                            .stream()
                            .map( x-> new Tuple<>(x.getKey(), x.getValue().size()))
                            .sorted((x,y) -> StringUtils.compare(x.getKey(),y.getKey()))
                            .collect(Collectors.toList());
                    prideStatsMongoService.updateSubmissionCountStats(date, PrideStatsKeysConstants.SUBMISSIONS_PER_MONTH, submissionsByDate);
                    return RepeatStatus.FINISHED;

                })
                .build();
    }


    /**
     * This method estimate the number of submissions by Instrument name.
     * @return @{@link Step}
     */
    @Bean
    public Step estimateInstrumentsCountStep() {
        return stepBuilderFactory
                .get(SubmissionPipelineConstants.PrideArchiveStepNames.PRIDE_ARCHIVE_SUBMISSION_STATS_INSTRUMENT.name())
                .tasklet((stepContribution, chunkContext) -> {
                    List<Tuple<String, Integer>> submissionsByDate = prideProjectMongoService
                            .findAllStream()
                            .flatMap(x -> x.getInstrumentsCvParams().stream())
                            .collect(Collectors.groupingBy(ParamProvider::getName))
                            .entrySet()
                            .stream()
                            .map(x -> new Tuple<>(x.getKey(), x.getValue().size()))
                            .sorted((x, y) -> y.getValue().compareTo(x.getValue()))
                            .collect(Collectors.toList());
                    prideStatsMongoService.updateSubmissionCountStats(date, PrideStatsKeysConstants.SUBMISSIONS_PER_INSTRUMENTS, submissionsByDate);
                    return RepeatStatus.FINISHED;

                })
                .build();
    }

    /**
     * This method estimate the number of submissions by Organism name.
     * @return @{@link Step}
     */
    @Bean
    public Step estimateOrganismCountStep() {
        return stepBuilderFactory
                .get(SubmissionPipelineConstants.PrideArchiveStepNames.PRIDE_ARCHIVE_SUBMISSION_STATS_ORGANISM.name())
                .tasklet((stepContribution, chunkContext) -> {
                    List<Tuple<String, Integer>> submissionsByDate = estimateDatasetsByTermInSampleDescription(prideProjectMongoService.findAllStream(), CvTermReference.EFO_ORGANISM);
                    prideStatsMongoService.updateSubmissionCountStats(date, PrideStatsKeysConstants.SUBMISSIONS_PER_ORGANISM, submissionsByDate);
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    /**
     * This method estimate the number of submissions by Organism part.
     * @return @{@link Step}
     */
    @Bean
    public Step estimateOrganismPartCountStep() {
        return stepBuilderFactory
                .get(SubmissionPipelineConstants.PrideArchiveStepNames.PRIDE_ARCHIVE_SUBMISSION_STATS_ORGANISM_PART.name())
                .tasklet((stepContribution, chunkContext) -> {
                    List<Tuple<String, Integer>> submissionsByDate = estimateDatasetsByTermInSampleDescription(prideProjectMongoService.findAllStream(), CvTermReference.EFO_ORGANISM_PART);
                    prideStatsMongoService.updateSubmissionCountStats(date, PrideStatsKeysConstants.SUBMISSIONS_PER_ORGANISM_PART, submissionsByDate);
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    /**
     * This method estimate the number of submissions by Diseases.
     * @return @{@link Step}
     */
    @Bean
    public Step estimateDiseasesCountStep() {
        return stepBuilderFactory
                .get(SubmissionPipelineConstants.PrideArchiveStepNames.PRIDE_ARCHIVE_SUBMISSION_STATS_DISEASES.name())
                .tasklet((stepContribution, chunkContext) -> {
                    List<Tuple<String, Integer>> submissionsByDate = estimateDatasetsByTermInSampleDescription(prideProjectMongoService.findAllStream(), CvTermReference.EFO_DISEASE);
                    prideStatsMongoService.updateSubmissionCountStats(date, PrideStatsKeysConstants.SUBMISSIONS_PER_DISEASES, submissionsByDate);
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    /**
     * This method estimate the number of submissions by Diseases.
     * @return @{@link Step}
     */
    @Bean
    public Step estimateModificationCountStep() {
        return stepBuilderFactory
                .get(SubmissionPipelineConstants.PrideArchiveStepNames.PRIDE_ARCHIVE_SUBMISSION_STATS_MODIFICATIONS.name())
                .tasklet((stepContribution, chunkContext) -> {
                    List<Tuple<String, Integer>> submissionsByDate = prideProjectMongoService
                            .findAllStream()
                            .flatMap( x-> x.getPtmList().stream())
                            .collect(Collectors.groupingBy(CvParam::getName))
                            .entrySet()
                            .stream()
                            .map( x -> new Tuple<>(x.getKey(), x.getValue().size()))
                            .sorted((x,y) -> y.getValue().compareTo(x.getValue()))
                            .collect(Collectors.toList());
                    prideStatsMongoService.updateSubmissionCountStats(date, PrideStatsKeysConstants.SUBMISSIONS_PER_MODIFICATIONS, submissionsByDate);
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step estimateCountryCountStep() {
        return stepBuilderFactory
                .get(SubmissionPipelineConstants.PrideArchiveStepNames.PRIDE_ARCHIVE_SUBMISSION_STATS_COUNTRY.name())
                .tasklet((stepContribution, chunkContext) -> {
                    List<Tuple<String, Integer>> submissionsByCountry = prideProjectMongoService
                            .findAllStream()
                            .filter( x-> (x.getCountries()!=null && x.getCountries().size() >0))
                            .flatMap( x-> x.getCountries().stream())
                            .collect(Collectors.groupingBy(String::trim))
                            .entrySet()
                            .stream()
                            .map( x -> new Tuple<>(x.getKey(), x.getValue().size()))
                            .sorted((x,y) -> y.getValue().compareTo(x.getValue()))
                            .collect(Collectors.toList());
                    prideStatsMongoService.updateSubmissionCountStats(date, PrideStatsKeysConstants.SUBMISSIONS_PER_COUNTRY, submissionsByCountry);
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step estimateSubmissionByCategoryStep() {
        return stepBuilderFactory
                .get(SubmissionPipelineConstants.PrideArchiveStepNames.PRIDE_ARCHIVE_SUBMISSION_STATS_CATEGORY.name())
                .tasklet((stepContribution, chunkContext) -> {
                    Set<CategoryStats> categoryStats = new HashSet<>();

                    List<MongoPrideProject> submissions = prideProjectMongoService.findAllStream().collect(Collectors.toList());

                    List<Tuple<String, Integer>> organisms = estimateDatasetsByTermInSampleDescription(submissions.stream(), CvTermReference.EFO_ORGANISM);
                    List<Tuple<String, Integer>> organismsPart = estimateDatasetsByTermInSampleDescription(submissions.stream(), CvTermReference.EFO_ORGANISM_PART);

                    if(organismsPart.stream().noneMatch(x -> x.getKey().equalsIgnoreCase(CvTermReference.PRIDE_NO_ORGANISM_PART.getName())))
                        organismsPart.add(new Tuple<>(CvTermReference.PRIDE_NO_ORGANISM_PART.getName(), 0));

                    List<Tuple<String, Integer>> diseases = estimateDatasetsByTermInSampleDescription(submissions.stream(), CvTermReference.EFO_DISEASE);

                    if(organismsPart.stream().noneMatch(x -> x.getKey().equalsIgnoreCase(CvTermReference.PRIDE_NO_DISEASES.getName())))
                        diseases.add(new Tuple<>(CvTermReference.PRIDE_NO_DISEASES.getName(), 0));

                    List<Tuple<String, Integer>> modifications = submissions.stream().flatMap( x-> x.getPtmList().stream())
                            .collect(Collectors.groupingBy(CvParam::getName))
                            .entrySet()
                            .stream()
                            .map( x -> new Tuple<>(x.getKey(), x.getValue().size()))
                            .sorted((x,y) -> y.getValue().compareTo(x.getValue()))
                            .collect(Collectors.toList());
                    modifications.add(new Tuple<>(CvTermReference.PRIDE_NO_MODIFICATION.getName(), 0));

                    for(int iOrg = 0; iOrg < organisms.size(); iOrg++){
                        List<MongoPrideProject> currentOrganism = filterProjectsByValueTerm(submissions, CvTermReference.EFO_ORGANISM, organisms.get(iOrg).getKey(), false);
                        for(int iOrgPart = 0; (iOrgPart < organismsPart.size() && currentOrganism.size()>0); iOrgPart++){
                            List<MongoPrideProject> currentOrganismPart = new ArrayList<>();
                            if(organismsPart.get(iOrgPart).getKey().equalsIgnoreCase(CvTermReference.PRIDE_NO_ORGANISM_PART.getName()))
                                currentOrganismPart = filterProjectsByValueTerm(currentOrganism, CvTermReference.EFO_ORGANISM_PART, organismsPart.get(iOrgPart).getKey(), true);
                            else
                                currentOrganismPart = filterProjectsByValueTerm(currentOrganism, CvTermReference.EFO_ORGANISM_PART, organismsPart.get(iOrgPart).getKey(), false);
                            for(int iDiseases = 0; (iDiseases < diseases.size() && currentOrganismPart.size() > 0); iDiseases++){
                                List<MongoPrideProject> currentDiseases;
                                if(organismsPart.get(iOrgPart).getKey().equalsIgnoreCase(CvTermReference.PRIDE_NO_DISEASES.getName()))
                                    currentDiseases = filterProjectsByValueTerm(currentOrganismPart, CvTermReference.EFO_DISEASE, diseases.get(iDiseases).getKey(), true);
                                else
                                     currentDiseases = filterProjectsByValueTerm(currentOrganismPart, CvTermReference.EFO_DISEASE, diseases.get(iDiseases).getKey() ,false);
                                for(int iMod = 0; (iMod < modifications.size() && currentDiseases.size() >0); iMod++){
                                    int finalIMod = iMod;
                                    long count = currentDiseases.parallelStream()
                                            .filter(mongoPrideProject -> mongoPrideProject.getPtmList()
                                                    .parallelStream().anyMatch(ptm -> ptm.getName().equalsIgnoreCase(modifications.get(finalIMod).getKey()))).count();

                                    if(count > 0){
                                        log.info(organisms.get(iOrg).getKey() + " | " + organismsPart.get(iOrgPart).getKey()
                                                + " | " + diseases.get(iDiseases).getKey() + "|" + modifications.get(finalIMod).getKey() + " | " + String.valueOf(count));
                                        categoryStats = addCategories(categoryStats, count, organisms.get(iOrg).getKey(),
                                                organismsPart.get(iOrgPart).getKey(), diseases.get(iDiseases).getKey(),
                                                modifications.get(finalIMod).getKey());
                                    }

                                }
                            }
                        }
                    }
                    categoryStats = computeCategoryStats(categoryStats);
                    prideStatsMongoService.updateSubmissionComplexStats(date, PrideStatsKeysConstants.SUBMISSIONS_PER_CATEGORIES, categoryStats);
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    private List<MongoPrideProject> filterProjectsByValueTerm(List<MongoPrideProject> submissions, CvTermReference term, String key, boolean addEmpty) {
        List<MongoPrideProject> resultSubmissions = new ArrayList<>();
        if(addEmpty){
            resultSubmissions.addAll(filterProjectsByEmptyValue(submissions, term));
        }
        resultSubmissions.addAll(submissions.stream().filter(a -> {
            List<Tuple<CvParam, Set<CvParam>>> descriptionValues = a.getSamplesDescription().stream()
                    .filter(keyDesc -> keyDesc.getKey().getAccession().equalsIgnoreCase(term.getAccession()))
                    .collect(Collectors.toList());
            boolean found = false;
            Iterator<Tuple<CvParam, Set<CvParam>>> it = descriptionValues.iterator();
            while(it.hasNext() && !found){
                Tuple<CvParam, Set<CvParam>> terms = it.next();
                found = terms.getValue().stream().anyMatch(value -> value.getName().equalsIgnoreCase(key));
            }
            return found;
        }).collect(Collectors.toList()));

        return resultSubmissions;
    }

    private List<MongoPrideProject> filterProjectsByEmptyValue(List<MongoPrideProject> submissions, CvTermReference term) {
        return submissions.stream().filter(a -> a.getSamplesDescription().
                stream().noneMatch(keyDesc -> keyDesc.getKey().getAccession().equalsIgnoreCase(term.getAccession()))).collect(Collectors.toList());
    }

    private Set<CategoryStats> computeCategoryStats(Set<CategoryStats> categoryStats) {
        for(CategoryStats category: categoryStats){
            category = computeCategoryCount(category);
            categoryStats.add(category);
        }
        return categoryStats;
    }

    private CategoryStats computeCategoryCount(CategoryStats category) {
        if(category.getSubCategories() == null || category.getSubCategories().isEmpty())
            return category;
        int count = 0;
        for(CategoryStats subCategory: Objects.requireNonNull(category.getSubCategories())){
            count = count + computeCategoryCount(subCategory).getCategory().getValue();
        }
        category.getCategory().setValue(count);
        return category;
    }

    private Set<CategoryStats> addCategoryStats(Collection<CategoryStats> categories, int count, String... keys){
        if(keys.length == 1 ){
            Set<CategoryStats> subCategories = new HashSet<>();
            CategoryStats current = (categories.stream().anyMatch(x -> x.getCategory().getKey().equalsIgnoreCase(keys[0])))?
                    categories.stream().filter(x -> x.getCategory().getKey().equalsIgnoreCase(keys[0])).findFirst().get():
                    CategoryStats.builder().category(new Tuple<>(keys[0], 0))
                            .subCategories(subCategories)
                            .build();
            current.getCategory().setValue(current.getCategory().getValue() + count);
            categories.add(current);
        }else if (keys.length > 1){
            CategoryStats current = (categories.stream().anyMatch(x -> x.getCategory().getKey().equalsIgnoreCase(keys[0])))?
                    categories.stream().filter(x -> x.getCategory().getKey().equalsIgnoreCase(keys[0])).findFirst().get():
                    CategoryStats.builder()
                            .category(new Tuple<>(keys[0], 0))
                            .subCategories(new HashSet<>())
                            .build();
            current.setSubCategories(addCategoryStats(current.getSubCategories(), count, Arrays.copyOfRange(keys, 1, keys.length)));
            categories.add(current);
        }
        return new HashSet<>(categories);

    }

    private Set<CategoryStats> addCategories(Set<CategoryStats> categoryStats, long count, String ... keys) {
        return addCategoryStats(categoryStats, (int) count, keys);
    }




    /**
     * This job estimates different statistics around each submission.
     *
     * @return the calculatePrideArchiveDataUsage job
     */
    @Bean
    public Job computeSubmissionStatsJob() {
        return jobBuilderFactory
                .get(SubmissionPipelineConstants.PrideArchiveJobNames.PRIDE_ARCHIVE_SUBMISSION_STATS.getName())
                .start(estimateSubmissionByCategoryStep())
                .start(estimateSubmissionByYearStep())
                .next(estimateSubmissionByCategoryStep())
                .next(estimateSubmissionByMonthStep())
                .next(estimateInstrumentsCountStep())
                .next(estimateOrganismCountStep())
                .next(estimateModificationCountStep())
                .next(estimateOrganismPartCountStep())
                .next(estimateDiseasesCountStep())
                .next(estimateCountryCountStep())
                .next(computeMoleculesStats())
                .build();
    }



    /**
     * Estimate the number of datasets for an specific {@link CvTermReference} in the sample Description.
     * @param projects PRIDE projects
     * @param term {@link CvTermReference}
     * @return List of Tuple with the values.
     */
    private List<Tuple<String, Integer>> estimateDatasetsByTermInSampleDescription(Stream<MongoPrideProject> projects, CvTermReference term){
        return projects.map(MongoPrideProject::getSamplesDescription)
                .collect(Collectors.toList())
                .stream().flatMap(Collection::stream)
                .collect(Collectors.toList())
                .stream()
                .filter(c -> c.getKey().getAccession().equalsIgnoreCase(term.getAccession()))
                .map(Tuple::getValue)
                .collect(Collectors.toList())
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(CvParam::getName))
                .entrySet()
                .stream()
                .map(f -> new Tuple<>(f.getKey(), f.getValue().size()))
                .sorted((x,y) -> y.getValue().compareTo(x.getValue()))
                .collect(Collectors.toList());
    }


}
