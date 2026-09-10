package com.brad.pms.ops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessSpecificationDocsTest {

    @Test
    void documentsTheSolutionDesignDecisionGate() throws Exception {
        String spec = Files.readString(Path.of("docs/business-specification.md"));
        assertThat(spec).contains("方案设计、评审与决策");
        assertThat(spec).contains("三类评审");
        assertThat(spec).contains("决策确认后锁定");
    }
}
