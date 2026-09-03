package com.stockflow.warehouse;

import com.stockflow.common.api.PageResponse;
import com.stockflow.common.exception.DuplicateResourceException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.warehouse.dto.WarehouseCreateRequest;
import com.stockflow.warehouse.dto.WarehouseResponse;
import com.stockflow.warehouse.dto.WarehouseUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseService.class);

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional
    public WarehouseResponse createWarehouse(WarehouseCreateRequest request) {
        if (warehouseRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Warehouse", "code", request.getCode());
        }

        Warehouse warehouse = new Warehouse(
                request.getCode().trim().toUpperCase(),
                request.getName().trim(),
                request.getAddress() != null ? request.getAddress().trim() : null
        );

        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Created warehouse '{}' with code '{}'", saved.getName(), saved.getCode());
        return WarehouseResponse.fromEntity(saved);
    }

    @Transactional
    public WarehouseResponse updateWarehouse(Long id, WarehouseUpdateRequest request) {
        Warehouse warehouse = findEntityById(id);

        warehouse.setName(request.getName().trim());
        warehouse.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        warehouse.setActive(request.getActive());

        Warehouse updated = warehouseRepository.save(warehouse);
        log.info("Updated warehouse ID {} (code: '{}')", updated.getId(), updated.getCode());
        return WarehouseResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseById(Long id) {
        return WarehouseResponse.fromEntity(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseByCode(String code) {
        Warehouse warehouse = warehouseRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "code", code));
        return WarehouseResponse.fromEntity(warehouse);
    }

    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> getAllWarehouses(Pageable pageable) {
        Page<Warehouse> page = warehouseRepository.findAll(pageable);
        return PageResponse.of(page, page.getContent().stream().map(WarehouseResponse::fromEntity).toList());
    }

    @Transactional(readOnly = true)
    public Warehouse findEntityById(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "ID", id));
    }
}
