package io.github.regulacao_marcarcao.regulacao_marcacao.exceptions;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.persistence.EntityNotFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Esta anotação torna a classe um gerenciador de exceções global para todos os controllers.
@ControllerAdvice
public class GlobalExceptionHandler {

    /** Corpo padrão de erro: o frontend lê o campo `message` em todas as telas. */
    private static ResponseEntity<Object> erro(HttpStatus status, String mensagem) {
        Map<String, String> body = new HashMap<>();
        body.put("message", mensagem);
        return new ResponseEntity<>(body, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {

        boolean isCpfConflict = ex.getBindingResult().getFieldErrors().stream()
                .anyMatch(error -> "cpfPaciente".equals(error.getField()) &&
                                   error.getDefaultMessage() != null &&
                                   error.getDefaultMessage().contains("CPF já cadastrado"));

        if (isCpfConflict) {
            // Status 409 Conflict é o "sinal" que o frontend usa para esse caso.
            return erro(HttpStatus.CONFLICT, "CPF já cadastrado, consulte o módulo Paciente");
        }

        // Demais erros de validação: devolve campo + mensagem. Sem o nome do campo a
        // tela mostrava apenas a mensagem padrão do Bean Validation ("não deve ser
        // nulo"), sem dizer o que estava faltando — o operador não tinha como agir.
        String detalhes = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> {
                    String mensagem = e.getDefaultMessage() != null ? e.getDefaultMessage() : "inválido";
                    // Mensagens customizadas já explicam o campo; não vale repeti-lo.
                    return mensagem.contains(e.getField()) ? mensagem : e.getField() + ": " + mensagem;
                })
                .distinct()
                .collect(Collectors.joining(" | "));

        return erro(HttpStatus.BAD_REQUEST,
                detalhes.isBlank() ? "Erro de validação. Verifique os campos." : detalhes);
    }

    /**
     * Regra de negócio violada — 409 Conflict.
     *
     * É por aqui que a mensagem de <b>cota esgotada</b> chega ao operador. Sem este
     * handler a exceção virava um 500 genérico, cujo corpo padrão do Spring não traz
     * o campo `message` (server.error.include-message = never): a cota bloqueava o
     * agendamento corretamente, mas a tela dizia apenas "Verifique os dados e tente
     * novamente", sem explicar que o limite da unidade havia sido atingido.
     *
     * Também cobre: grupo com cotas/unidades vinculadas que não pode ser excluído.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleRegraDeNegocio(IllegalStateException ex) {
        return erro(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Dados inválidos enviados pelo cliente — 400 com a causa explícita. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleArgumentoInvalido(IllegalArgumentException ex) {
        return erro(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Recurso inexistente — 404 em vez de 500. */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Object> handleNaoEncontrado(EntityNotFoundException ex) {
        return erro(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
