package com.brad.pms;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PmsApplicationMapperScanTest {

    @Test
    void scansOrchestratorPersistenceMappers() {
        MapperScan mapperScan = PmsApplication.class.getAnnotation(MapperScan.class);

        assertThat(mapperScan).isNotNull();
        assertThat(Stream.concat(Arrays.stream(mapperScan.value()), Arrays.stream(mapperScan.basePackages())).toList())
                .contains("com.brad.pms.mapper", "com.brad.pms.ai.orchestrator.persistence");
    }
}
