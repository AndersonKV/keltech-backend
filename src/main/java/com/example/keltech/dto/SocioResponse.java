package com.example.keltech.dto;

import java.util.List;

public   record SocioResponse(
        String cnpjEmpresa,
        int participacao,
        String nome,
        String documento,
        String dataEntrada,
        String dataSaida,
        String cargoSociedade,
        String cidade,
        String uf,
        String cep,
        Boolean alerta,
        Boolean restricao,
        String situacao,
        List<Integer> socios
) {}

