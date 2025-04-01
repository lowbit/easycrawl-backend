package com.rijads.easycrawl;

import com.rijads.easycrawl.controller.CrawlerConfigController;
import com.rijads.easycrawl.dto.CrawlerConfigDTO;
import com.rijads.easycrawl.dto.DropdownDTO;
import com.rijads.easycrawl.service.CrawlerConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrawlerConfigControllerTest {

    @Mock
    private CrawlerConfigService service;

    @InjectMocks
    private CrawlerConfigController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAllCrawlerConfigs_Success() {
        // Arrange
        String website = "example.com";
        String category = "Electronics";
        LocalDateTime createdFrom = LocalDateTime.now().minusDays(1);
        LocalDateTime createdTo = LocalDateTime.now();
        Pageable pageable = mock(Pageable.class);

        Page<CrawlerConfigDTO> expectedPage = new PageImpl<>(Arrays.asList(new CrawlerConfigDTO(), new CrawlerConfigDTO()));
        when(service.getAllCrawlerConfigs(website, category, createdFrom, createdTo, pageable)).thenReturn(expectedPage);

        // Act
        Page<CrawlerConfigDTO> resultPage = controller.getAllCrawlerConfigs(website, category, createdFrom, createdTo, pageable);

        // Assert
        assertNotNull(resultPage);
        assertEquals(2, resultPage.getNumberOfElements());
        verify(service, times(1)).getAllCrawlerConfigs(website, category, createdFrom, createdTo, pageable);
    }

    @Test
    void testGetAllWebsitesDropdown_Success() {
        // Arrange
        List<DropdownDTO> expectedDropdowns = Arrays.asList(new DropdownDTO(), new DropdownDTO());
        when(service.getAllWebsitesDropdown()).thenReturn(expectedDropdowns);

        // Act
        List<DropdownDTO> resultDropdowns = controller.getAllWebsitesDropdown();

        // Assert
        assertNotNull(resultDropdowns);
        assertEquals(2, resultDropdowns.size());
        verify(service, times(1)).getAllWebsitesDropdown();
    }

    @Test
    void testGetAllCategoriesDropdown_Success() {
        // Arrange
        List<DropdownDTO> expectedDropdowns = Arrays.asList(new DropdownDTO(), new DropdownDTO());
        when(service.getAllCategoriesDropdown()).thenReturn(expectedDropdowns);

        // Act
        List<DropdownDTO> resultDropdowns = controller.getAllCategoriesDropdown();

        // Assert
        assertNotNull(resultDropdowns);
        assertEquals(2, resultDropdowns.size());
        verify(service, times(1)).getAllCategoriesDropdown();
    }

    @Test
    void testGetAllCrawlerConfigsByWebsiteCodeDropdown_Success() {
        // Arrange
        String websiteCode = "EC";
        List<DropdownDTO> expectedDropdowns = Arrays.asList(new DropdownDTO(), new DropdownDTO());
        when(service.getAllCrawlerConfigsDropdown(websiteCode)).thenReturn(expectedDropdowns);

        // Act
        List<DropdownDTO> resultDropdowns = controller.getAllCrawlerConfigsByWebsiteCodeDropdown(websiteCode);

        // Assert
        assertNotNull(resultDropdowns);
        assertEquals(2, resultDropdowns.size());
        verify(service, times(1)).getAllCrawlerConfigsDropdown(websiteCode);
    }

    @Test
    void testAddCategory_Success() {
        // Arrange
        DropdownDTO request = new DropdownDTO();
        ResponseEntity<DropdownDTO> expectedResponse = ResponseEntity.ok(new DropdownDTO());

        when(service.addProductCategory(request)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<DropdownDTO> result = controller.addCategory(request);

        // Assert
        assertEquals(expectedResponse, result);
        verify(service, times(1)).addProductCategory(request);
    }

    @Test
    void testAddCrawlerConfig_Success() {
        // Arrange
        CrawlerConfigDTO request = new CrawlerConfigDTO();
        ResponseEntity<CrawlerConfigDTO> expectedResponse = ResponseEntity.ok(new CrawlerConfigDTO());

        when(service.addCrawlerConfig(request)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<CrawlerConfigDTO> result = controller.addCrawlerConfig(request);

        // Assert
        assertEquals(expectedResponse, result);
        verify(service, times(1)).addCrawlerConfig(request);
    }

    @Test
    void testEditCrawlerConfig_Success() {
        // Arrange
        String code = "CC001";
        CrawlerConfigDTO request = new CrawlerConfigDTO();
        ResponseEntity<CrawlerConfigDTO> expectedResponse = ResponseEntity.ok(new CrawlerConfigDTO());

        when(service.editCrawlerConfig(code, request)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<CrawlerConfigDTO> result = controller.editCrawlerConfig(code, request);

        // Assert
        assertEquals(expectedResponse, result);
        verify(service, times(1)).editCrawlerConfig(code, request);
    }

    @Test
    void testDeleteCrawlerConfig_Success() {
        // Arrange
        String code = "CC001";
        ResponseEntity<Void> expectedResponse = ResponseEntity.ok().build();

        when(service.deleteCrawlerConfig(code)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<Void> result = controller.deleteCrawlerConfig(code);

        // Assert
        assertEquals(expectedResponse, result);
        verify(service, times(1)).deleteCrawlerConfig(code);
    }

    @Test
    void testDeleteCrawlerConfigs_Success() {
        // Arrange
        List<String> codes = Arrays.asList("CC001", "CC002");
        ResponseEntity<Void> expectedResponse = ResponseEntity.ok().build();

        when(service.deleteCrawlerConfigs(codes)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<Void> result = controller.deleteCrawlerConfigs(codes);

        // Assert
        assertEquals(expectedResponse, result);
        verify(service, times(1)).deleteCrawlerConfigs(codes);
    }
}