# Annotation Glossary

This living glossary covers annotations already used by OwlNest Backend. Add an entry when a new Spring or Jakarta annotation first enters the project.

## Application Startup

### `@SpringBootApplication`

Marks the main configuration class. It combines application configuration, auto-configuration based on dependencies, and component scanning. Spring processes it while `SpringApplication.run(...)` builds the `ApplicationContext`; discovered singleton beans are normally created during context startup. Keep the annotated class in the root package so feature packages below it are scanned. Usually an application has one main annotation of this kind. A common pitfall is placing components outside its package tree and expecting them to be discovered.

## Test Context

### `@SpringBootTest`

Asks Spring Boot's test framework to create a full application context before the test class runs and close cached contexts after the suite. Use it for integration tests, not every unit test: it is slower and may start database infrastructure.

### `@Import(TestcontainersConfiguration.class)`

Adds the named configuration or component to this test's context. Here it makes the PostgreSQL container bean available. Keep test-only imports out of production startup.

### `@TestConfiguration(proxyBeanMethods = false)`

Declares configuration intended only for tests. Spring reads its `@Bean` methods while building the test context. `proxyBeanMethods = false` avoids proxying method calls because this class does not call one bean method from another.

### `@Bean`

Marks a factory method whose returned object is managed by the Spring container. By default, the method name becomes the bean name and the scope is singleton. The container resolves method parameters as dependencies and manages applicable initialization and destruction callbacks. Avoid manually calling a `@Bean` method from ordinary application code.

### `@Configuration(proxyBeanMethods = false)`

Marks a class as a source of bean definitions. Spring reads it while building the application context. Disabling proxy methods is suitable here because `SecurityConfiguration` does not call one `@Bean` method from another; it avoids an unnecessary CGLIB proxy.

### `@Component`, `@Service`, and `@Repository`

All three make a class discoverable during component scanning and normally create one singleton bean for the application context. `@Component` is the general form, `@Service` communicates an application use case, and `@Repository` identifies persistence adapters and enables translation of supported persistence exceptions. Constructor injection is preferred because required dependencies remain explicit and testable.

## Persistence and Transactions

### `@Entity`, `@Table`, `@Id`, `@Column`, and `@UniqueConstraint`

These Jakarta Persistence annotations tell Hibernate how Java objects map to database tables, identifiers, columns, and uniqueness rules. Hibernate reads the metadata when the persistence context starts and tracks loaded entities within a transaction. Flyway remains the schema source of truth; the mappings must match its SQL migrations. A protected no-argument constructor is required so Hibernate can instantiate an entity.

### `@Transactional`

Spring wraps calls made through the managed service proxy in a database transaction. A successful call commits; an unchecked exception rolls it back by default. Use it on application operations that must be atomic, such as provisioning an account and profile. Calling a transactional method from another method on the same object bypasses the proxy and is a common pitfall.

## HTTP API

### `@RestController`, `@RequestMapping`, and `@GetMapping`

`@RestController` registers the class as an MVC controller whose return values are serialized into the HTTP response body. `@RequestMapping` defines the shared route prefix, while `@GetMapping` selects a GET route. Spring resolves these mappings during startup and invokes the matching method per request. Controllers should delegate business work to application services and return DTOs, not JPA entities.

### `@ServiceConnection`

Lets Spring Boot derive connection details from a Testcontainers object and override normal connection properties for the test context. Here it connects the application `DataSource` to PostgreSQL without hard-coded ports.

### `@AutoConfigureMockMvc`

Adds a `MockMvc` test client backed by the application context without opening a real HTTP port. It still executes the MVC and Spring Security filter chains, so it is useful for authenticated endpoint integration tests.

### `@Autowired`

Requests a dependency from the Spring test context. Production classes use constructor injection; field injection is kept here only to reduce test setup noise. The fields are populated after the test instance is created and before test methods run.

## JUnit

### `@Test`

Marks a method as a JUnit Jupiter test. JUnit discovers and invokes it during `./gradlew test`; it does not create Spring state by itself. Prefer plain `@Test` tests when no Spring context is required.

### `@AfterEach`

Marks cleanup that JUnit runs after every test method, including failed tests. The security identity unit test uses it to clear `SecurityContextHolder`, preventing one test's authentication from leaking into the next test.
