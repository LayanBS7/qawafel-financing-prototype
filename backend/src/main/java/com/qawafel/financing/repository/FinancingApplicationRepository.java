package com.qawafel.financing.repository;

import com.qawafel.financing.domain.FinancingApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancingApplicationRepository extends JpaRepository<FinancingApplication, Long> {

    List<FinancingApplication> findAllByOrderByUploadedAtDesc();
}
