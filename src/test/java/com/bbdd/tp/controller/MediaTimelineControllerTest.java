package com.bbdd.tp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MediaTimelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testProvisionComponentManualV3() throws Exception {
        String jsonPayload = """
                {
                    "componentId": "%s",
                    "trackId": "%s",
                    "assetId": "%s",
                    "eventRateNumerator": 24000,
                    "eventRateDenominator": 1001,
                    "xSize": 1920,
                    "ySize": 1080,
                    "algorithmName": "video_face_detection"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/3.0/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(content().string("Manual JDBC/Mongo Transaction complete: Atomic dual-engine insert succeeded."));
    }
}