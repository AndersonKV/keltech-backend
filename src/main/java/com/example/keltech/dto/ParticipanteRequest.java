package com.example.keltech.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParticipanteRequest {
    @Min(value =  1, message = "A quantidade mínima é 1.")
    private int quantidade;
}
