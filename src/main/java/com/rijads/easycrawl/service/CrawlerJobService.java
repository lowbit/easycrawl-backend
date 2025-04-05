package com.rijads.easycrawl.service;

import com.rijads.easycrawl.dto.CrawlerErrorDTO;
import com.rijads.easycrawl.dto.CrawlerJobDTO;
import com.rijads.easycrawl.mapper.CrawlerJobMapper;
import com.rijads.easycrawl.mapper.CrawlerRawMapper;
import com.rijads.easycrawl.model.CrawlerConfig;
import com.rijads.easycrawl.model.CrawlerJob;
import com.rijads.easycrawl.model.CrawlerRaw;
import com.rijads.easycrawl.repository.CrawlerConfigRepository;
import com.rijads.easycrawl.repository.CrawlerErrorRepository;
import com.rijads.easycrawl.repository.CrawlerJobRepository;
import com.rijads.easycrawl.repository.CrawlerRawRepository;
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


@Service
public class CrawlerJobService {
    private final Logger logger = LoggerFactory.getLogger(CrawlerJobService.class);
    private final CrawlerJobRepository repository;
    private final CrawlerErrorRepository crawlerErrorRepository;
    private final CrawlerConfigRepository crawlerConfigRepository;
    private final CrawlerJobMapper crawlerJobMapper;

    public CrawlerJobService(CrawlerJobRepository repository, CrawlerErrorRepository crawlerErrorRepository,
                             CrawlerJobMapper crawlerJobMapper, CrawlerConfigRepository crawlerConfigRepository ) {
        this.repository = repository;
        this.crawlerErrorRepository = crawlerErrorRepository;
        this.crawlerJobMapper = crawlerJobMapper;
        this.crawlerConfigRepository = crawlerConfigRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void createScheduledJobs(){
        logger.info("Starting scheduled job creation");
        List<CrawlerConfig> configs = crawlerConfigRepository.findAllByAutoScheduleIsTrue();
        for(CrawlerConfig config : configs){
            CrawlerJob latestJob = repository.findFirstByConfigCodeWhereTestrunIsFalseandStatusIsFinished(config.getCode());
            LocalDateTime now = LocalDateTime.now();
            if(latestJob == null || ChronoUnit.HOURS.between(latestJob.getCreated(),now)+1 >= config.getAutoScheduleEvery()){
                CrawlerJob job = new CrawlerJob();
                job.setStatus("Created");
                job.setCreatedBy("Scheduler");
                job.setCreated(LocalDateTime.now());
                job.setConfig(config);
                job.setCrawlerWebsite(config.getCrawlerWebsite());
                job.setTestRun(false);
                repository.save(job);
                logger.info("Created job: " + job.getId());
            }
        }

    }

    public Page<CrawlerJobDTO> getAllCrawlerJobs(final Pageable page){
        Specification<CrawlerJob> spec = (root, query, criteriaBuilder) -> null;
        Page<CrawlerJob> resEntities = repository.findAll(spec,page);
        return resEntities.map(crawlerJobMapper::toDto);
    }

    public List<CrawlerErrorDTO> getAllCrawlerJobErrors(Long id) {
        return crawlerErrorRepository.findAllByJob_Id(Math.toIntExact(id)).stream().map(crawlerJobMapper::errorToDto).toList();
    }

    public CrawlerJobDTO create(CrawlerJobDTO crawlerJobDTO) {
        CrawlerJob entity = crawlerJobMapper.toEntity(crawlerJobDTO);
        entity.setCreated(LocalDateTime.now());
        entity.setCreatedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        entity.setStatus("Created");
        return crawlerJobMapper.toDto(repository.save(entity));
    }

    public CrawlerJobDTO getCrawlJobById(String id) {
        Optional<CrawlerJob> entity = repository.findById(id);
        return entity.map(crawlerJobMapper::toDto).orElse(null);
    }
}
