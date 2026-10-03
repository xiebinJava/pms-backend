package com.brad.pms.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryNodeWorkbenchPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsEveryVariantWithItsWhitelistedFields() throws Exception {
        assertThatCode(() -> StoryNodeWorkbenchPolicy.validate(
                mapper.readTree("{\"acceptanceCriteria\":\"标准\",\"background\":\"背景\"}"), "writing"))
                .doesNotThrowAnyException();
        assertThatCode(() -> StoryNodeWorkbenchPolicy.validate(
                mapper.readTree("{\"iterationName\":\"迭代一\",\"meetingNote\":\"结论\",\"dependencies\":\"依赖\"}"), "iteration"))
                .doesNotThrowAnyException();
        assertThatCode(() -> StoryNodeWorkbenchPolicy.validate(
                mapper.readTree("{\"implementationNote\":\"说明\",\"selfTestResult\":\"自测\",\"codeLink\":\"https://git.example.com/pr/1\"}"), "development"))
                .doesNotThrowAnyException();
        assertThatCode(() -> StoryNodeWorkbenchPolicy.validate(
                mapper.readTree("{\"acceptanceConclusion\":\"通过\",\"acceptanceNote\":\"备注\"}"), "acceptance"))
                .doesNotThrowAnyException();
        assertThatCode(() -> StoryNodeWorkbenchPolicy.validate(
                mapper.readTree("{\"releaseVersion\":\"1.0.0\",\"releaseWindow\":\"窗口\",\"releaseNote\":\"说明\"}"), "release"))
                .doesNotThrowAnyException();
        assertThatCode(() -> StoryNodeWorkbenchPolicy.validate(
                mapper.readTree("{\"launchDate\":\"2026-10-03\",\"launchVerification\":\"验证\",\"retrospective\":\"复盘\"}"), "launch"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsEmptyOptionalFields() throws Exception {
        assertThatCode(() -> StoryNodeWorkbenchPolicy.validate(
                mapper.readTree("{\"acceptanceCriteria\":\"\",\"background\":\"\"}"), "writing"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"codeLink\":\"javascript:alert(1)\"}",
            "{\"codeLink\":\"https://user:secret@example.com/pr\"}",
            "{\"codeLink\":123}",
            "{\"launchDate\":\"2026-13-40\"}",
            "{\"launchDate\":\"next week\"}",
            "{\"acceptanceCriteria\":123}"
    })
    void rejectsMalformedValues(String json) throws Exception {
        ObjectNode state = (ObjectNode) mapper.readTree(json);
        String variant = state.has("codeLink") ? "development" : state.has("launchDate") ? "launch" : "writing";
        assertThatThrownBy(() -> StoryNodeWorkbenchPolicy.validate(state, variant))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesTextLengthLimits() {
        ObjectNode state = mapper.createObjectNode();
        state.put("acceptanceCriteria", "a".repeat(2001));
        assertThatThrownBy(() -> StoryNodeWorkbenchPolicy.validate(state, "writing"))
                .hasMessageContaining("长度");
        state.put("acceptanceCriteria", "a".repeat(2000));
        assertThatCode(() -> StoryNodeWorkbenchPolicy.validate(state, "writing")).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownVariant() throws Exception {
        assertThat(StoryNodeWorkbenchPolicy.supports("writing")).isTrue();
        assertThat(StoryNodeWorkbenchPolicy.supports("unknown")).isFalse();
        assertThat(StoryNodeWorkbenchPolicy.supports(null)).isFalse();
        assertThatThrownBy(() -> StoryNodeWorkbenchPolicy.validate(mapper.createObjectNode(), "unknown"))
                .hasMessageContaining("类型无效");
    }

    @Test
    void mergeWritesOnlyWhitelistedFieldsAndKeepsServerHistory() throws Exception {
        ObjectNode existing = (ObjectNode) mapper.readTree(
                "{\"background\":\"旧背景\",\"legacyNote\":\"服务端历史\"}");
        ObjectNode incoming = (ObjectNode) mapper.readTree(
                "{\"acceptanceCriteria\":\"新标准\",\"unexpected\":\"丢弃\"}");

        ObjectNode merged = StoryNodeWorkbenchPolicy.merge(existing, incoming, "writing");

        assertThat(merged.path("acceptanceCriteria").asText()).isEqualTo("新标准");
        assertThat(merged.path("background").asText()).isEqualTo("旧背景");
        assertThat(merged.path("legacyNote").asText()).isEqualTo("服务端历史");
        assertThat(merged.has("unexpected")).isFalse();
    }

    @Test
    void mergeRejectsMalformedIncomingBeforeWriting() {
        ObjectNode incoming = mapper.createObjectNode();
        incoming.put("codeLink", "ftp://example.com/file");
        assertThatThrownBy(() -> StoryNodeWorkbenchPolicy.merge(null, incoming, "development"))
                .hasMessageContaining("http");
    }
}
