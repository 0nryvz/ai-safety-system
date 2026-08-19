package com.isg.backend.shared.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageResponseTest {

    @Test
    void storesPaginationMetadataAndContent() {
        List<String> content = List.of("first", "second");

        PageResponse<String> response = new PageResponse<>(
                content,
                1,
                2,
                10,
                5
        );

        assertEquals(content, response.content());
        assertEquals(1, response.page());
        assertEquals(2, response.size());
        assertEquals(10, response.totalElements());
        assertEquals(5, response.totalPages());
    }

    @Test
    void supportsEmptyPage() {
        PageResponse<String> response = new PageResponse<>(
                List.of(),
                0,
                20,
                0,
                0
        );

        assertEquals(List.of(), response.content());
        assertEquals(0, response.page());
        assertEquals(20, response.size());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
    }
}
