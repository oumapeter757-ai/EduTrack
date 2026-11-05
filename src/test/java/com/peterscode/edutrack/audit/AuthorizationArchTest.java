package com.peterscode.edutrack.audit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * ArchUnit test that fails when a @RestController handler lacks @PreAuthorize.
 * Webhook controllers with permitAll() are explicitly allowed.
 */
@AnalyzeClasses(packages = "com.peterscode.edutrack", importOptions = {ImportOption.DoNotIncludeTests.class})
class AuthorizationArchTest {

    @ArchTest
    public static final ArchRule everyHandlerHasPreAuthorize = methods()
            .that().areAnnotatedWith(GetMapping.class)
            .or().areAnnotatedWith(PostMapping.class)
            .or().areAnnotatedWith(PutMapping.class)
            .or().areAnnotatedWith(DeleteMapping.class)
            .or().areAnnotatedWith(PatchMapping.class)
            .or().areAnnotatedWith(RequestMapping.class)
            .and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().areDeclaredInClassesThat().haveNameNotMatching(".*(C2bWebhook|MpesaWebhook|Fees)Controller")
            .should().beAnnotatedWith(PreAuthorize.class)
            .because("Every REST endpoint must have explicit authorization");
}
