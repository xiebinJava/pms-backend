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
}
