package com.exemplo.pedidos;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Fitness functions FF-11 e FF-12 (docs/governanca/fitness-functions.md). */
@AnalyzeClasses(packages = "com.exemplo.pedidos", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    @ArchTest
    static final ArchRule ff11_domain_is_isolated = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..adapters..", "..config..",
                    "org.springframework..", "java.sql..", "javax.sql..")
            .allowEmptyShould(true)
            .as("FF-11: domain não depende de application, adapters, Spring nem JDBC");

    @ArchTest
    static final ArchRule ff11_application_does_not_know_adapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapters..", "org.springframework.web..", "java.sql..", "javax.sql..")
            .allowEmptyShould(true)
            .as("FF-11: application não depende de adapters, Spring Web nem JDBC");

    @ArchTest
    static final ArchRule ff11_v1_independent_of_v2 = noClasses()
            .that().resideInAPackage("..adapters.in.web.v1..")
            .should().dependOnClassesThat().resideInAPackage("..adapters.in.web.v2..")
            .allowEmptyShould(true)
            .as("FF-11: adaptador v1 não depende do v2");

    @ArchTest
    static final ArchRule ff11_v2_independent_of_v1 = noClasses()
            .that().resideInAPackage("..adapters.in.web.v2..")
            .should().dependOnClassesThat().resideInAPackage("..adapters.in.web.v1..")
            .allowEmptyShould(true)
            .as("FF-11: adaptador v2 não depende do v1");

    @ArchTest
    static final ArchRule ff12_http_clients_only_from_factory = noClasses()
            .that().doNotHaveFullyQualifiedName("com.exemplo.pedidos.config.HttpClients")
            .should().callMethod("org.springframework.web.client.RestClient", "builder")
            .orShould().callMethod("org.springframework.web.client.RestClient", "create")
            .orShould().callMethod("java.net.http.HttpClient", "newBuilder")
            .orShould().callMethod("java.net.http.HttpClient", "newHttpClient")
            .orShould().callConstructor("org.springframework.web.client.RestTemplate")
            .allowEmptyShould(true)
            .as("FF-12: clientes HTTP só são criados pela fábrica com timeout (config.HttpClients)");
}
