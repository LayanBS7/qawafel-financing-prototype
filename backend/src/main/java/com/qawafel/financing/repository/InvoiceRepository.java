package com.qawafel.financing.repository;

import com.qawafel.financing.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByApplicationIdOrderByInvoiceDateAsc(Long applicationId);
}
