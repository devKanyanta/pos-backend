package com.devkanyanta.pos.purchasing;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierReturnRepository extends JpaRepository<SupplierReturnEntity, Long>, JpaSpecificationExecutor<SupplierReturnEntity> {

    Optional<SupplierReturnEntity> findByRequestId(String requestId);

    boolean existsBySupplierId(Long supplierId);


}
