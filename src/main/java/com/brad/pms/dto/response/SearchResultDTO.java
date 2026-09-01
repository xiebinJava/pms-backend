package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SearchResultDTO {

    private List<SearchHitDTO> projects = new ArrayList<>();
    private List<SearchHitDTO> tasks = new ArrayList<>();
    private List<SearchHitDTO> milestones = new ArrayList<>();
    private List<SearchHitDTO> comments = new ArrayList<>();
}
