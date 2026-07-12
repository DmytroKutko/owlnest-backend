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

### `@ServiceConnection`

Lets Spring Boot derive connection details from a Testcontainers object and override normal connection properties for the test context. Here it connects the application `DataSource` to PostgreSQL without hard-coded ports.

## JUnit

### `@Test`

Marks a method as a JUnit Jupiter test. JUnit discovers and invokes it during `./gradlew test`; it does not create Spring state by itself. Prefer plain `@Test` tests when no Spring context is required.
