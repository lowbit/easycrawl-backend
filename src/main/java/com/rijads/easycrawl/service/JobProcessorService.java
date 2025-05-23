package com.rijads.easycrawl.service;

import com.rijads.easycrawl.model.Job;
import com.rijads.easycrawl.model.JobError;
import com.rijads.easycrawl.repository.JobErrorRepository;
import com.rijads.easycrawl.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class JobProcessorService {
    private static final Logger logger = LoggerFactory.getLogger(JobProcessorService.class);

    private final JobRepository jobRepository;
    private final JobErrorRepository jobErrorRepository;
    private final ProductMatchingService productMatchingService;
    
    public JobProcessorService(
            JobRepository jobRepository,
            JobErrorRepository jobErrorRepository,
            ProductMatchingService productMatchingService) {
        this.jobRepository = jobRepository;
        this.jobErrorRepository = jobErrorRepository;
        this.productMatchingService = productMatchingService;
    }

    /**
     * Periodically check for and process non-crawler jobs
     */
    @Scheduled(fixedRate = 5000)
    public void processJobs() {
        try {
            // First check for product mapping jobs
            processNextJobOfType("PRODUCT_MAPPING");

            // Then check for cleanup jobs
            processNextJobOfType("PRODUCT_CLEANUP");
        } catch (Exception e) {
            logger.error("Error in job processing scheduler", e);
        }
    }

    /**
     * Process the next available job of a specific type
     */
    private void processNextJobOfType(String jobType) {
        // Find a job that's ready to process
        Job job = jobRepository.findNextAvailableJob(jobType);

        if (job != null) {
            try {
                // Mark job as running
                job.setStatus("Running");
                job.setStartedAt(LocalDateTime.now());
                jobRepository.save(job);

                // Process based on job type
                String resultDescription;
                if ("PRODUCT_MAPPING".equals(jobType)) {
                    resultDescription = processProductMappingJob(job);
                } else if ("PRODUCT_CLEANUP".equals(jobType)) {
                    resultDescription = "";
                } else {
                    throw new IllegalArgumentException("Unsupported job type: " + jobType);
                }

                // Mark job as completed
                job.setStatus("Finished");
                job.setFinishedAt(LocalDateTime.now());
                job.setDescription(resultDescription);
                jobRepository.save(job);

            } catch (Exception e) {
                // Handle error
                job.setStatus("Failed");
                job.setFinishedAt(LocalDateTime.now());
                job.setErrorMessage(e.getMessage());
                jobRepository.save(job);

                // Record detailed error
                createJobError(job, e);
                logger.error("Error processing {} job {}: {}", jobType, job.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Process a product mapping job and return a description of the results
     */
    private String processProductMappingJob(Job job) {
        StringBuilder description = new StringBuilder();

        // Get parameters
        String category = null;
        if (job.getParameters() != null && !job.getParameters().isEmpty()) {
            category = job.getParameters();
        } else if (job.getConfig() != null && job.getConfig().getProductCategory() != null) {
            category = job.getConfig().getProductCategory().getCode();
        }

        // Process products
        int newMappedProducts = 0;
        int updatedProducts = 0;

        if (category != null) {
            description.append("Processing products for category: ").append(category).append("\n");
            // Process with category filter
            newMappedProducts = productMatchingService.processItemsByCategory(category, job);
        } else {
            description.append("Processing all unmapped products\n");
            // Process all products
            newMappedProducts = productMatchingService.processAllUnmappedItems(job);
        }

        // Add results to description
        description.append("Results:\n");
        description.append("- New products mapped: ").append(newMappedProducts).append("\n");
        description.append("- Products updated: ").append(updatedProducts).append("\n");

        return description.toString();
    }

    /**
     * Create a job error record
     */
    private void createJobError(Job job, Exception e) {
        JobError error = new JobError();
        error.setJob(job);
        error.setSource(job.getCrawlerWebsite() != null ? job.getCrawlerWebsite().getCode() : "system");
        error.setCategory(job.getConfig() != null ? job.getConfig().getProductCategory().getCode() : "all");
        error.setJobType(job.getJobType());
        error.setError(e.getMessage());
        error.setCreated(LocalDateTime.now());
        jobErrorRepository.save(error);
    }
}