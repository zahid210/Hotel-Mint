package com.hotel.backoffice.repository;

import com.hotel.backoffice.entity.IdempotenciaClave;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotenciaClaveRepository extends JpaRepository<IdempotenciaClave, String> {
}
