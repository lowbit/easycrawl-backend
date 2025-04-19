package com.rijads.easycrawl.service;

import com.rijads.easycrawl.dto.JobDTO;
import com.rijads.easycrawl.dto.JobErrorDTO;
import com.rijads.easycrawl.mapper.JobMapper;
import com.rijads.easycrawl.model.CrawlerConfig;
import com.rijads.easycrawl.model.Job;
import com.rijads.easycrawl.repository.CrawlerConfigRepository;
import com.rijads.easycrawl.repository.JobErrorRepository;
import com.rijads.easycrawl.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing all types of jobs (crawler, product mapping, cleanup, etc.)
 */
@Service
public class JobService {
    private final Logger logger = LoggerFactory.getLogger(JobService.class);
    private final JobRepository repository;
    private final CrawlerConfigRepository configRepository;
    private final JobErrorRepository jobErrorRepository;
    private final JobMapper jobMapper;

    public JobService(JobRepository repository, JobErrorRepository jobErrorRepository,
                      JobMapper jobMapper, CrawlerConfigRepository configRepository) {
        this.repository = repository;
        this.jobErrorRepository = jobErrorRepository;
        this.jobMapper = jobMapper;
        this.configRepository = configRepository;
    }

    /**
     * Creates scheduled jobs for all job types
     */
    @Scheduled(cron = "0 10 * * * *")
    public void createScheduledJobs() {
        logger.info("Starting scheduled job creation");

        // Create scheduled crawler jobs
        createScheduledCrawlerJobs();

        // Create scheduled product mapping jobs
        createScheduledProductMappingJobs();

        // Create scheduled product cleanup jobs

        // Weekly product cleanup job
        LocalDateTime now = LocalDateTime.now();
        if (now.getDayOfWeek().getValue() == 1 && now.getHour() == 2) { // Monday at 2 AM
            createScheduledProductCleanupJobs();
        }
    }

    /**
     * Creates scheduled crawler jobs
     */
    private void createScheduledCrawlerJobs() {
        // Existing logic for crawler jobs
        List<CrawlerConfig> configs =  configRepository.findAllByAutoScheduleIsTrue();
        for(CrawlerConfig config : configs) {
            Job latestJob = repository.findFirstByConfigCodeAndJobTypeWhereTestrunIsFalseandStatusIsFinished(
                    config.getCode(), "CRAWL");
            LocalDateTime now = LocalDateTime.now();
            if(latestJob == null || ChronoUnit.HOURS.between(latestJob.getCreated(), now) + 1 >= config.getAutoScheduleEvery()) {
                Job job = new Job();
                job.setStatus("Created");
                job.setCreatedBy("Scheduler");
                job.setCreated(LocalDateTime.now());
                job.setConfig(config);
                job.setCrawlerWebsite(config.getCrawlerWebsite());
                job.setTestRun(false);
                job.setJobType("CRAWL");
                repository.save(job);
                logger.info("Created crawler job: " + job.getId());
            }
        }
    }

    /**
     * Creates scheduled product mapping jobs
     */
    private void createScheduledProductMappingJobs() {
        // Product mapping scheduling logic
        Job latestJob = repository.findFirstByConfigCodeAndJobTypeWhereTestrunIsFalseandStatusIsFinished(
                null, "PRODUCT_MAPPING");
        LocalDateTime now = LocalDateTime.now();
        if(latestJob == null || ChronoUnit.HOURS.between(latestJob.getCreated(), now) >= 1) {
            Job job = new Job();
            job.setStatus("Created");
            job.setCreatedBy("Scheduler");
            job.setCreated(LocalDateTime.now());
            job.setTestRun(false);
            job.setJobType("PRODUCT_MAPPING");
            repository.save(job);
            logger.info("Created product mapping job: " + job.getId());
        }
    }

    /**
     * Creates scheduled product cleanup jobs
     */
    private void createScheduledProductCleanupJobs() {
        Job job = new Job();
        job.setStatus("Created");
        job.setCreatedBy("Scheduler");
        job.setCreated(LocalDateTime.now());
        job.setTestRun(false);
        job.setJobType("PRODUCT_CLEANUP");
        repository.save(job);
        logger.info("Created product cleanup job: " + job.getId());
    }

    /**
     * Get all jobs with pagination
     */
    public Page<JobDTO> getAllJobs(final Pageable page) {
        Specification<Job> spec = (root, query, criteriaBuilder) -> null;
        Page<Job> resEntities = repository.findAll(spec, page);
        return resEntities.map(jobMapper::toDto);
    }

    /**
     * Get all errors for a specific job
     */
    public List<JobErrorDTO> getAllJobErrors(Long id) {
        return jobErrorRepository.findAllByJob_Id(Math.toIntExact(id))
                .stream()
                .map(jobMapper::errorToDto)
                .toList();
    }

    /**
     * Create a new job
     */
    public JobDTO create(JobDTO jobDTO) {
        Job entity = jobMapper.toEntity(jobDTO);
        entity.setCreated(LocalDateTime.now());

        // Get current authenticated user or use system if none
        String username = "SYSTEM";
        try {
            username = SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            logger.warn("Could not get authenticated user, using SYSTEM");
        }

        entity.setCreatedBy(username);
        entity.setStatus("Created");
        return jobMapper.toDto(repository.save(entity));
    }

    /**
     * Get a job by ID
     */
    public JobDTO getJobById(String id) {
        Optional<Job> entity = repository.findById(id);
        return entity.map(jobMapper::toDto).orElse(null);
    }

    /**
     * Get jobs by type
     */
    public Page<JobDTO> getJobsByType(String jobType, final Pageable page) {
        Page<Job> jobs = repository.findByJobType(jobType, page);
        return jobs.map(jobMapper::toDto);
    }
}