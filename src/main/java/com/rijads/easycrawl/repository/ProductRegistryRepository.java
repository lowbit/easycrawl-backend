package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.ProductRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRegistryRepository extends CrudRepository<ProductRegistry, Integer>, PagingAndSortingRepository<ProductRegistry, Integer>, JpaSpecificationExecutor<ProductRegistry> {

    /**
     * Find registry entries by type and enabled status
     */
    List<ProductRegistry> findByRegistryTypeAndEnabledTrue(ProductRegistry.RegistryType registryType);

    /**
     * Find registry entries by key containing the search term
     */
    List<ProductRegistry> findByRegistryKeyContainingIgnoreCase(String searchTerm);

    /**
     * Find registry entries by key and type
     */
    List<ProductRegistry> findByRegistryKeyAndRegistryType(String registryKey, ProductRegistry.RegistryType registryType);

    /**
     * Check if a registry entry exists with the given key and type
     */
    boolean existsByRegistryKeyIgnoreCaseAndRegistryType(String registryKey, ProductRegistry.RegistryType registryType);
    List<ProductRegistry> findByRegistryType(ProductRegistry.RegistryType registryType);
}
