package com.hotel.backoffice.repository;

import com.hotel.backoffice.entity.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {
    Page<Auditoria> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Auditoria> findTop100ByOrderByCreatedAtDesc();
}
