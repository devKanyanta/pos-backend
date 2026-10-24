package com.devkanyanta.pos.supplier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.purchasing.PurchaseOrderRepository;
import com.devkanyanta.pos.purchasing.SupplierReturnRepository;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.ConflictException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.util.Texts;
import com.devkanyanta.pos.shared.web.PageResponse;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupplierService {

    public record SupplierRequest(String name, String contactPerson, String phone, String email,
                                  String address, String tpin, String notes, Boolean active) {
    }

    public record SupplierResponse(Long id, String name, String contactPerson, String phone, String email,
                                   String address, String tpin, String notes, boolean active) {
    }

    private static final Map<String, String> SORTS = Map.of("name", "name", "createdAt", "createdAt");

    private final SupplierRepository supplierRepository;

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final SupplierReturnRepository supplierReturnRepository;

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        String name = request == null ? null : Texts.clean(request.name());
        if (name == null) {
            throw new BadRequestException("Supplier name is required");
        }
        ensureNameFree(name, null);

        SupplierEntity supplier = SupplierEntity.builder().name(name).build();
        apply(supplier, request);
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> search(String q, Boolean active, Integer page, Integer size, String sort) {
        String like = Texts.clean(q) == null ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Specification<SupplierEntity> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (active != null) {
                all.add(active ? cb.or(cb.isNull(root.get("active")), cb.isTrue(root.get("active")))
                        : cb.isFalse(root.get("active")));
            }
            if (like != null) {
                all.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("contactPerson"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("phone"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("email"), "")), like)));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
        return PageResponse.of(
                supplierRepository.findAll(spec,
                        PageResponse.pageable(page, size, sort, SORTS, Sort.by("name").and(Sort.by("id")))),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        SupplierEntity supplier = find(id);
        if (request == null) {
            throw new BadRequestException("Supplier request is required");
        }
        if (request.name() != null) {
            String name = Texts.clean(request.name());
            if (name == null) {
                throw new BadRequestException("Supplier name cannot be blank");
            }
            ensureNameFree(name, id);
            supplier.setName(name);
        }
        apply(supplier, request);
        return toResponse(supplierRepository.save(supplier));
    }

    /** Suppliers with purchasing history are deactivated instead of deleted. */
    @Transactional
    public void delete(Long id) {
        SupplierEntity supplier = find(id);
        if (purchaseOrderRepository.existsBySupplierId(id) || supplierReturnRepository.existsBySupplierId(id)) {
            supplier.setActive(false);
            supplierRepository.save(supplier);
            return;
        }
        supplierRepository.delete(supplier);
    }

    /** The supplier, which must exist; used by purchasing. */
    public SupplierEntity find(Long id) {
        return supplierRepository.findById(id).orElseThrow(() -> new NotFoundException("Supplier not found"));
    }

    private void ensureNameFree(String name, Long exceptId) {
        supplierRepository.findByNameIgnoreCase(name).filter(s -> !s.getId().equals(exceptId)).ifPresent(s -> {
            throw new ConflictException("A supplier named '" + s.getName() + "' already exists");
        });
    }

    private void apply(SupplierEntity supplier, SupplierRequest request) {
        if (request.contactPerson() != null) {
            supplier.setContactPerson(Texts.truncate(Texts.clean(request.contactPerson()), 255));
        }
        if (request.phone() != null) {
            supplier.setPhone(Texts.truncate(Texts.clean(request.phone()), 30));
        }
        if (request.email() != null) {
            String email = Texts.clean(request.email());
            if (email != null && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                throw new BadRequestException("Supplier email is not valid");
            }
            supplier.setEmail(email);
        }
        if (request.address() != null) {
            supplier.setAddress(Texts.truncate(Texts.clean(request.address()), 500));
        }
        if (request.tpin() != null) {
            String tpin = Texts.clean(request.tpin());
            if (tpin != null && !tpin.matches("\\d{10}")) {
                throw new BadRequestException("TPIN must be a 10-digit numeric value");
            }
            supplier.setTpin(tpin);
        }
        if (request.notes() != null) {
            supplier.setNotes(Texts.truncate(Texts.clean(request.notes()), 1000));
        }
        if (request.active() != null) {
            supplier.setActive(request.active());
        }
    }

    private SupplierResponse toResponse(SupplierEntity s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getContactPerson(), s.getPhone(), s.getEmail(),
                s.getAddress(), s.getTpin(), s.getNotes(), !Boolean.FALSE.equals(s.getActive()));
    }
}
