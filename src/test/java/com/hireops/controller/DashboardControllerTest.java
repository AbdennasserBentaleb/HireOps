package com.hireops.controller;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.repository.JobPostingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobPostingRepository jobPostingRepository;

    @Test
    void testGetDashboard_ShouldReturnViewWithModels() throws Exception {
        JobPosting fetchedJob = new JobPosting();
        fetchedJob.setStatus(JobStatus.FETCHED);
        fetchedJob.setTitle("Java Dev");

        JobPosting scoredJob = new JobPosting();
        scoredJob.setStatus(JobStatus.SCORED);
        scoredJob.setTitle("Kotlin Dev");

        when(jobPostingRepository.findAll()).thenReturn(List.of(fetchedJob, scoredJob));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("fetchedJobs"))
                .andExpect(model().attributeExists("scoredJobs"))
                .andExpect(model().attributeExists("approvedJobs"))
                .andExpect(model().attributeExists("appliedJobs"));
    }
}
