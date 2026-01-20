package com.hireops.repository;

import com.hireops.model.ResumePersona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ResumePersonaRepository extends JpaRepository<ResumePersona, UUID> {
}
