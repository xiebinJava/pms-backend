package com.brad.pms.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PersonnelServiceTest {

    @Autowired
    private PersonnelService personnelService;

    @Test
    void listWithoutKeywordReturnsPersonnel() {
        assertThat(personnelService.list(null)).isNotEmpty();
    }

    @Test
    void primaryAffiliationChangesRemainSerializedByEmployee() {
        // The row lock is part of the service contract; this assertion protects
        // the mapper method from being replaced with a non-locking lookup.
        try {
            var method = com.brad.pms.mapper.UserMapper.class.getMethod("selectForUpdate", Long.class);
            var select = method.getAnnotation(org.apache.ibatis.annotations.Select.class);
            assertThat(select).isNotNull();
            assertThat(select.value()[0]).containsIgnoringCase("for update");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }
}
