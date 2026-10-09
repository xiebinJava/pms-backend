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
    void developmentRejectsNonUuidCaseIds() throws Exception {
        var state = mapper.readTree("{\"testCases\":[{\"id\":\"c1\",\"priority\":\"NORMAL\"}]}");
        assertThatThrownBy(() -> StoryNodeWorkbenchPolicy.validate(state, "development")).hasMessageContaining("标识");
    }

    @Test
    void developmentRejectsNonCanonicalUuidToProtectStableHistoryMatching() throws Exception {
        var state = mapper.readTree("{\"testCases\":[{\"id\":\"ABCDEF00-0000-4000-8000-000000000001\",\"priority\":\"NORMAL\"}]}");
        assertThatThrownBy(() -> StoryNodeWorkbenchPolicy.validate(state, "development")).hasMessageContaining("标识");
    }

    @Test
    void developmentRetainsLegacyAndStableCaseHistoryButDropsIncomingUnknownFields() throws Exception {
        var result = StoryNodeWorkbenchPolicy.merge(mapper.readTree("{\"implementationNote\":\"旧方案\",\"testCases\":[{\"id\":\"00000000-0000-4000-8000-000000000001\",\"history\":\"保留\"}]}"),
                mapper.readTree("{\"testCases\":[{\"id\":\"00000000-0000-4000-8000-000000000001\",\"name\":\"退款\",\"priority\":\"HIGH\",\"expectedResult\":\"到账\",\"unknown\":\"丢弃\"}],\"mergeStatus\":\"MERGED\",\"deployEnv\":\"测试环境\"}"), "development");
        assertThat(result.path("implementationNote").asText()).isEqualTo("旧方案");
        assertThat(result.path("testCases").get(0).path("history").asText()).isEqualTo("保留");
        assertThat(result.path("testCases").get(0).has("unknown")).isFalse();
        assertThat(result.path("mergeStatus").asText()).isEqualTo("MERGED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"testCases\":[{\"id\":\"00000000-0000-4000-8000-000000000001\",\"priority\":\"BAD\"}]}", "{\"testCases\":null}", "{\"mergeStatus\":null}", "{\"testCases\":[{\"id\":\"00000000-0000-4000-8000-000000000001\",\"priority\":null}]}"})
    void developmentRejectsMalformedCasesAndStatuses(String json) throws Exception {
        var state = mapper.readTree(json);
        assertThatThrownBy(() -> StoryNodeWorkbenchPolicy.validate(state, "development")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void developmentRejectsMoreThan100CasesAndDuplicateIdsButAllowsDeletingAllCases() throws Exception {
        var state = mapper.createObjectNode(); var cases = state.putArray("testCases");
        for (int i = 0; i < 100; i++) cases.addObject().put("id", String.format("00000000-0000-4000-8000-%012d", i)).put("priority", "NORMAL");
        assertThatCode(() -> StoryNodeWorkbenchPolicy.validate(state, "development")).doesNotThrowAnyException();
        cases.addObject().put("id", "case-101").put("priority", "NORMAL");
        assertThatThrownBy(() -> StoryNodeWorkbenchPolicy.validate(state, "development")).hasMessageContaining("100");
        cases.remove(100); ((ObjectNode) cases.get(99)).put("id", "00000000-0000-4000-8000-000000000000");
        assertThatThrownBy(() -> StoryNodeWorkbenchPolicy.validate(state, "development")).hasMessageContaining("重复");
        assertThat(StoryNodeWorkbenchPolicy.merge(state, mapper.readTree("{\"testCases\":[]}"), "development").path("testCases")).isEmpty();
    }

    @Test
    void writingPersistsCombinedDescriptionAndPriorityWithoutDuplicatingIdentity() throws Exception {
        var merged = StoryNodeWorkbenchPolicy.merge(mapper.readTree("{\"background\":\"旧背景\",\"acceptanceCriteria\":\"旧标准\"}"),
                mapper.readTree("{\"title\":\"故事\",\"baseTitle\":\"旧名\",\"topicId\":\"7\",\"baseTopicId\":\"\",\"descriptionAndAcceptance\":\"新的描述及标准\",\"priority\":\"HIGH\"}"), "writing");
        assertThat(merged.path("descriptionAndAcceptance").asText()).isEqualTo("新的描述及标准");
        assertThat(merged.path("priority").asText()).isEqualTo("HIGH");
        assertThat(merged.path("background").asText()).isEqualTo("旧背景");
        assertThat(merged.has("title")).isFalse();
        assertThat(merged.has("topicId")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"priority\":\"unknown\"}", "{\"topicId\":\"-1\"}", "{\"title\":\"   \"}", "{\"descriptionAndAcceptance\":123}"})
    void rejectsInvalidWritingFields(String json) throws Exception {
        assertThatThrownBy(() -> StoryNodeWorkbenchPolicy.validate(mapper.readTree(json), "writing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void iterationPersistsPlanLinkAndConfirmedPeopleWithoutDroppingHistory() throws Exception {
        var merged = StoryNodeWorkbenchPolicy.merge(
                mapper.readTree("{\"meetingNote\":\"旧结论\"}"),
                mapper.readTree("{\"iterationPlanId\":\"12\",\"developerIds\":[3,5],\"testerIds\":[7],\"unexpected\":\"丢弃\"}"),
                "iteration");
        assertThat(merged.path("iterationPlanId").asText()).isEqualTo("12");
        assertThat(merged.path("developerIds")).hasSize(2);
        assertThat(merged.path("testerIds")).hasSize(1);
        assertThat(merged.path("meetingNote").asText()).isEqualTo("旧结论");
        assertThat(merged.has("unexpected")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"iterationPlanId\":\"-1\"}",
            "{\"iterationPlanId\":\"abc\"}",
            "{\"developerIds\":\"3\"}",
            "{\"testerIds\":[0]}",
            "{\"developerIds\":[1,\"x\"]}"
    })
    void rejectsInvalidIterationFields(String json) throws Exception {
        assertThatThrownBy(() -> StoryNodeWorkbenchPolicy.validate(mapper.readTree(json), "iteration"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsEmptyIterationPlanAndPeople() throws Exception {
        assertThatCode(() -> StoryNodeWorkbenchPolicy.validate(
                mapper.readTree("{\"iterationPlanId\":\"\",\"developerIds\":[],\"testerIds\":[]}"), "iteration"))
                .doesNotThrowAnyException();
    }

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
                mapper.readTree("{\"acceptanceConclusion\":\"PASS\",\"acceptanceNote\":\"备注\"}"), "acceptance"))
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
    void mergeAcceptsNonHttpCodeReferences() {
        ObjectNode incoming = mapper.createObjectNode();
        incoming.put("codeLink", "ftp://example.com/file");
        assertThat(StoryNodeWorkbenchPolicy.merge(null, incoming, "development").path("codeLink").asText())
                .isEqualTo("ftp://example.com/file");
    }
}
