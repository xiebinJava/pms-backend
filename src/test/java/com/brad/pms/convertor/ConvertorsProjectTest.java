package com.brad.pms.convertor;

import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.ProjectDO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConvertorsProjectTest {

    @Test
    void exposesProjectLevelInTheProjectContract() {
        ProjectDO project = new ProjectDO();
        project.setProjectLevel(2);

        ProjectDTO dto = Convertors.toProject(project, null, null, null, 0, 0, 0, 0);

        assertThat(dto.getProjectLevel()).isEqualTo(2);
    }
}
