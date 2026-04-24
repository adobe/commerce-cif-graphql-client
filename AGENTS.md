# CIF GraphQL Client

Generic GraphQL client for Adobe Experience Manager (AEM), packaged as an OSGi bundle.

## Build

- JDK 8 (CI uses `cimg/openjdk:8.0`; compiler source/target is 1.8)
- `mvn clean install` — build, test, and verify formatting
- `mvn clean install -P format-code` — auto-format code and sort imports before building
- `mvn install -P autoInstallBundle` — build and deploy bundle to local AEM instance

## Testing

- **Unit tests**: JUnit 4 + Mockito 1.x + wcm.io AEM Mock. Run with `mvn test`
- **Coverage**: JaCoCo enforces ≥80% branch coverage; build fails if not met

## Code Style

- **Formatter**: Eclipse-based rules in `eclipse-formatter.xml` (4-space indent, 140-char line width, spaces not tabs). Validated during `verify` phase; auto-fix with `-P format-code`
- **Import order**: `java`, `javax`, `org`, then others; static imports last. Enforced by impsort-maven-plugin; auto-fix with `-P format-code`
- **License headers**: Apache 2.0 header required on all source files. Enforced by apache-rat-plugin during `compile` phase

## Module Map

Single-module project (`bundle` packaging).

| Package | Description |
|---------|-------------|
| `c.a.c.c.graphql.client` | Public API — `GraphqlClient` interface, request/response types, configuration |
| `c.a.c.c.graphql.client.impl` | OSGi component implementation — HTTP client, caching, metrics, adapter factory |
| `c.a.c.c.graphql.client.impl.circuitbreaker` | Failsafe-based circuit breaker policies (server error, timeout, unavailable) |

*`c.a.c.c` = `com.adobe.cq.commerce`*

## Architecture

- **OSGi Declarative Services**: `GraphqlClientImpl` is a factory component configured via `GraphqlClientConfiguration` (OSGi metatype annotation interface). Instances are registered with an `identifier` service property used to look up clients from JCR resources
- **Adapter pattern**: `GraphqlClientAdapterFactory` adapts Sling resources to `GraphqlClient` by matching the `cq:graphqlClient` resource property to the client identifier
- **HTTP**: Apache HttpClient 4.x with pooled connection manager. Configurable timeouts, keep-alive, TLS settings
- **Caching**: Guava `Cache` keyed by request + options. Cache names, sizes, and TTLs configured per-client via OSGi. Mutations bypass cache. `CacheInvalidator` supports selective invalidation by store view, cache name, or pattern
- **Fault tolerance**: `FaultTolerantExecutor` uses Failsafe circuit breaker (optional, enabled via config). `DefaultExecutor` is the non-resilient alternative
- **Metrics**: Dropwizard Metrics for HTTP connection pool and cache stats, wired via optional OSGi reference to a named `MetricRegistry`
- **Serialization**: Gson for JSON request/response marshalling. Response is generic: `GraphqlResponse<T, U>` where T = data type, U = error type
