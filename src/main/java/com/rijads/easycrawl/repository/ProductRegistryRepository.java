package com.rijads.easycrawl.repository;

import com.rijads.easycrawl.model.ProductRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRegistryRepository extends JpaRepository<ProductRegistry, Integer> {

    List<ProductRegistry> findByRegistryTypeAndEnabledTrue(ProductRegistry.RegistryType type);

    List<ProductRegistry> findByRegistryTypeInAndEnabledTrue(List<ProductRegistry.RegistryType> types);
}
