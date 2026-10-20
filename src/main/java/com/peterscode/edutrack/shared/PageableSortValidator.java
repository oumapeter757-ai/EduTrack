package com.peterscode.edutrack.shared;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class PageableSortValidator {
    private PageableSortValidator() {}

    public static Pageable validate(Pageable pageable, Set<String> allowedProperties) {
        pageable.getSort().forEach(order -> {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new IllegalArgumentException("Unsupported sort property: " + order.getProperty());
            }
        });
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
    }
}
