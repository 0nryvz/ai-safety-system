package com.isg.backend.shared.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void usesIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        request.addHeader(CorrelationIdFilter.HEADER_NAME, "test-correlation-id");

        filter.doFilter(request, response, chain);

        assertEquals(
                "test-correlation-id",
                response.getHeader(CorrelationIdFilter.HEADER_NAME)
        );

        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String correlationId =
                response.getHeader(CorrelationIdFilter.HEADER_NAME);

        assertNotNull(correlationId);
        assertFalse(correlationId.isBlank());
        assertDoesNotThrow(() -> java.util.UUID.fromString(correlationId));

        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void clearsMdcAfterRequestCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
