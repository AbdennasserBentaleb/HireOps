package com.hireops.dto;

public record JobDetailResponse(
        String refnr,
        String titel,
        String arbeitgeber,
        String stellenbeschreibung
) {}
