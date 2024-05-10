package io.github.guilhermedevsec.msavaliadorcredito.application.Exception;

public class ErroSolicitacaoCartaoException extends RuntimeException{
    public ErroSolicitacaoCartaoException(String message){
        super(message);
    }
}
