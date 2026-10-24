package com.devkanyanta.pos.supplier;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<SupplierEntity, Long>, JpaSpecificationExecutor<SupplierEntity> {

    Optional<SupplierEntity> findByNameIgnoreCase(String name);


}
