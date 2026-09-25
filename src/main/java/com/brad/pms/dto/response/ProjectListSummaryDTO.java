package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectListSummaryDTO {

    private long total;
    private long active;
    private long overdue;
    private long noManager;
    private long staleNode;
    private List<ManagerOption> managers = new ArrayList<>();
    private List<NodeOption> currentNodes = new ArrayList<>();

    @Data
    public static class ManagerOption {
        private Long id;
        private String name;
    }

    @Data
    public static class NodeOption {
        private String key;
        private String name;
    }
}
