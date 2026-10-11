package com.mediconnect.helper;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Builds the Pageable for list endpoints from ?page=&size=&sort=field,direction.
 * Replaces CLRG's private buildPageable (which had the direction inverted: "desc" gave ASC).
 *  - page below 0 becomes 0, size is kept between 1 and 100
 *  - the sort field must be in the controller's allow-list (API name -> entity property path),
 *    so clients cannot sort by arbitrary columns or crash the query with a bad name
 *  - ties are broken by id so pages do not repeat or skip rows
 */
@Component
@RequiredArgsConstructor
public class PageableBuilder {

    public static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_FIELD = "id";

    private final MessageUtils messageUtils;

    public Pageable build(int page, int size, String[] sort, Map<String, String> allowedFields) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        String field = sort != null && sort.length > 0 && !sort[0].isBlank() ? sort[0].trim() : DEFAULT_FIELD;
        String property = allowedFields.get(field);
        if (property == null) {
            throw new BadRequestException(messageUtils.getMessage("sort.field.invalid", field, allowedFields.keySet()));
        }

        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && sort.length > 1) {
            String d = sort[1].trim();
            if (d.equalsIgnoreCase("asc")) {
                direction = Sort.Direction.ASC;
            } else if (!d.equalsIgnoreCase("desc")) {
                throw new BadRequestException(messageUtils.getMessage("sort.direction.invalid", d));
            }
        }

        Sort order = Sort.by(direction, property);
        if (!"id".equals(property)) {
            order = order.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return PageRequest.of(safePage, safeSize, order);
    }
}
