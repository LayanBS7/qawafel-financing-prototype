package com.qawafel.financing.service;

import com.qawafel.financing.analysis.AnalysisResult;
import com.qawafel.financing.analysis.AnalysisService;
import com.qawafel.financing.domain.*;
import com.qawafel.financing.ingest.ExcelInvoiceParser;
import com.qawafel.financing.ingest.InvoiceValidator;
import com.qawafel.financing.ingest.ParsedWorkbook;
import com.qawafel.financing.ingest.ValidationResult;
import com.qawafel.financing.repository.DataIssueRepository;
import com.qawafel.financing.repository.FinancingApplicationRepository;
import com.qawafel.financing.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

/** The upload flow: read the file, validate it, store the clean data, then analyse on demand. */
@Service
public class ApplicationService {

    private final ExcelInvoiceParser parser;
    private final InvoiceValidator validator;
    private final AnalysisService analysis;
    private final FinancingApplicationRepository applications;
    private final InvoiceRepository invoices;
    private final DataIssueRepository issues;

    public ApplicationService(ExcelInvoiceParser parser, InvoiceValidator validator, AnalysisService analysis,
                              FinancingApplicationRepository applications, InvoiceRepository invoices,
                              DataIssueRepository issues) {
        this.parser = parser;
        this.validator = validator;
        this.analysis = analysis;
        this.applications = applications;
        this.invoices = invoices;
        this.issues = issues;
    }

    @Transactional
    public FinancingApplication create(InputStream file, String fileName, String companyName, BigDecimal requestedAmount) {
        ParsedWorkbook wb = parser.parse(file);
        ValidationResult v = validator.validate(wb);

        String company = companyName != null && !companyName.isBlank() ? companyName.trim() : v.companyName();
        FinancingApplication app = new FinancingApplication(company, fileName, v.periodStart(), v.periodEnd(),
                v.asOfDate(), requestedAmount);
        app.recordRowCounts(v.rowsRead(), v.invoices().size(), v.rowsExcluded());
        applications.save(app);

        invoices.saveAll(v.invoices().stream().map(c -> toEntity(app, c)).toList());
        issues.saveAll(v.issues().stream().map(i -> new DataIssue(app, i.sourceRow(), i.invoiceNumber(), i.type(),
                i.severity(), i.message(), i.actionTaken())).toList());
        return app;
    }

    @Transactional(readOnly = true)
    public List<FinancingApplication> list() {
        return applications.findAllByOrderByUploadedAtDesc();
    }

    @Transactional(readOnly = true)
    public AnalysisResult analyze(Long id) {
        FinancingApplication app = get(id);
        return analysis.analyze(app,
                invoices.findByApplicationIdOrderByInvoiceDateAsc(id),
                issues.findByApplicationIdOrderBySourceRowAsc(id));
    }

    @Transactional(readOnly = true)
    public List<DataIssue> issues(Long id) {
        get(id);
        return issues.findByApplicationIdOrderBySourceRowAsc(id);
    }

    @Transactional(readOnly = true)
    public List<Invoice> invoices(Long id) {
        get(id);
        return invoices.findByApplicationIdOrderByInvoiceDateAsc(id);
    }

    @Transactional
    public FinancingApplication decide(Long id, AnalystDecision decision, String note) {
        FinancingApplication app = get(id);
        app.decide(decision, note);
        return app;
    }

    @Transactional
    public void delete(Long id) {
        FinancingApplication app = get(id);
        invoices.deleteAll(invoices.findByApplicationIdOrderByInvoiceDateAsc(id));
        issues.deleteAll(issues.findByApplicationIdOrderBySourceRowAsc(id));
        applications.delete(app);
    }

    private FinancingApplication get(Long id) {
        return applications.findById(id).orElseThrow(() -> new NotFoundException("Application " + id + " not found."));
    }

    public static Invoice toEntity(FinancingApplication app, ValidationResult.CleanInvoice c) {
        return new Invoice(app, c.sourceRow(), c.invoiceNumber(), c.type(), c.invoiceDate(), c.dueDate(),
                c.customerKey(), c.customerName(), c.customerVat(), c.segment(), c.city(), c.category(),
                c.paymentTermsDays(), c.subtotal(), c.vat(), c.total(), c.status(), c.paidDate(), c.amountPaid(),
                c.paidDateReliable());
    }
}
