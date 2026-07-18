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

All three make a class discoverable during component scanning and normally create one singleton bean for the application context. `@Component` is the general form, `@Service` marks service/use-case logic, and `@Repository` identifies repository implementations and enables translation of supported persistence exceptions. Constructor injection is preferred because required dependencies remain explicit and testable.

## Persistence and Transactions

### `@Entity`, `@Table`, `@Id`, `@Column`, and `@UniqueConstraint`

These Jakarta Persistence annotations tell Hibernate how Java objects map to database tables, identifiers, columns, and uniqueness rules. Hibernate reads the metadata when the persistence context starts and tracks loaded entities within a transaction. Flyway remains the schema source of truth; the mappings must match its SQL migrations. A protected no-argument constructor is required so Hibernate can instantiate an entity.

### `@Enumerated(EnumType.STRING)`

Stores an enum by its constant name, such as `PREFER_NOT_TO_SAY`, instead of its numeric position. String storage is readable and remains stable when enum constants are reordered; renaming a constant still requires a data migration.

### `@Transactional`

Spring wraps calls made through the managed service proxy in a database transaction. A successful call commits; an unchecked exception rolls it back by default. Use it on service operations that must be atomic, such as provisioning an account and profile. Calling a transactional method from another method on the same object bypasses the proxy and is a common pitfall. OwlNest keeps this annotation on service entry points, not repositories. Miss-only repository advisory-lock calls therefore participate in the existing service transaction and release at its commit/rollback; do not invoke those lock ports outside the owning transactional service, put them on established-user fast paths, or hold them across external calls.

### `@Lock` and `@Param`

Spring Data JPA processes `@Lock` on a repository query when the proxy executes it. `PESSIMISTIC_WRITE` asks Hibernate to acquire a database write lock for the selected active post row until the surrounding transaction completes, serializing replace/delete/interaction mutations. It must run inside a transaction; broad or inconsistent lock ordering can reduce throughput or deadlock. `@Param` binds the named method argument to the JPQL placeholder and is resolved for each repository invocation. PostgreSQL concurrency integration tests must cover the lock-dependent invariant instead of assuming the annotation is sufficient.

## HTTP API

### `@RestController`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@DeleteMapping`, `@PathVariable`, and `@RequestParam`

`@RestController` registers the class as an MVC controller whose return values are serialized into the HTTP response body. `@RequestMapping` defines the shared route prefix, while `@GetMapping`, `@PostMapping`, and `@DeleteMapping` select HTTP routes. `@PathVariable` converts a route segment such as `accountId` into the declared method-parameter type before invocation; an invalid UUID is rejected as a bad request. `@RequestParam` binds query parameters during MVC dispatch. The comment collection deliberately binds the complete `MultiValueMap`, hides that transport parameter from springdoc, and validates allowed names/cardinality itself so unknown or repeated pagination parameters fail closed. Spring resolves these mappings during startup and invokes the matching method per request. Controllers should delegate business work to services and return DTOs, not JPA entities.

### `@ResponseStatus`

Sets the successful HTTP status produced by a controller method when no `ResponseEntity` overrides it. Spring MVC applies it after `PresenceController.heartbeat()` returns and sends `204 No Content`. Keep it on controller methods or mapped exceptions, not services, because HTTP status is a transport concern.

### `@PutMapping`, `@RequestBody`, and `@Valid`

`@PutMapping` selects the HTTP PUT operation used for a repeatable complete profile submission. `@RequestBody` deserializes request JSON into the request record. `@Valid` then invokes Jakarta Bean Validation before the controller method runs; invalid input returns `400` without entering the service.

### `@NotBlank`, `@NotNull`, `@Size`, `@Pattern`, and `@Past`

These Bean Validation constraints define input rules declaratively: required non-whitespace text, required nested values, length/cardinality limits, username character rules, and a birth date before today. They validate the API boundary; domain methods still protect essential invariants when called outside HTTP. Collection element constraints such as `List<@NotNull @Valid Media>` are evaluated for every submitted item.

### `@JsonProperty`

Jackson reads `@JsonProperty` while constructing serialization metadata. The post response uses it to keep the exact JSON field `isAuthor` for a Java record boolean component whose bean-style name could otherwise be interpreted differently by tooling. It affects JSON mapping, not authorization; permissions must already be derived in the service and verified through response/OpenAPI tests.

### `@RestControllerAdvice` and `@ExceptionHandler`

`@RestControllerAdvice` registers centralized API exception mapping during startup. `@ExceptionHandler` selects a method when the declared exception escapes a controller. The profile handler converts a username conflict into a `409` Problem Details response without coupling the application exception to Spring MVC.

### `@Tag`, `@Operation`, `@Parameters`, `@Parameter`, `@ApiResponses`, `@ApiResponse`, `@Content`, `@Schema`, `@ArraySchema`, and `@Header`

These Swagger annotations enrich the OpenAPI contract generated by springdoc. `@Tag` groups related controller operations inside one API document, `@Operation` provides a stable operation ID and human-readable behavior, and `@Parameters` groups explicit `@Parameter` declarations such as the comment collection's bounded `limit` and opaque `cursor`. A hidden transport parameter prevents the underlying `MultiValueMap` implementation detail from appearing alongside those public query parameters. Response annotations declare observable HTTP outcomes. `@Content` describes a response media type while `@Schema` identifies its payload model; `@ArraySchema` documents collection/item limits that are intentionally applied after normalization, and `@Header` describes the create response's runtime `Location` header. An empty `@Content` prevents springdoc from copying the success schema onto a response with no documented body. Springdoc reads these annotations when generating `/v3/api-docs/*`; they do not change request handling or validation. Keep them synchronized with controller behavior and integration tests, and avoid documenting responses the application cannot actually return.

### `@SecurityRequirement` and `@SecurityRequirements`

Declare which OpenAPI security schemes a documented operation accepts. OwlNest lists `keycloakOAuth2` browser login and manual `bearerAuth` as alternatives; the container annotation holds both requirements. Swagger UI uses them to show authorization choices and attach a token to Try it out requests. Spring Security still performs the real runtime authentication; these annotations alone never protect an endpoint.

### `@ServiceConnection`

Lets Spring Boot derive connection details from a Testcontainers object and override normal connection properties for the test context. It connects the application `DataSource` to PostgreSQL without hard-coded ports. The Redis test uses `name = "redis"` because Spring Boot cannot infer a service type from a generic container bean's declared return type; the hint selects Redis connection details. This annotation is processed only while the test application context starts and must not be used in production configuration.

### `@AutoConfigureMockMvc`

Adds a `MockMvc` test client backed by the application context without opening a real HTTP port. It still executes the MVC and Spring Security filter chains, so it is useful for authenticated endpoint integration tests.

### `@Autowired`

Requests a dependency from the Spring test context. Production classes use constructor injection; field injection is kept here only to reduce test setup noise. The fields are populated after the test instance is created and before test methods run.

## JUnit

### `@Test`

Marks a method as a JUnit Jupiter test. JUnit discovers and invokes it during `./gradlew test`; it does not create Spring state by itself. Prefer plain `@Test` tests when no Spring context is required.

### `@AfterEach`

Marks cleanup that JUnit runs after every test method, including failed tests. The security identity unit test uses it to clear `SecurityContextHolder`, preventing one test's authentication from leaking into the next test.
