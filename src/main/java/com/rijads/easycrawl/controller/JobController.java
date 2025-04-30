package com.rijads.easycrawl.controller;

import com.rijads.easycrawl.dto.JobDTO;
import com.rijads.easycrawl.dto.JobErrorDTO;
import com.rijads.easycrawl.service.JobService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for managing all types of jobs (crawler, product mapping, cleanup)
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Get all jobs with pagination
     */
    @GetMapping
    public Page<JobDTO> getAllJobs(@RequestParam(required = false) final String jobType,
                                   @RequestParam(required = false) final String status,
                                   @RequestParam(required = false) final String crawlerConfigCode,
                                   final Pageable page) {
        return jobService.getAllJobs(jobType, status, crawlerConfigCode, page);
    }

    /**
     * Get jobs by type with pagination
     */
    @GetMapping("/type/{jobType}")
    public Page<JobDTO> getJobsByType(@PathVariable String jobType, final Pageable page) {
        return jobService.getJobsByType(jobType, page);
    }

    /**
     * Get a job by ID
     */
    @GetMapping("/{id}")
    public JobDTO getJobById(@PathVariable final String id){
        return jobService.getJobById(id);
    }

    /**
     * Get all errors for a specific job
     */
    @GetMapping("/{id}/errors")
    public List<JobErrorDTO> getJobErrors(@PathVariable Long id) {
        return jobService.getAllJobErrors(id);
    }

    /**
     * Create a new job
     */
    @PostMapping
    public JobDTO createJob(@RequestBody JobDTO jobDTO) {
        return jobService.create(jobDTO);
    }

    /**
     * Trigger manual creation of scheduled jobs
     */
    @PostMapping("/schedule")
    public ResponseEntity<Map<String, Object>> triggerScheduledJobs() {
        jobService.createScheduledJobs();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Scheduled jobs creation triggered"
        ));
    }

    /**
     * Trigger crawler job creation for a specific config
     */
    @PostMapping("/crawl/{configCode}")
    public ResponseEntity<JobDTO> createCrawlerJob(
            @PathVariable String configCode,
            @RequestParam(defaultValue = "false") boolean testRun) {

        JobDTO jobDTO = new JobDTO();
        jobDTO.setCrawlerConfigCode(configCode);
        jobDTO.setTestRun(testRun);
        jobDTO.setJobType("CRAWL");

        JobDTO createdJob = jobService.create(jobDTO);
        return ResponseEntity.ok(createdJob);
    }

    /**
     * Trigger product mapping job creation for a specific config or category
     */
    @PostMapping("/product-mapping/{configOrCategory}")
    public ResponseEntity<JobDTO> createProductMappingJob(
            @PathVariable String configOrCategory,
            @RequestParam(required = false) String parameters) {

        JobDTO jobDTO = new JobDTO();

        // Check if configOrCategory is a config code or just a category
        if (configOrCategory.contains("/")) {
            jobDTO.setCrawlerConfigCode(configOrCategory);
        } else {
            // Create a job with just parameters for the category
            jobDTO.setParameters(parameters != null ? parameters : configOrCategory);
        }

        jobDTO.setJobType("PRODUCT_MAPPING");

        JobDTO createdJob = jobService.create(jobDTO);
        return ResponseEntity.ok(createdJob);
    }

    /**
     * Trigger product cleanup job creation
     */
    @PostMapping("/product-cleanup")
    public ResponseEntity<JobDTO> createProductCleanupJob(
            @RequestParam(required = false) String parameters) {

        JobDTO jobDTO = new JobDTO();
        jobDTO.setJobType("PRODUCT_CLEANUP");
        jobDTO.setParameters(parameters);

        JobDTO createdJob = jobService.create(jobDTO);
        return ResponseEntity.ok(createdJob);
    }
}