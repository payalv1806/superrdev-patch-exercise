package com.internal.tasktracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Initial task list loads successfully with default pagination")
    void testInitialTaskList() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.total").value(47))
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.items[*].archived", everyItem(is(false))));
    }

    @Test
    @DisplayName("Search returns matching tasks and excludes archived tasks")
    void testSearchExcludesArchivedTasks() throws Exception {
        // 'migration' appears in task 8 (archived=false) and task 20 (archived=true)
        mockMvc.perform(get("/api/tasks")
                        .param("q", "migration")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(8))
                .andExpect(jsonPath("$.items[0].archived").value(false));
    }

    @Test
    @DisplayName("Status filter is strictly applied even when title matches search term")
    void testStatusFilterEnforcedOnTitleMatches() throws Exception {
        // 'login' appears in title of task 1 (status=OPEN). It must NOT match when status=DONE.
        mockMvc.perform(get("/api/tasks")
                        .param("q", "login")
                        .param("status", "DONE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("Search and status filter work together correctly")
    void testSearchAndStatusFilterCombination() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("q", "api")
                        .param("status", "IN_PROGRESS")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", not(empty())))
                .andExpect(jsonPath("$.items[*].status", everyItem(is("IN_PROGRESS"))))
                .andExpect(jsonPath("$.items[*].archived", everyItem(is(false))));
    }

    @Test
    @DisplayName("Pagination and page size return distinct non-overlapping items")
    void testPaginationAndPageSize() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("page", "1")
                        .param("pageSize", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(5))
                .andExpect(jsonPath("$.items", hasSize(5)));

        mockMvc.perform(get("/api/tasks")
                        .param("page", "2")
                        .param("pageSize", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.pageSize").value(5))
                .andExpect(jsonPath("$.items", hasSize(5)));
    }

    @Test
    @DisplayName("Empty search results are handled cleanly")
    void testEmptySearchResults() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("q", "nonexistentquery_xyz_12345")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("Invalid status returns 400 Bad Request instead of 500 error")
    void testInvalidStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("status", "INVALID_STATUS")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Invalid status")));
    }

    @Test
    @DisplayName("Invalid pagination parameters return 400 Bad Request")
    void testInvalidPaginationReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("page", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Page number")));

        mockMvc.perform(get("/api/tasks")
                        .param("pageSize", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Page size")));
    }
}
