package com.hireops.service;

import com.hireops.dto.JobDetailResponse;
import com.hireops.dto.JobSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

@Service
public class BundesagenturApiClient {

    private final RestClient restClient;
    private final String baseUrl;

    public BundesagenturApiClient(RestClient restClient,
            @Value("${ba.api.url:https://rest.arbeitsagentur.de/jobboerse/jobsuche-service/pc/v4}") String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @io.github.resilience4j.retry.annotation.Retry(name = "bundesagenturRetry")
    public JobSearchResponse searchJobs(String keyword, int page, int size) {
        var uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/app/jobs")
                .queryParam("was", keyword)
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .toUri();

        return restClient.get()
                .uri(uri)
                .header("X-API-Key", "jobboerse-jobsuche")
                .retrieve()
                .body(JobSearchResponse.class);
    }

    /**
     * Fetches the full detail record for a single job posting by its hashId.
     * The detail response contains the `kontakt` object with potential email/phone.
     */
    public Optional<JsonNode> getJobDetails(String hashId) {
        try {
            var uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/app/jobs/" + hashId)
                    .build()
                    .toUri();

            JsonNode detail = restClient.get()
                    .uri(uri)
                    .header("X-API-Key", "jobboerse-jobsuche")
                    .retrieve()
                    .body(JsonNode.class);

            return Optional.ofNullable(detail);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
