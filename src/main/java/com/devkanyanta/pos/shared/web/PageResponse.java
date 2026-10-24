package com.devkanyanta.pos.shared.web;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.devkanyanta.pos.shared.exception.BadRequestException;

/** Standard envelope for paginated lists. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /**
     * Builds a {@link Pageable} from request parameters. {@code sort} is {@code field} or
     * {@code field,asc|desc} and must be one of {@code allowedSorts} (public name -> entity property),
     * so clients can never sort by arbitrary columns.
     */
    public static Pageable pageable(Integer page, Integer size, String sort,
                                    Map<String, String> allowedSorts, Sort defaultSort) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
        Sort resolved = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            String property = allowedSorts.get(parts[0].trim());
            if (property == null) {
                throw new BadRequestException("Cannot sort by '" + parts[0].trim() + "'. Allowed: "
                        + String.join(", ", allowedSorts.keySet()));
            }
            Sort.Direction direction = Sort.Direction.ASC;
            if (parts.length > 1) {
                String d = parts[1].trim().toLowerCase();
                if (d.equals("desc")) {
                    direction = Sort.Direction.DESC;
                } else if (!d.equals("asc")) {
                    throw new BadRequestException("Sort direction must be asc or desc");
                }
            }
            resolved = Sort.by(direction, property).and(Sort.by("id"));
        }
        return PageRequest.of(pageNumber, pageSize, resolved);
    }
}
