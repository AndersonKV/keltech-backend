package com.example.keltech.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SocioDetalheResponse(
        String nome,
        int participacao,
        String cnpj,
        JsonNode dadosReceita,
        String mapaUrl
) {
}
