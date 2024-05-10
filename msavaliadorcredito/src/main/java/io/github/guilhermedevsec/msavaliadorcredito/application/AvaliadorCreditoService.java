package io.github.guilhermedevsec.msavaliadorcredito.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import feign.FeignException;
import io.github.guilhermedevsec.msavaliadorcredito.application.Exception.DadosClienteNotFoundException;
import io.github.guilhermedevsec.msavaliadorcredito.application.Exception.ErroComunicacaoMicroservicesException;
import io.github.guilhermedevsec.msavaliadorcredito.application.Exception.ErroSolicitacaoCartaoException;
import io.github.guilhermedevsec.msavaliadorcredito.domain.Cartao;
import io.github.guilhermedevsec.msavaliadorcredito.domain.CartaoAprovado;
import io.github.guilhermedevsec.msavaliadorcredito.domain.CartaoCliente;
import io.github.guilhermedevsec.msavaliadorcredito.domain.DadosCliente;
import io.github.guilhermedevsec.msavaliadorcredito.domain.DadosSolicitacaoEmissaoCartao;
import io.github.guilhermedevsec.msavaliadorcredito.domain.ProtocoloSolicitacaoCartao;
import io.github.guilhermedevsec.msavaliadorcredito.domain.RetornoAvaliacaoCliente;
import io.github.guilhermedevsec.msavaliadorcredito.domain.SituacaoCliente;
import io.github.guilhermedevsec.msavaliadorcredito.infra.clients.CartoesResourceClient;
import io.github.guilhermedevsec.msavaliadorcredito.infra.clients.ClienteResourceClient;
import io.github.guilhermedevsec.msavaliadorcredito.infra.mqueue.SolicitacaoEmissaoCartaoPublisher;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AvaliadorCreditoService {
    
    private final ClienteResourceClient clientesClient;
    private final CartoesResourceClient cartoesClient;
    private final SolicitacaoEmissaoCartaoPublisher emissaoCartaoPublisher;


    public SituacaoCliente obterSituacaoCliente(String cpf) throws DadosClienteNotFoundException, ErroComunicacaoMicroservicesException{
        //Obter dados cliente - msclientes
        //obter cartoes do cliente - mscartoes
        try{

            ResponseEntity<DadosCliente> dadosClienteResponse = clientesClient.dadosCliente(cpf);
            ResponseEntity<List<CartaoCliente>> cartaoClienteResponse = cartoesClient.getCartoesByCliente(cpf);
    
            return SituacaoCliente.builder()
                                  .cliente(dadosClienteResponse.getBody())
                                  .cartoes(cartaoClienteResponse.getBody())
                                  .build();
            
        }catch(FeignException.FeignClientException e){
            int status = e.status();
            if(HttpStatus.NOT_FOUND.value() == status){
                throw new DadosClienteNotFoundException();
            }
            throw new ErroComunicacaoMicroservicesException(e.getMessage(), status);
        }
    }

    public RetornoAvaliacaoCliente realizarAvaliacaoCliente(String cpf, Long renda) throws DadosClienteNotFoundException, ErroComunicacaoMicroservicesException{
       try{
            ResponseEntity<DadosCliente> dadosClienteResponse = clientesClient.dadosCliente(cpf);
            ResponseEntity<List<Cartao>> cartoesResponse = cartoesClient.getCartoesRendaAteh(renda);

            List<Cartao> cartoes = cartoesResponse.getBody();
            var listaCartoesAprovados = cartoes.stream().map(cartao -> {

                DadosCliente dadosCliente = dadosClienteResponse.getBody();
            
                BigDecimal limiteBasico = cartao.getLimiteBasico();
                BigDecimal idadeBD = BigDecimal.valueOf(dadosCliente.getIdade());
                var fator = idadeBD.divide(BigDecimal.valueOf(10));
                BigDecimal limiteAprovado = fator.multiply(limiteBasico);

                CartaoAprovado aprovado = new CartaoAprovado();
                aprovado.setCartao(cartao.getNome());
                aprovado.setBandeira(cartao.getBandeira());
                aprovado.setLimiteAprovado(limiteAprovado);

                return aprovado;
            }).collect(Collectors.toList());
            
            return new RetornoAvaliacaoCliente(listaCartoesAprovados);

        }catch(FeignException.FeignClientException e){
            int status = e.status();
            if(HttpStatus.NOT_FOUND.value() == status){
            throw new DadosClienteNotFoundException();
        }
        throw new ErroComunicacaoMicroservicesException(e.getMessage(), status);
    }
    }

    public ProtocoloSolicitacaoCartao solicitarEmissaoCartao(DadosSolicitacaoEmissaoCartao dados){
        try{
            emissaoCartaoPublisher.solicitarCartao(dados);
            var protocolo = UUID.randomUUID().toString();
            return new ProtocoloSolicitacaoCartao(protocolo);
        }catch(Exception e){
            throw new ErroSolicitacaoCartaoException(e.getMessage());
        }
    }
}
    
