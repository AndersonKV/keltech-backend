package com.example.keltech.dto;

import java.util.List;

public record ErrorResponse(
        int status,
        String titulo,
        String detalhes,
        List<String> validacao
) {
}