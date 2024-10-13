package transaction.stage2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.NestedTransactionNotSupportedException;

/**
 * 트랜잭션 전파(Transaction Propagation)란? 트랜잭션의 경계에서 이미 진행 중인 트랜잭션이 있을 때 또는 없을 때 어떻게 동작할 것인가를 결정하는 방식을 말한다.
 * <p>
 * FirstUserService 클래스의 메서드를 실행할 때 첫 번째 트랜잭션이 생성된다. SecondUserService 클래스의 메서드를 실행할 때 두 번째 트랜잭션이 어떻게 되는지 관찰해보자.
 * <p>
 * https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#tx-propagation
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Stage2Test {

    private static final Logger log = LoggerFactory.getLogger(Stage2Test.class);

    @Autowired
    private FirstUserService firstUserService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    /**
     * 생성된 트랜잭션이 몇 개인가? 왜 그런 결과가 나왔을까?
     */
    // REQUIRED + REQUIRED -> 존재하는 트랜잭션 참여
    @Test
    void testRequired() {
        final var actual = firstUserService.saveFirstTransactionWithRequired();

        log.info("transactions : {}", actual);
        assertThat(actual)
                .hasSize(1)
                .containsExactly("transaction.stage2.FirstUserService.saveFirstTransactionWithRequired");
    }

    /**
     * 생성된 트랜잭션이 몇 개인가? 왜 그런 결과가 나왔을까?
     */
    // REQUIRED + REQUIRED_NEW -> 새로운 트랜잭션 생성
    @Test
    void testRequiredNew() {
        final var actual = firstUserService.saveFirstTransactionWithRequiredNew();

        log.info("transactions : {}", actual);
        assertThat(actual)
                .hasSize(2)
                .containsExactlyInAnyOrder("transaction.stage2.FirstUserService.saveFirstTransactionWithRequiredNew",
                        "transaction.stage2.SecondUserService.saveSecondTransactionWithRequiresNew");
    }

    /**
     * firstUserService.saveAndExceptionWithRequiredNew()에서 강제로 예외를 발생시킨다. REQUIRES_NEW 일 때 예외로 인한 롤백이 발생하면서 어떤 상황이
     * 발생하는지 확인해보자.
     */
    // REQUIRED + REQUIRED_NEW -> 새로운 트랜잭션 생성
    // 롤백 시, 1번 트랜잭션만 롤백? TODO 추측
    @Test
    void testRequiredNewWithRollback() {
        assertThat(firstUserService.findAll()).hasSize(0);

        assertThatThrownBy(() -> firstUserService.saveAndExceptionWithRequiredNew())
                .isInstanceOf(RuntimeException.class);

        assertThat(firstUserService.findAll()).hasSize(1);
    }

    /**
     * FirstUserService.saveFirstTransactionWithSupports() 메서드를 보면 @Transactional이 주석으로 되어 있다. 주석인 상태에서 테스트를 실행했을 때와 주석을
     * 해제하고 테스트를 실행했을 때 어떤 차이점이 있는지 확인해보자.
     */
    // 주석 있는 경우: REQUIRED + SUPPORTS -> 존재하는 트랜잭션 참여 (saveFirstTransactionWithSupports)
    // 주석 없는 경우: - + SUPPORTS -> 트랜잭션 없이 진행 (saveSecondTransactionWithSupports)
    // 주석이 없으면 트랜잭션 자체가 생성되지 않고, 주석이 있으면 트랜잭션이 생성은 되는 듯. 다만, SUPPORTS 경우 사용하지 않을 뿐.
    @Test
    void testSupports() {
        final var actual = firstUserService.saveFirstTransactionWithSupports();

        log.info("transactions : {}", actual);
        assertThat(actual)
                .hasSize(1)
                .containsExactly("transaction.stage2.SecondUserService.saveSecondTransactionWithSupports");
    }

    /**
     * FirstUserService.saveFirstTransactionWithMandatory() 메서드를 보면 @Transactional이 주석으로 되어 있다. 주석인 상태에서 테스트를 실행했을 때와
     * 주석을 해제하고 테스트를 실행했을 때 어떤 차이점이 있는지 확인해보자. SUPPORTS와 어떤 점이 다른지도 같이 챙겨보자.
     */
    // 주석 있는 경우: REQUIRED + MANDATORY -> 존재하는 트랜잭션 참여
    // 주석 없는 경우: - + MANDATORY -> 예외 발생
    @Test
    void testMandatory() {
        assertThatThrownBy(() -> firstUserService.saveFirstTransactionWithMandatory())
                .isInstanceOf(IllegalTransactionStateException.class);

        /* 주석 있는 경우
        final var actual = firstUserService.saveFirstTransactionWithMandatory();
        log.info("transactions : {}", actual);
        assertThat(actual)
                .hasSize(1)
                .containsExactly("transaction.stage2.FirstUserService.saveFirstTransactionWithMandatory");
         */
    }

    /**
     * 아래 테스트는 몇 개의 물리적 트랜잭션이 동작할까? FirstUserService.saveFirstTransactionWithNotSupported() 메서드의 @Transactional을 주석
     * 처리하자. 다시 테스트를 실행하면 몇 개의 물리적 트랜잭션이 동작할까?
     * <p>
     * 스프링 공식 문서에서 물리적 트랜잭션과 논리적 트랜잭션의 차이점이 무엇인지 찾아보자.
     */
    // 주석 있는 경우: 물리적 트랜잭션 2개이지만, 두번째 트랜잭션은 사용 X
    // 주석 없는 경우: 물리적 트랜잭션 1개이지만 사용 X (saveSecondTransactionWithNotSupported)
    @Test
    void testNotSupported() {
        final var actual = firstUserService.saveFirstTransactionWithNotSupported();

        log.info("transactions : {}", actual);
        assertThat(actual)
                .hasSize(2)
                .containsExactlyInAnyOrder("transaction.stage2.SecondUserService.saveSecondTransactionWithNotSupported",
                        "transaction.stage2.FirstUserService.saveFirstTransactionWithNotSupported");
        /* 주석 없는 경우
        assertThat(actual)
                .hasSize(1)
                .containsExactly("transaction.stage2.SecondUserService.saveSecondTransactionWithNotSupported");
         */
    }

    /**
     * 아래 테스트는 왜 실패할까? FirstUserService.saveFirstTransactionWithNested() 메서드의 @Transactional을 주석 처리하면 어떻게 될까?
     */
    // 주석 있는 경우: REQUIRED + NESTED -> JDBC만 저장 포인트 지원하므로 예외
    // 주석 없는 경우: - + NESTED -> 하나 생성
    @Test
    void testNested() {
        assertThatThrownBy(() -> firstUserService.saveFirstTransactionWithNested())
                .isInstanceOf(NestedTransactionNotSupportedException.class);

        /* 주석 없는 경우
        final var actual = firstUserService.saveFirstTransactionWithNested();
        log.info("transactions : {}", actual);
        assertThat(actual)
                .hasSize(0)
                .containsExactly("");
         */
    }

    /**
     * 마찬가지로 @Transactional을 주석처리하면서 관찰해보자.
     */
    // 주석 있는 경우: REQUIRED + NEVER -> 예외
    // 주석 없는 경우: - + NEVER -> 트랜잭션 1개이지만 사용 X
    @Test
    void testNever() {
        assertThatThrownBy(() -> firstUserService.saveFirstTransactionWithNever())
                .isInstanceOf(IllegalTransactionStateException.class);

        /* 주석 없는 경우
        final var actual = firstUserService.saveFirstTransactionWithNever();
        log.info("transactions : {}", actual);
        assertThat(actual)
                .hasSize(1)
                .containsExactly("transaction.stage2.SecondUserService.saveSecondTransactionWithNever");
         */
    }
}
