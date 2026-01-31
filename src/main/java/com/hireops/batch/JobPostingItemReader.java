package com.hireops.batch;

import com.hireops.dto.JobSearchResponse;
import com.hireops.service.BundesagenturApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemReader;

import java.util.ArrayList;
import java.util.List;

public class JobPostingItemReader implements ItemReader<JsonNode> {

    private final BundesagenturApiClient apiClient;
    private final String keyword;
    private int currentPage = 1;
    private List<JsonNode> jobs = new ArrayList<>();

    // We fetch a smaller initial chunk (25 jobs max) on startup instead of 250 
    // to keep the dashboard polished and manageable for a demo.
    private static final int PAGE_SIZE = 25;
    private static final int MAX_PAGES = 1;

    public JobPostingItemReader(BundesagenturApiClient apiClient, String keyword) {
        this.apiClient = apiClient;
        this.keyword = keyword;
    }

    @Override
    public JsonNode read() {
        if (jobs.isEmpty() && currentPage <= MAX_PAGES) {
            fetchNextPage();
        }

        if (jobs.isEmpty()) {
            return null; // Indicates end of data
        }

        return jobs.remove(0);
    }

    private void fetchNextPage() {
        JobSearchResponse response = apiClient.searchJobs(keyword, currentPage, PAGE_SIZE);
        if (response != null && response.stellenangebote() != null && !response.stellenangebote().isEmpty()) {
            jobs.addAll(response.stellenangebote());
            currentPage++;
        } else {
            // Signal no more pages to fetch by maxing out the page counter
            currentPage = MAX_PAGES + 1;
        }
    }
}
