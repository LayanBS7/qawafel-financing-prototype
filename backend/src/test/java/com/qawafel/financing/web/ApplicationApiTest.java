package com.qawafel.financing.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void uploadReturnsAnalysis() throws Exception {
        byte[] bytes = new ClassPathResource("sample/invoices_12m.xlsx").getContentAsByteArray();
        mvc.perform(multipart("/api/applications")
                        .file(new MockMultipartFile("file", "invoices.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes))
                        .param("companyName", "Test Wholesaler")
                        .param("requestedAmount", "2500000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.application.companyName").value("Test Wholesaler"))
                .andExpect(jsonPath("$.application.requestedAmount").value(2500000.0))
                .andExpect(jsonPath("$.monthly.length()").value(12))
                .andExpect(jsonPath("$.dataQuality.rowsExcluded").value(10))
                .andExpect(jsonPath("$.risk.score").isNumber());
    }

    @Test
    void rejectsAFileThatIsNotExcel() throws Exception {
        mvc.perform(multipart("/api/applications")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void rejectsMissingFile() throws Exception {
        mvc.perform(multipart("/api/applications"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownApplicationIs404() throws Exception {
        mvc.perform(get("/api/applications/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sampleThenIssuesThenDecision() throws Exception {
        String body = mvc.perform(post("/api/applications/sample"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));

        mvc.perform(get("/api/applications/" + id + "/issues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(32));

        mvc.perform(post("/api/applications/" + id + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"RECOMMEND_WITH_CONDITIONS\",\"note\":\"Cap Al-Rayyan exposure\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECIDED"))
                .andExpect(jsonPath("$.decision").value("RECOMMEND_WITH_CONDITIONS"));

        mvc.perform(post("/api/applications/" + id + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"no decision\"}"))
                .andExpect(status().isBadRequest());
    }
}
