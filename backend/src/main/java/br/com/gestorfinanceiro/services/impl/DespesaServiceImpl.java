package br.com.gestorfinanceiro.services.impl;

import br.com.gestorfinanceiro.dto.grafico.GraficoBarraDTO;
import br.com.gestorfinanceiro.dto.grafico.GraficoPizzaDTO;
import br.com.gestorfinanceiro.exceptions.common.InvalidDataException;
import br.com.gestorfinanceiro.exceptions.common.InvalidUuidException;
import br.com.gestorfinanceiro.exceptions.despesa.DespesaNotFoundException;
import br.com.gestorfinanceiro.exceptions.despesa.DespesaOperationException;
import br.com.gestorfinanceiro.exceptions.user.InvalidUserIdException;
import br.com.gestorfinanceiro.models.DespesaEntity;
import br.com.gestorfinanceiro.models.UserEntity;
import br.com.gestorfinanceiro.repositories.DespesaRepository;
import br.com.gestorfinanceiro.repositories.UserRepository;
import br.com.gestorfinanceiro.services.DespesaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DespesaServiceImpl implements DespesaService {

    private final DespesaRepository despesaRepository;
    private final UserRepository userRepository;

    public DespesaServiceImpl(DespesaRepository despesaRepository, UserRepository userRepository) {
        this.despesaRepository = despesaRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public DespesaEntity criarDespesa(DespesaEntity despesa, String userId) {
        if (despesa == null) {
            throw new InvalidDataException("A despesa não pode ser nula.");
        }

        if (despesa.getValor() == null || despesa.getValor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidDataException("O valor da despesa deve ser maior que zero.");
        }

        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found."));

        try {
            despesa.setUser(user);
            return despesaRepository.save(despesa);
        } catch (Exception e) {
            throw new DespesaOperationException("Erro ao criar despesa. Por favor, tente novamente.", e);
        }
    }

    @Override
    public List<DespesaEntity> listarDespesasUsuario(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new InvalidUserIdException();
        }

        List<DespesaEntity> despesas = despesaRepository.findAllByUserUuid(userId);

        if (despesas.isEmpty()) {
            throw new DespesaNotFoundException(userId);
        } else {
            return despesas;
        }
    }

    @Override
    public DespesaEntity buscarDespesaPorId(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            throw new InvalidUuidException();
        }

        return despesaRepository.findById(uuid)
                .orElseThrow(() -> new DespesaNotFoundException(uuid));
    }


    @Override
    @Transactional
    public DespesaEntity atualizarDespesa(String uuid, DespesaEntity despesaAtualizada) {
        if (uuid == null || uuid.trim().isEmpty()) {
            throw new InvalidUuidException();
        }

        if (despesaAtualizada == null) {
            throw new InvalidDataException("Os dados da despesa não podem ser nulos.");
        }

        DespesaEntity despesa = despesaRepository.findById(uuid)
                .orElseThrow(() -> new DespesaNotFoundException(uuid));


        try {
            despesa.setData(despesaAtualizada.getData());
            despesa.setCategoria(despesaAtualizada.getCategoria());
            despesa.setValor(despesaAtualizada.getValor());
            despesa.setDestinoPagamento(despesaAtualizada.getDestinoPagamento());
            despesa.setObservacoes(despesaAtualizada.getObservacoes());

            return despesaRepository.save(despesa);
        } catch (Exception e) {
            throw new DespesaOperationException("Erro ao atualizar despesa. Por favor, tente novamente.", e);
        }
    }

    @Override
    @Transactional
    public void excluirDespesa(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            throw new InvalidUuidException();
        }

        DespesaEntity despesa = despesaRepository.findById(uuid)
                .orElseThrow(() -> new DespesaNotFoundException(uuid));

        try {
            despesaRepository.delete(despesa);
        } catch (Exception e) {
            throw new DespesaOperationException("Erro ao excluir despesa. Por favor, tente novamente.", e);
        }
    }

    @Override
    public GraficoBarraDTO gerarGraficoBarras(String userId, YearMonth inicio, YearMonth fim) {
        List<DespesaEntity> despesas = despesaRepository.findByUserAndYearMonthRange(userId, inicio, fim);

        // Formata datas para o padrão "Mês Ano" em português
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-BR"));

        // Cria um mapa ordenado com os dados mensais
        Map<String, BigDecimal> dadosMensais = new LinkedHashMap<>();
        despesas.stream()
                .collect(Collectors.groupingBy(
                        d -> YearMonth.from(d.getData()),
                        Collectors.reducing(BigDecimal.ZERO, DespesaEntity::getValor, BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> dadosMensais.put(e.getKey().format(formatter), e.getValue()));

        // Retorna o DTO com os dados mensais
        return new GraficoBarraDTO(dadosMensais);
    }

    @Override
    public GraficoPizzaDTO gerarGraficoPizza(String userId, LocalDate inicio, LocalDate fim) {
        List<DespesaEntity> despesas = despesaRepository.findByUserAndDateRange(userId, inicio, fim);

        Map<String, BigDecimal> categorias = despesas.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getCategoria().name(),
                        Collectors.mapping(DespesaEntity::getValor, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));

        return new GraficoPizzaDTO(categorias);
    }

    @Override
    public List<DespesaEntity> buscarDespesasPorIntervaloDeDatas(String userId, LocalDate inicio, LocalDate fim) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new InvalidUserIdException();
        }

        if (inicio == null || fim == null) {
            throw new InvalidDataException("As datas de início e fim não podem ser nulas.");
        }

        if (inicio.isAfter(fim)) {
            throw new InvalidDataException("A data de início não pode ser após a data de fim.");
        }

        try {
            return despesaRepository.findByUserAndDateRange(userId, inicio, fim);
        } catch (Exception e) {
            throw new DespesaOperationException("Erro ao buscar despesas por intervalo de datas. Por favor, tente novamente.", e);
        }
    }

    @Override
    public List<DespesaEntity> buscarDespesasPorIntervaloDeValores(String userId, BigDecimal min, BigDecimal max) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new InvalidUserIdException();
        }

        if (min == null || max == null) {
            throw new InvalidDataException("Os valores mínimo e máximo não podem ser nulos.");
        }

        if (min.compareTo(BigDecimal.ZERO) <= 0 || max.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidDataException("Os valores mínimo e máximo devem ser maiores que zero.");
        }

        if (min.compareTo(max) > 0) {
            throw new InvalidDataException("O valor mínimo não pode ser maior que o valor máximo.");
        }

        try {
            return despesaRepository.findByUserAndValueBetween(userId, min, max);
        } catch (Exception e) {
            throw new DespesaOperationException("Erro ao buscar despesas por intervalo de valores. Por favor, tente novamente.", e);
        }
    }
}