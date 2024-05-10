package io.github.guilhermedevsec.msavaliadorcredito.application.Exception;

import lombok.Getter;

public class ErroComunicacaoMicroservicesException extends Exception{
    @Getter
    private Integer status;

    public ErroComunicacaoMicroservicesException(String msg, Integer status){
        super(msg);
        this.status = status;
    }
}
