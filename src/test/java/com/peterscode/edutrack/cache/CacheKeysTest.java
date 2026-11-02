package com.peterscode.edutrack.cache;

import com.peterscode.edutrack.cache.config.CacheKeys;
import com.peterscode.edutrack.cache.config.EdutrackCacheProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CacheKeys key generation.
 * No Redis required — pure logic tests.
 */
class CacheKeysTest {

    private CacheKeys cacheKeys;

    @BeforeEach
    void setUp() {
        EdutrackCacheProperties properties = new EdutrackCacheProperties();
        properties.setSchemaVersion(1);
        cacheKeys = new CacheKeys("dev", properties);
    }

    @Nested
    @DisplayName("Tenant-scoped keys")
    class TenantKeys {

        @Test
        @DisplayName("Builds key with schoolId")
        void buildsTenantKey() {
            String key = cacheKeys.tenant("school-profile", 42L);
            assertThat(key).isEqualTo("edutrack:dev:v1:school-profile:42");
        }

        @Test
        @DisplayName("Builds key with schoolId and additional id")
        void buildsTenantKeyWithId() {
            String key = cacheKeys.tenant("fee-structure", 42L, 99L);
            assertThat(key).isEqualTo("edutrack:dev:v1:fee-structure:42:99");
        }

        @Test
        @DisplayName("Builds key with schoolId and two additional ids")
        void buildsTenantKeyWithTwoIds() {
            String key = cacheKeys.tenant("timetable-teacher", 42L, 7L, 1L);
            assertThat(key).isEqualTo("edutrack:dev:v1:timetable-teacher:42:7:1");
        }

        @Test
        @DisplayName("Builds key with schoolId and string sub-key")
        void buildsTenantKeyWithSubKey() {
            String key = cacheKeys.tenant("borrowing-policy", 42L, "STUDENT");
            assertThat(key).isEqualTo("edutrack:dev:v1:borrowing-policy:42:STUDENT");
        }

        @Test
        @DisplayName("Rejects null schoolId")
        void rejectsNullSchoolId() {
            assertThatThrownBy(() -> cacheKeys.tenant("school-profile", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("schoolId must not be null");
        }

        @Test
        @DisplayName("Rejects null additional id")
        void rejectsNullId() {
            assertThatThrownBy(() -> cacheKeys.tenant("fee-structure", 42L, (Long) null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("id must not be null");
        }

        @Test
        @DisplayName("Rejects blank sub-key")
        void rejectsBlankSubKey() {
            assertThatThrownBy(() -> cacheKeys.tenant("borrowing-policy", 42L, ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("subKey must not be blank");
        }
    }

    @Nested
    @DisplayName("Global keys")
    class GlobalKeys {

        @Test
        @DisplayName("Builds global key with identifier")
        void buildsGlobalKey() {
            String key = cacheKeys.global("school-public", "alpha");
            assertThat(key).isEqualTo("edutrack:dev:v1:school-public:alpha");
        }

        @Test
        @DisplayName("Rejects null identifier")
        void rejectsNullIdentifier() {
            assertThatThrownBy(() -> cacheKeys.global("school-public", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("identifier must not be blank");
        }

        @Test
        @DisplayName("Rejects blank identifier")
        void rejectsBlankIdentifier() {
            assertThatThrownBy(() -> cacheKeys.global("school-public", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("identifier must not be blank");
        }
    }

    @Nested
    @DisplayName("Tenant isolation")
    class TenantIsolation {

        @Test
        @DisplayName("Different schools produce different keys")
        void differentSchoolsDifferentKeys() {
            String key1 = cacheKeys.tenant("school-profile", 1L);
            String key2 = cacheKeys.tenant("school-profile", 2L);
            assertThat(key1).isNotEqualTo(key2);
        }

        @Test
        @DisplayName("Same school different caches produce different keys")
        void sameSchoolDifferentCaches() {
            String key1 = cacheKeys.tenant("school-profile", 42L);
            String key2 = cacheKeys.tenant("classlevels", 42L);
            assertThat(key1).isNotEqualTo(key2);
        }

        @Test
        @DisplayName("Key format contains school id")
        void keyContainsSchoolId() {
            String key = cacheKeys.tenant("school-profile", 42L);
            assertThat(key).contains(":42");
        }
    }

    @Nested
    @DisplayName("Schema versioning")
    class SchemaVersioning {

        @Test
        @DisplayName("Key contains schema version")
        void keyContainsSchemaVersion() {
            String key = cacheKeys.tenant("school-profile", 42L);
            assertThat(key).contains("v1");
        }

        @Test
        @DisplayName("Different schema versions produce different keys")
        void differentSchemaVersions() {
            EdutrackCacheProperties propsV2 = new EdutrackCacheProperties();
            propsV2.setSchemaVersion(2);
            CacheKeys v2Keys = new CacheKeys("dev", propsV2);

            String keyV1 = cacheKeys.tenant("school-profile", 42L);
            String keyV2 = v2Keys.tenant("school-profile", 42L);

            assertThat(keyV1).contains("v1");
            assertThat(keyV2).contains("v2");
            assertThat(keyV1).isNotEqualTo(keyV2);
        }
    }

    @Nested
    @DisplayName("Environment isolation")
    class EnvironmentIsolation {

        @Test
        @DisplayName("Different environments produce different keys")
        void differentEnvironments() {
            CacheKeys prodKeys = new CacheKeys("prod", new EdutrackCacheProperties());
            String devKey = cacheKeys.tenant("school-profile", 42L);
            String prodKey = prodKeys.tenant("school-profile", 42L);

            assertThat(devKey).contains("edutrack:dev:");
            assertThat(prodKey).contains("edutrack:prod:");
            assertThat(devKey).isNotEqualTo(prodKey);
        }
    }

    @Nested
    @DisplayName("Scan prefixes")
    class ScanPrefixes {

        @Test
        @DisplayName("Scan prefix for specific cache and school")
        void scanPrefixForCache() {
            String prefix = cacheKeys.scanPrefix("school-profile", 42L);
            assertThat(prefix).isEqualTo("edutrack:dev:v1:school-profile:42");
        }

        @Test
        @DisplayName("Scan prefix for all caches of a school")
        void scanPrefixAll() {
            String prefix = cacheKeys.scanPrefixAll(42L);
            assertThat(prefix).isEqualTo("edutrack:dev:v1:*:42");
        }
    }
}
