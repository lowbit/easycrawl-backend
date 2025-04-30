package com.rijads.easycrawl.mapper;

import com.rijads.easycrawl.dto.JobDTO;
import com.rijads.easycrawl.dto.JobErrorDTO;
import com.rijads.easycrawl.model.Job;
import com.rijads.easycrawl.model.JobError;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for Job and JobError entities
 */
@Mapper(componentModel = "spring")
public interface JobMapper {
    @Mapping(source = "crawlerWebsite.code", target = "crawlerWebsiteCode")
    @Mapping(source = "config.code", target = "crawlerConfigCode")
    JobDTO toDto(Job entity);

    @Mapping(source = "crawlerWebsiteCode", target = "crawlerWebsite.code")
    @Mapping(source = "crawlerConfigCode", target = "config.code")
    Job toEntity(JobDTO dto);
    Job toEntityWithoutObjects(JobDTO dto);

    @Mapping(source = "job.id", target = "jobId")
    JobErrorDTO errorToDto(JobError entity);

    @Mapping(source = "jobId", target = "job.id")
    JobError errorToEntity(JobErrorDTO dto);
}