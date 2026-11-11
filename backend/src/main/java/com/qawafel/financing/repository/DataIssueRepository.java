package com.qawafel.financing.repository;

import com.qawafel.financing.domain.DataIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataIssueRepository extends JpaRepository<DataIssue, Long> {

    List<DataIssue> findByApplicationIdOrderBySourceRowAsc(Long applicationId);
}
