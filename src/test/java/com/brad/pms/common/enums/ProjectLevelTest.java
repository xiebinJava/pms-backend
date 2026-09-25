package com.brad.pms.common.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectLevelTest {

    @Test
    void exposesStableImportanceLevels() {
        assertThat(ProjectLevel.ROUTINE.getCode()).isZero();
        assertThat(ProjectLevel.IMPORTANT.getCode()).isEqualTo(1);
        assertThat(ProjectLevel.KEY.getCode()).isEqualTo(2);
        assertThat(ProjectLevel.STRATEGIC.getCode()).isEqualTo(3);
        assertThat(ProjectLevel.labelOf(1)).isEqualTo("重要项目（B）");
        assertThat(ProjectLevel.labelOf(3)).isEqualTo("战略项目（S）");
        assertThat(ProjectLevel.labelOf(99)).isEqualTo("99");
    }
}
