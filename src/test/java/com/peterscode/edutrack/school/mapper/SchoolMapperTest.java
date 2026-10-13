package com.peterscode.edutrack.school.mapper;

import com.peterscode.edutrack.school.dto.CreateSchoolRequestDto;
import com.peterscode.edutrack.school.dto.PublicSchoolProfileDto;
import com.peterscode.edutrack.school.dto.SchoolResponseDto;
import com.peterscode.edutrack.school.dto.UpdateSchoolProfileRequestDto;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolMapperTest {

    private SchoolMapper schoolMapper;

    @BeforeEach
    void setUp() {
        schoolMapper = new SchoolMapper();
    }

    @Test
    @DisplayName("toEntity assigns default modules based on tier when not explicitly provided")
    void toEntity_AssignsDefaultModulesForTier() {
        CreateSchoolRequestDto dto = CreateSchoolRequestDto.builder()
                .name("Oakwood High")
                .code("owh")
                .subdomain("oakwood")
                .subscriptionTier(SubscriptionTier.BASIC)
                .build();

        School entity = SchoolMapper.toEntity(dto);

        assertThat(entity).isNotNull();
        assertThat(entity.getCode()).isEqualTo("OWH");
        assertThat(entity.getSubdomain()).isEqualTo("oakwood");
        assertThat(entity.getEnabledModules()).contains(
                ServiceModule.STUDENT_MANAGEMENT,
                ServiceModule.TEACHER_MANAGEMENT,
                ServiceModule.ATTENDANCE,
                ServiceModule.GRADING,
                ServiceModule.TIMETABLE
        );
        assertThat(entity.getEnabledModules()).doesNotContain(ServiceModule.BILLING, ServiceModule.LMS);
    }

    @Test
    @DisplayName("toDto maps all entity fields accurately")
    void toDto_MapsAllFields() {
        School school = School.builder()
                .name("Summit Academy")
                .code("SUM")
                .subdomain("summit")
                .city("Denver")
                .country("USA")
                .timezone("America/Denver")
                .currency("USD")
                .subscriptionTier(SubscriptionTier.ENTERPRISE)
                .enabledModules(Set.of(ServiceModule.LMS, ServiceModule.BILLING))
                .active(true)
                .build();
        school.setId(5L);

        SchoolResponseDto dto = schoolMapper.toDto(school);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getName()).isEqualTo("Summit Academy");
        assertThat(dto.getCode()).isEqualTo("SUM");
        assertThat(dto.getCity()).isEqualTo("Denver");
        assertThat(dto.getSubscriptionTier()).isEqualTo(SubscriptionTier.ENTERPRISE);
        assertThat(dto.getEnabledModules()).contains(ServiceModule.LMS, ServiceModule.BILLING);
    }

    @Test
    @DisplayName("toPublicDto maps only non-sensitive public metadata")
    void toPublicDto_MapsPublicFieldsOnly() {
        School school = School.builder()
                .name("Public High")
                .code("PUB")
                .subdomain("pubhigh")
                .logoUrl("https://example.com/logo.png")
                .websiteUrl("https://pubhigh.edu")
                .timezone("UTC")
                .currency("USD")
                .subscriptionTier(SubscriptionTier.ENTERPRISE)
                .active(true)
                .build();
        school.setId(7L);

        PublicSchoolProfileDto dto = schoolMapper.toPublicDto(school);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getName()).isEqualTo("Public High");
        assertThat(dto.getCode()).isEqualTo("PUB");
        assertThat(dto.getSubdomain()).isEqualTo("pubhigh");
        assertThat(dto.getLogoUrl()).isEqualTo("https://example.com/logo.png");
    }

    @Test
    @DisplayName("updateEntityFromProfileDto modifies only mutable profile fields")
    void updateEntityFromProfileDto_Success() {
        School school = School.builder()
                .name("Old Name")
                .code("OLD")
                .subdomain("old")
                .city("Old City")
                .build();

        UpdateSchoolProfileRequestDto updateDto = UpdateSchoolProfileRequestDto.builder()
                .name("New Name")
                .city("New City")
                .timezone("Europe/London")
                .build();

        SchoolMapper.updateEntityFromProfileDto(updateDto, school);

        assertThat(school.getName()).isEqualTo("New Name");
        assertThat(school.getCity()).isEqualTo("New City");
        assertThat(school.getTimezone()).isEqualTo("Europe/London");
        assertThat(school.getCode()).isEqualTo("OLD"); // Unchanged
        assertThat(school.getSubdomain()).isEqualTo("old"); // Unchanged
    }
}
