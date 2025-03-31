package br.com.gestorfinanceiro.services.OrcamentoMensalServiceTest;

import br.com.gestorfinanceiro.exceptions.categoria.CategoriaNameNotFoundException;
import br.com.gestorfinanceiro.exceptions.orcamentomensal.OrcamentoMensalAlreadyExistsException;
import br.com.gestorfinanceiro.exceptions.orcamentomensal.OrcamentoMensalNotFoundException;
import br.com.gestorfinanceiro.exceptions.common.InvalidDataException;
import br.com.gestorfinanceiro.models.CategoriaEntity;
import br.com.gestorfinanceiro.models.OrcamentoMensalEntity;
import br.com.gestorfinanceiro.models.UserEntity;
import br.com.gestorfinanceiro.models.enums.CategoriaType;
import br.com.gestorfinanceiro.models.enums.Roles;
import br.com.gestorfinanceiro.repositories.CategoriaRepository;
import br.com.gestorfinanceiro.repositories.OrcamentoMensalRepository;
import br.com.gestorfinanceiro.repositories.UserRepository;
import br.com.gestorfinanceiro.services.OrcamentoMensalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class OrcamentoMensalServiceIntegrationTest {

    private static final String CATEGORIA_PADRAO = "Alimentacao";
    private static final String CATEGORIA_INVALIDA = "CategoriaInexistente";
    private static final BigDecimal VALOR_PADRAO = BigDecimal.valueOf(100);
    private static final BigDecimal VALOR_ATUALIZADO = BigDecimal.valueOf(200);
    private static final BigDecimal VALOR_NEGATIVO = BigDecimal.valueOf(-100);
    private static final YearMonth PERIODO_PADRAO = YearMonth.of(2023, 1);
    private static final YearMonth PERIODO_DIFERENTE = YearMonth.of(2023, 2);

    @Autowired
    private OrcamentoMensalService orcamentoMensalService;

    @Autowired
    private OrcamentoMensalRepository orcamentoMensalRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private UserRepository userRepository;

    private String userId;

    @BeforeEach
    public void setUp() {
        limparBancoDeDados();
        UserEntity user = criarUsuarioTest();
        userId = user.getUuid();
        criarCategoriaTest(CATEGORIA_PADRAO, user);
    }

    private void limparBancoDeDados() {
        orcamentoMensalRepository.deleteAll();
        categoriaRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    class CriarOrcamentoMensal {
        @Test
        void deveCriarOrcamentoMensalComSucesso() {
            // Act
            OrcamentoMensalEntity orcamento = orcamentoMensalService.criarOrcamentoMensal(
                    userId, CATEGORIA_PADRAO, VALOR_PADRAO, PERIODO_PADRAO);

            // Assert
            assertNotNull(orcamento.getUuid());
            assertEquals(CATEGORIA_PADRAO, orcamento.getCategoria().getNome());
            assertEquals(VALOR_PADRAO, orcamento.getValorLimite());
            assertEquals(PERIODO_PADRAO, orcamento.getPeriodo());
            assertEquals(userId, orcamento.getUser().getUuid());
        }

        @Test
        void deveLancarExcecaoQuandoCategoriaNaoExiste() {
            // Assert
            assertThrows(CategoriaNameNotFoundException.class, () -> {
                // Act
                orcamentoMensalService.criarOrcamentoMensal(
                        userId, CATEGORIA_INVALIDA, VALOR_PADRAO, PERIODO_PADRAO);
            });
        }

        @Test
        void deveLancarExcecaoQuandoValorNegativo() {
            // Assert
            assertThrows(InvalidDataException.class, () -> {
                // Act
                orcamentoMensalService.criarOrcamentoMensal(
                        userId, CATEGORIA_PADRAO, VALOR_NEGATIVO, PERIODO_PADRAO);
            });
        }

        @Test
        void deveLancarExcecaoQuandoOrcamentoDuplicado() {
            // Arrange
            orcamentoMensalService.criarOrcamentoMensal(
                    userId, CATEGORIA_PADRAO, VALOR_PADRAO, PERIODO_PADRAO);

            // Assert
            assertThrows(OrcamentoMensalAlreadyExistsException.class, () -> {
                // Act
                orcamentoMensalService.criarOrcamentoMensal(
                        userId, CATEGORIA_PADRAO, VALOR_ATUALIZADO, PERIODO_PADRAO);
            });
        }
    }

    @Nested
    class ListarOrcamentosMensais {
        @Test
        void deveListarTodosOrcamentosPorUsuario() {
            // Arrange
            orcamentoMensalService.criarOrcamentoMensal(
                    userId, CATEGORIA_PADRAO, VALOR_PADRAO, PERIODO_PADRAO);
            orcamentoMensalService.criarOrcamentoMensal(
                    userId, CATEGORIA_PADRAO, VALOR_ATUALIZADO, PERIODO_DIFERENTE);

            // Act
            List<OrcamentoMensalEntity> orcamentos = orcamentoMensalService.listarTodosPorUsuario(userId);

            // Assert
            assertEquals(2, orcamentos.size());
        }

        @Test
        void deveLancarExcecaoQuandoNenhumOrcamentoEncontrado() {
            // Assert
            assertThrows(OrcamentoMensalNotFoundException.class, () -> {
                // Act
                orcamentoMensalService.listarTodosPorUsuario(userId);
            });
        }

        @Test
        void deveListarOrcamentosPorPeriodo() {
            // Arrange
            orcamentoMensalService.criarOrcamentoMensal(
                    userId, CATEGORIA_PADRAO, VALOR_PADRAO, PERIODO_PADRAO);
            orcamentoMensalService.criarOrcamentoMensal(
                    userId, CATEGORIA_PADRAO, VALOR_ATUALIZADO, PERIODO_DIFERENTE);

            // Act
            List<OrcamentoMensalEntity> orcamentos = orcamentoMensalService.listarPorPeriodo(userId, PERIODO_PADRAO);

            // Assert
            assertEquals(1, orcamentos.size());
            assertEquals(PERIODO_PADRAO, orcamentos.get(0).getPeriodo());
        }

        @Test
        void deveLancarExcecaoQuandoPeriodoInvalido() {
            // Assert
            assertThrows(InvalidDataException.class, () -> {
                // Act
                orcamentoMensalService.listarPorPeriodo(userId, null);
            });
        }
    }

    @Nested
    class AtualizarOrcamentoMensal {
        private String orcamentoId;

        @BeforeEach
        void setUp() {
            OrcamentoMensalEntity orcamento = orcamentoMensalService.criarOrcamentoMensal(
                    userId, CATEGORIA_PADRAO, VALOR_PADRAO, PERIODO_PADRAO);
            orcamentoId = orcamento.getUuid();
        }

        @Test
        void deveAtualizarOrcamentoComSucesso() {
            // Act
            OrcamentoMensalEntity orcamentoAtualizado = orcamentoMensalService.atualizarOrcamentoMensal(
                    userId, orcamentoId, CATEGORIA_PADRAO, VALOR_ATUALIZADO, PERIODO_DIFERENTE);

            // Assert
            assertEquals(orcamentoId, orcamentoAtualizado.getUuid());
            assertEquals(VALOR_ATUALIZADO, orcamentoAtualizado.getValorLimite());
            assertEquals(PERIODO_DIFERENTE, orcamentoAtualizado.getPeriodo());
        }

        @Test
        void deveLancarExcecaoQuandoOrcamentoNaoExiste() {
            // Assert
            assertThrows(OrcamentoMensalNotFoundException.class, () -> {
                // Act
                orcamentoMensalService.atualizarOrcamentoMensal(
                        userId, "uuid-inexistente", CATEGORIA_PADRAO, VALOR_ATUALIZADO, PERIODO_DIFERENTE);
            });
        }

        @Test
        void deveLancarExcecaoQuandoTentarAtualizarParaOrcamentoDuplicado() {
            // Arrange
            orcamentoMensalService.criarOrcamentoMensal(
                    userId, CATEGORIA_PADRAO, VALOR_ATUALIZADO, PERIODO_DIFERENTE);

            // Assert
            assertThrows(OrcamentoMensalAlreadyExistsException.class, () -> {
                // Act
                orcamentoMensalService.atualizarOrcamentoMensal(
                        userId, orcamentoId, CATEGORIA_PADRAO, VALOR_ATUALIZADO, PERIODO_DIFERENTE);
            });
        }
    }

    @Nested
    class DeletarOrcamentoMensal {
        private String orcamentoId;

        @BeforeEach
        void setUp() {
            OrcamentoMensalEntity orcamento = orcamentoMensalService.criarOrcamentoMensal(
                    userId, CATEGORIA_PADRAO, VALOR_PADRAO, PERIODO_PADRAO);
            orcamentoId = orcamento.getUuid();
        }

        @Test
        void deveDeletarOrcamentoComSucesso() {
            // Act
            orcamentoMensalService.excluirOrcamentoMensal(userId, orcamentoId);

            // Assert
            assertThrows(OrcamentoMensalNotFoundException.class, () -> {
                orcamentoMensalService.buscarPorId(userId, orcamentoId);
            });
        }

        @Test
        void deveLancarExcecaoQuandoOrcamentoNaoExiste() {
            // Assert
            assertThrows(OrcamentoMensalNotFoundException.class, () -> {
                // Act
                orcamentoMensalService.excluirOrcamentoMensal(userId, "uuid-inexistente");
            });
        }
    }

    //----------------- Métodos Auxiliares -----------------//

    private UserEntity criarUsuarioTest() {
        UserEntity userTest = new UserEntity();
        userTest.setUsername("Jorge");
        userTest.setEmail("jorge@gmail.com");
        userTest.setPassword("123456");
        userTest.setRole(Roles.USER);
        return userRepository.save(userTest);
    }

    private void criarCategoriaTest(String nome, UserEntity user) {
        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setNome(nome);
        categoria.setTipo(CategoriaType.RECEITAS);
        categoria.setUser(user);
        categoriaRepository.save(categoria);
    }
}