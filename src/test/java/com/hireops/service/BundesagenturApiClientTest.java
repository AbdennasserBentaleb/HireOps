package com.hireops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireops.dto.JobDetailResponse;
import com.hireops.dto.JobSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.springframework.context.annotation.Import;

@RestClientTest(BundesagenturApiClient.class)
@Import(com.hireops.config.RestClientConfig.class)
class BundesagenturApiClientTest {

    @Autowired
    private BundesagenturApiClient client;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void searchJobs_shouldReturnJobSearchResponse() throws Exception {
        com.fasterxml.jackson.databind.JsonNode jobData = objectMapper
                .readTree("{\"refnr\":\"ref1\",\"titel\":\"Java Experte\",\"arbeitgeber\":\"Acme Corp\"}");
        JobSearchResponse mockResponse = new JobSearchResponse(List.of(jobData));

        server.expect(requestTo(
                "https://rest.arbeitsagentur.de/jobboerse/jobsuche-service/pc/v4/app/jobs?was=Java&page=0&size=10"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(mockResponse), MediaType.APPLICATION_JSON));

        JobSearchResponse response = client.searchJobs("Java", 0, 10);

        assertThat(response).isNotNull();
        assertThat(response.stellenangebote()).hasSize(1);
        assertThat(response.stellenangebote().get(0).path("refnr").asText()).isEqualTo("ref1");
    }
}
