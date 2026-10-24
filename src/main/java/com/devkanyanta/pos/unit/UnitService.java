package com.devkanyanta.pos.unit;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.ConflictException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.util.Texts;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnitService {

    public record UnitRequest(String code, String name) {
    }

    public record UnitResponse(Long id, String code, String name) {
    }

    private final UnitRepository unitRepository;

    private final ProductRepository productRepository;

    /** Common retail units, created once so a fresh install can assign units straight away. */
    @Bean
    public CommandLineRunner seedUnits() {
        return args -> {
            String[][] defaults = {{"PCS", "Piece"}, {"KG", "Kilogram"}, {"G", "Gram"}, {"L", "Litre"},
                    {"ML", "Millilitre"}, {"BOX", "Box"}, {"PACK", "Pack"}, {"BAG", "Bag"}, {"DOZ", "Dozen"}};
            for (String[] unit : defaults) {
                if (unitRepository.findByCode(unit[0]).isEmpty()) {
                    unitRepository.save(UnitEntity.builder().code(unit[0]).name(unit[1]).build());
                }
            }
        };
    }

    @Transactional
    public UnitResponse create(UnitRequest request) {
        String code = requireCode(request);
        String name = requireName(request);
        if (unitRepository.findByCode(code).isPresent()) {
            throw new ConflictException("Unit code already exists: " + code);
        }
        return toResponse(unitRepository.save(UnitEntity.builder().code(code).name(name).build()));
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> list() {
        return unitRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(UnitEntity::getCode))
                .map(this::toResponse).toList();
    }

    @Transactional
    public UnitResponse update(Long id, UnitRequest request) {
        UnitEntity unit = find(id);
        if (request != null && request.code() != null) {
            String code = requireCode(request);
            unitRepository.findByCode(code).filter(u -> !u.getId().equals(id)).ifPresent(u -> {
                throw new ConflictException("Unit code already exists: " + code);
            });
            unit.setCode(code);
        }
        if (request != null && request.name() != null) {
            unit.setName(requireName(request));
        }
        return toResponse(unitRepository.save(unit));
    }

    @Transactional
    public void delete(Long id) {
        UnitEntity unit = find(id);
        if (productRepository.existsByUnitId(id)) {
            throw new ConflictException("Unit " + unit.getCode() + " is used by products and cannot be deleted");
        }
        unitRepository.delete(unit);
    }

    public UnitEntity find(Long id) {
        return unitRepository.findById(id).orElseThrow(() -> new NotFoundException("Unit not found"));
    }

    private static String requireCode(UnitRequest request) {
        String code = request == null ? null : Texts.clean(request.code());
        if (code == null || !code.toUpperCase(Locale.ROOT).matches("[A-Z0-9]{1,10}")) {
            throw new BadRequestException("Unit code is required: 1-10 letters or digits");
        }
        return code.toUpperCase(Locale.ROOT);
    }

    private static String requireName(UnitRequest request) {
        String name = request == null ? null : Texts.clean(request.name());
        if (name == null) {
            throw new BadRequestException("Unit name is required");
        }
        return Texts.truncate(name, 60);
    }

    private UnitResponse toResponse(UnitEntity unit) {
        return new UnitResponse(unit.getId(), unit.getCode(), unit.getName());
    }
}
