package org.nors.dev.codes.lpu.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.nors.dev.codes.lpu.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void statusErrorClearsPresetVideoContentType() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/videos/clip.mp4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("video/mp4");

        ResponseEntity<ApiError> entity = handler.handleStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"),
                request,
                response
        );

        assertEquals(HttpStatus.NOT_FOUND, entity.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, entity.getHeaders().getContentType());
        assertEquals("Video not found", entity.getBody().message());
        assertNull(response.getContentType());
    }

    @Test
    void committedMediaResponseIsLeftAlone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/videos/clip.mp4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("video/mp4");
        response.getWriter().write("partial");
        response.flushBuffer();
        assertTrue(response.isCommitted());

        ResponseEntity<ApiError> entity = handler.handleGeneric(new IOException("disk read failed"), request, response);

        assertNull(entity);
        assertEquals("video/mp4", response.getContentType());
    }

    @Test
    void clientDisconnectDoesNotWriteApiError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/videos/clip.mp4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("video/mp4");

        ResponseEntity<ApiError> entity = handler.handleGeneric(new IOException("Broken pipe"), request, response);

        assertNull(entity);
        assertEquals("video/mp4", response.getContentType());
    }
}
