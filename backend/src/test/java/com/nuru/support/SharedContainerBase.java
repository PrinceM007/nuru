package com.nuru.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests: boots the full app against a real SQL Server 2022
 * (same image as local dev) with Flyway migrations applied.
 *
 * <p>A single container is shared by the whole test JVM to keep resource usage
 * predictable (SQL Server needs ~2 GB per instance). Tests use random
 * emails/ids so accumulated rows never collide.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class SharedContainerBase {

  private static final MSSQLServerContainer<?> SQLSERVER =
      new MSSQLServerContainer<>(
          DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
          .acceptLicense();

  static {
    SQLSERVER.start();
  }

  @DynamicPropertySource
  static void sqlServerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", SQLSERVER::getJdbcUrl);
    registry.add("spring.datasource.username", SQLSERVER::getUsername);
    registry.add("spring.datasource.password", SQLSERVER::getPassword);
  }
}
