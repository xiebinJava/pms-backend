package com.brad.pms.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopicDevelopmentTestingPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void acceptsOptionalFieldsAndAnEmptyDraftIssue() throws Exception {
        var state = mapper.readTree("""
            {"buildVersion":"","testStatus":"NOT_STARTED","reportUrl":"","residualIssues":[{"id":"draft","description":""}]}
            """);
        assertThatCode(() -> TopicDevelopmentTestingPolicy.validate(state)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"reportUrl\":123}", "{\"testStatus\":\"UNKNOWN\"}", "{\"residualIssues\":{}}",
            "{\"residualIssues\":[{\"id\":\"duplicate\"},{\"id\":\"duplicate\"}]}",
            "{\"residualIssues\":[{\"id\":\"\",\"description\":\"问题\"}]}"})
    void rejectsMalformedRecords(String json) throws Exception {
        var state = mapper.readTree(json);
        assertThatThrownBy(() -> TopicDevelopmentTestingPolicy.validate(state)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void acceptsReportReferencesWithoutProtocolValidation() throws Exception {
        var state = mapper.readTree("{\"reportUrl\":\"内部文档/测试报告\"}");
        assertThatCode(() -> TopicDevelopmentTestingPolicy.validate(state)).doesNotThrowAnyException();
    }

    @Test void enforcesIssueAndTextLimits() {
        var state = mapper.createObjectNode();
        state.put("buildVersion", "a".repeat(201));
        assertThatThrownBy(() -> TopicDevelopmentTestingPolicy.validate(state)).hasMessageContaining("构建版本");
        state.remove("buildVersion");
        var issues = state.putArray("residualIssues");
        for (int i = 0; i < 100; i++) issues.addObject().put("id", "issue-" + i).put("description", "正常");
        assertThatCode(() -> TopicDevelopmentTestingPolicy.validate(state)).doesNotThrowAnyException();
        issues.addObject().put("id", "issue-100");
        assertThatThrownBy(() -> TopicDevelopmentTestingPolicy.validate(state)).hasMessageContaining("100");
    }
}
