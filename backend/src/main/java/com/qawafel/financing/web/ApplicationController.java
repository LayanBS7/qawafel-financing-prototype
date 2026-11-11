package com.qawafel.financing.web;

import com.qawafel.financing.analysis.AnalysisResult;
import com.qawafel.financing.domain.FinancingApplication;
import com.qawafel.financing.ingest.InvalidFileException;
import com.qawafel.financing.service.ApplicationService;
import com.qawafel.financing.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private static final String SAMPLE_FILE = "sample/invoices_12m.xlsx";

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    /** Upload an invoice export and get back the full analysis. */
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResult upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "companyName", required = false) String companyName,
                                 @RequestParam(value = "requestedAmount", required = false) BigDecimal requestedAmount)
            throws IOException {
        if (file.isEmpty()) {
            throw new InvalidFileException("The uploaded file is empty.");
        }
        if (requestedAmount != null && requestedAmount.signum() <= 0) {
            throw new InvalidFileException("Requested amount must be positive.");
        }
        try (InputStream in = file.getInputStream()) {
            FinancingApplication app = service.create(in, file.getOriginalFilename(), companyName, requestedAmount);
            return service.analyze(app.getId());
        }
    }

    /** Load the bundled sample file, so the prototype can be tried without preparing an upload. */
    @PostMapping("/sample")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResult loadSample(@RequestParam(value = "requestedAmount", required = false) BigDecimal requestedAmount)
            throws IOException {
        try (InputStream in = new ClassPathResource(SAMPLE_FILE).getInputStream()) {
            FinancingApplication app = service.create(in, "invoices_12m.xlsx (sample)", null,
                    requestedAmount != null ? requestedAmount : new BigDecimal("3000000"));
            return service.analyze(app.getId());
        }
    }

    /** Download the sample file, to see what the expected format looks like. */
    @GetMapping("/sample/file")
    public ResponseEntity<Resource> sampleFile() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices_12m.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ClassPathResource(SAMPLE_FILE));
    }

    @GetMapping
    public List<ApplicationSummary> list() {
        return service.list().stream().map(ApplicationSummary::of).toList();
    }

    @GetMapping("/{id}")
    public AnalysisResult get(@PathVariable Long id) {
        return service.analyze(id);
    }

    @GetMapping("/{id}/issues")
    public List<IssueView> issues(@PathVariable Long id) {
        return service.issues(id).stream().map(IssueView::of).toList();
    }

    @GetMapping("/{id}/invoices")
    public List<InvoiceView> invoices(@PathVariable Long id,
                                      @RequestParam(value = "customerKey", required = false) String customerKey) {
        return service.invoices(id).stream()
                .filter(i -> customerKey == null || customerKey.equals(i.getCustomerKey()))
                .map(InvoiceView::of).toList();
    }

    /** The analyst's call on the application. */
    @PostMapping("/{id}/decision")
    public ApplicationSummary decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest body) {
        return ApplicationSummary.of(service.decide(id, body.decision(), body.note()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
