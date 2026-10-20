package com.peterscode.edutrack.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.peterscode.edutrack", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureTest {

    @ArchTest
    public static final ArchRule controllers_should_be_suffixed_and_annotated = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(RestController.class);

    @ArchTest
    public static final ArchRule services_should_be_in_service_packages = classes()
            .that().haveSimpleNameEndingWith("ServiceImpl")
            .should().resideInAPackage("..service..");
}
