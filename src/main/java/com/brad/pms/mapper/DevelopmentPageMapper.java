package com.brad.pms.mapper;

import com.brad.pms.dto.request.DevelopmentItemPageQry;
import com.brad.pms.dto.response.DevelopmentStoryListDTO;
import com.brad.pms.dto.response.DevelopmentTopicListDTO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

public interface DevelopmentPageMapper {
    record Scope(DevelopmentItemPageQry query, List<Long> projects, String keyword, String additionalMatches, String excludedMatches) {
        public Scope(DevelopmentItemPageQry query, List<Long> projects) {
            this(query, projects, StringUtils.hasText(query.getKeyword())
                    ? query.getKeyword().trim().toLowerCase(Locale.ROOT) : null, "[]", "[]");
        }

        public Scope withAdjustedMatches(String additional, String excluded) {
            return new Scope(query, projects, keyword, additional, excluded);
        }

        public long offset() {
            return (long) (query.getCurrPage() - 1) * query.getPageSize();
        }
    }

    List<Long> deletedTopicProjects(@Param("projectId") Long projectId);

    record SearchText(long id, String title, String projectName, String projectCode, String nodeName,
                      String ownerName, String topicTitle, boolean sqlMatch) {}

    Cursor<SearchText> unicodeTopicSearchText(Scope scope);

    Cursor<SearchText> unicodeStorySearchText(Scope scope);

    long countTopics(Scope scope);
    List<Long> topicIds(Scope scope);
    long countStories(Scope scope);
    List<Long> storyIds(Scope scope);

    List<DevelopmentTopicListDTO> topics(@Param("ids") List<Long> ids, @Param("projects") List<Long> projects);
    List<DevelopmentStoryListDTO> stories(@Param("ids") List<Long> ids, @Param("projects") List<Long> projects);
}
