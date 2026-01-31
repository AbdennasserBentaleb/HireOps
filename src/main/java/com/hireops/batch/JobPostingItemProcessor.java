package com.hireops.batch;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.service.BundesagenturApiClient;
import org.springframework.batch.item.ItemProcessor;
import com.fasterxml.jackson.databind.JsonNode;

public class JobPostingItemProcessor implements ItemProcessor<JsonNode, JobPosting> {

    private final BundesagenturApiClient apiClient;

    public JobPostingItemProcessor(BundesagenturApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public JobPosting process(JsonNode node) {
        JobPosting posting = new JobPosting();

        String refnr = node.path("refnr").asText("");
        String title = node.path("titel").asText("Unknown Title");
        String company = node.path("arbeitgeber").asText("Unknown Company");
        String hashId = node.path("hashId").asText("");

        posting.setReferenceId(refnr);
        posting.setHashId(hashId);
        posting.setTitle(title);
        posting.setCompany(company);
        posting.setStatus(JobStatus.FETCHED);

        // Try to fetch the full job detail to get the real description and contact
        // email.
        // The detail endpoint returns `stellenbeschreibung` and `kontakt` with the HR
        // email.
        if (!hashId.isEmpty()) {
            apiClient.getJobDetails(hashId).ifPresent(detail -> {
                // Use the real, full job description for better AI matching quality
                String description = detail.path("stellenbeschreibung").asText("");
                if (!description.isEmpty()) {
                    posting.setDescription(description);
                }

                // Extract employer contact email from the kontakt object.
                // API v4 structure: kontakt -> angebotskontakt -> email
                // Or sometimes flat: kontakt -> email
                JsonNode kontakt = detail.path("kontakt");
                if (!kontakt.isMissingNode()) {
                    String email = kontakt.path("angebotskontakt").path("email").asText("");
                    if (email.isEmpty()) {
                        email = kontakt.path("email").asText("");
                    }
                    if (!email.isEmpty()) {
                        posting.setEmployerEmail(email);
                    }
                }
            });
        }

        // Fallback: synthesize description if detail call failed or returned nothing
        if (posting.getDescription() == null || posting.getDescription().isEmpty()) {
            posting.setDescription(String.format(
                    "Role: %s\nCompany: %s\nRequirements: The candidate should have experience matching %s.",
                    title, company, title));
        }

        return posting;
    }
}
