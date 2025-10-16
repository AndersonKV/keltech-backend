package com.example.keltech.controller;

import com.example.keltech.dto.ErrorResponse;
import com.example.keltech.dto.SocioDetalheResponse;
import com.example.keltech.dto.SocioResponse;
import com.example.keltech.exception.HttpErrorException;
import com.example.keltech.exception.RateLimitException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/socios")
@CrossOrigin(origins = "http://localhost:3000")
public class ControllerSocio {

    private static final String BASE_URL_JSON = "https://keltech-test.wiremockapi.cloud/json";
    private static final String BASE_URL_RECEITA = "https://publica.cnpj.ws/cnpj/";
    private static final int TIMEOUT_MILLIS = 5000;
    private static final int RETRY_COUNT = 2;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public List<SocioResponse> listarSocios(
            @Parameter(description = "Participação mínima do sócio", example = "20")
            @RequestParam(defaultValue = "20") Integer participacaoMin
    ) {
        try {
            JsonNode quadro = fetchJsonNode(BASE_URL_JSON)
                    .path("mix")
                    .path("quadroSocietario")
                    .path("data")
                    .path("quadroSocietario");

            List<SocioResponse> socios = new ArrayList<>();
            List<SocioResponse> todosSocios = new ArrayList<>();
            List<SocioResponse> resultado = null;

            if (quadro.isArray()) {
                for (JsonNode item : quadro) {
                    int participacao = item.path("participacao").asInt();

                    List<Integer> sociosInternos = new ArrayList<>();
                    JsonNode sociosNode = item.path("socios");
                    if (sociosNode.isArray()) {
                        for (JsonNode s : sociosNode) {
                            sociosInternos.add(s.asInt());
                        }
                    }

                    SocioResponse socio = new SocioResponse(
                            item.path("cnpjEmpresa").asText(),
                            participacao,
                            item.path("nome").asText(),
                            item.path("documento").asText(),
                            item.path("dataEntrada").asText(),
                            item.path("dataSaida").asText(),
                            item.path("cargoSociedade").asText(),
                            item.path("cidade").asText(),
                            item.path("uf").asText(),
                            item.path("cep").asText(),
                            item.path("alerta").asBoolean(),
                            item.path("restricao").asBoolean(),
                            item.path("situacao").asText(),
                            sociosInternos
                    );

                    todosSocios.add(socio);

                    resultado = todosSocios.stream()
                            .limit(Math.max(1, participacaoMin))
                            .collect(Collectors.toList());
                }
            }

            return resultado;

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Erro ao buscar sócios", e);
        }
    }

    @GetMapping("/{cnpj}")
    public ResponseEntity<?> detalharSocio(@PathVariable String cnpj) {
        try {
            JsonNode receitaNode = null;
            try {
                receitaNode = fetchJsonNode(BASE_URL_RECEITA + cnpj);
            } catch (HttpTimeoutException e) {
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                        .body(new ErrorResponse(
                                408,
                                "Timeout na requisição",
                                "A consulta à Receita Federal excedeu o tempo limite de " + TIMEOUT_MILLIS + "ms",
                                List.of()
                        ));
            } catch (RateLimitException e) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ErrorResponse(
                                429,
                                "Muitas requisições",
                                e.getMessage(),
                                List.of()
                        ));
            } catch (HttpErrorException e) {
                if (e.getStatusCode() == 404) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ErrorResponse(
                                    404,
                                    "CNPJ não encontrado",
                                    "O CNPJ " + cnpj + " não foi encontrado na Receita Federal",
                                    List.of()
                            ));
                } else if (e.getStatusCode() == 400) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new ErrorResponse(
                                    400,
                                    "Requisição inválida",
                                    "CNPJ inválido",
                                    List.of()
                            ));
                }
                return ResponseEntity.status(e.getStatusCode())
                        .body(new ErrorResponse(
                                e.getStatusCode(),
                                "Erro na consulta",
                                e.getMessage() + " ao consultar CNPJ " + cnpj,
                                List.of()
                        ));
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ErrorResponse(
                                503,
                                "Serviço indisponível",
                                "Erro ao consultar Receita Federal após " + (RETRY_COUNT + 1) + " tentativas: " + e.getMessage(),
                                List.of()
                        ));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ErrorResponse(
                                500,
                                "Erro interno",
                                "Requisição interrompida",
                                List.of()
                        ));
            }

            if (receitaNode == null || receitaNode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(
                                404,
                                "CNPJ não encontrado",
                                "O CNPJ " + cnpj + " não foi encontrado na Receita Federal",
                                List.of()
                        ));
            }

            JsonNode quadro = fetchJsonNode(BASE_URL_JSON)
                    .path("mix")
                    .path("quadroSocietario")
                    .path("data")
                    .path("quadroSocietario");

            JsonNode socioNode = null;
            if (quadro.isArray()) {
                for (JsonNode item : quadro) {
                    if (cnpj.equals(item.path("documento").asText())) {
                        socioNode = item;
                        break;
                    }
                }
            }

            String nomeSocio = socioNode != null ? socioNode.path("nome").asText() :
                    receitaNode.path("razao_social").asText("N/A");
            int participacao = socioNode != null ? socioNode.path("participacao").asInt() : 0;

            String cep = "";
            JsonNode estabelecimento = receitaNode.path("estabelecimento");

            cep = estabelecimento.path("cep").asText("");

            // se não encontrou CEP, tenta construir endereço completo
            String enderecoCompleto = "";
            if (cep.isEmpty()) {
                String logradouro = estabelecimento.path("logradouro").asText("");
                String numero = estabelecimento.path("numero").asText("");
                String bairro = estabelecimento.path("bairro").asText("");
                String municipio = estabelecimento.path("municipio").asText("");
                String uf = estabelecimento.path("uf").asText("");

                enderecoCompleto = String.join(" ", logradouro, numero, bairro, municipio, uf).trim();
            }

            String mapaUrl = !cep.isEmpty()
                    ? "https://www.google.com/maps/search/?api=1&query=" + cep
                    : !enderecoCompleto.isEmpty()
                    ? "https://www.google.com/maps/search/?api=1&query=" + enderecoCompleto.replace(" ", "+")
                    : "";

            return ResponseEntity.ok(new SocioDetalheResponse(
                    nomeSocio,
                    participacao,
                    cnpj,
                    receitaNode,
                    mapaUrl
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(
                            500,
                            "Erro interno",
                            "Erro inesperado ao processar a requisição: " + e.getMessage(),
                            List.of()
                    ));
        }
    }


    private JsonNode fetchJsonNode(String url) throws IOException, InterruptedException, RateLimitException, HttpErrorException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(TIMEOUT_MILLIS))
                .build();

        IOException lastException = null;
        int attempts = 0;

        for (int i = 0; i <= RETRY_COUNT; i++) {
            attempts++;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(TIMEOUT_MILLIS))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 429) {
                    // extrai tempo de retry do header se disponível
                    String retryAfter = response.headers().firstValue("Retry-After").orElse("60");
                    ZonedDateTime liberacaoEm = ZonedDateTime.now().plusSeconds(Long.parseLong(retryAfter));

                    String mensagem = String.format(
                            "Excedido o limite máximo de 3 consultas por minuto. Liberação ocorrerá em %s",
                            liberacaoEm.format(DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z '(Brasilia Standard Time)'", new Locale("pt", "BR")))
                    );

                    throw new RateLimitException(mensagem);
                }

                if (response.statusCode() == 404) {
                    throw new HttpErrorException(404, "CNPJ não encontrado");
                }

                if (response.statusCode() >= 400) {
                    throw new HttpErrorException(response.statusCode(), "Erro HTTP " + response.statusCode());
                }

                return objectMapper.readTree(response.body());

            } catch (HttpTimeoutException e) {
                if (i == RETRY_COUNT) {
                    throw e;
                }
                lastException = e;
                Thread.sleep(500 * (i + 1));
            } catch (RateLimitException | HttpErrorException e) {
                throw e;
            } catch (IOException e) {
                lastException = e;
                if (i < RETRY_COUNT) {
                    Thread.sleep(500 * (i + 1));
                }
            }
        }

        throw new IOException("Falha após " + attempts + " tentativas: " + url, lastException);
    }




}