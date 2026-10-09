package com.brad.pms.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class NodeIterationPlanCmd {

    private Long id;

    private Long systemId;

    @JsonIgnore
    private boolean systemIdSpecified;

    private Long systemVersionId;

    /** Distinguishes an omitted legacy field from an explicit request to clear it. */
    @JsonIgnore
    private boolean systemVersionIdSpecified;

    @Size(max = 200)
    private String name;

    private Long ownerId;

    @Size(max = 500)
    private String goal;

    @Size(max = 20)
    private String status;

    private LocalDate startDate;

    private LocalDate dueDate;

    @Min(0)
    @Max(1000)
    private Integer sort;

    public void setSystemVersionId(Long systemVersionId) {
        this.systemVersionId = systemVersionId;
        this.systemVersionIdSpecified = true;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
        this.systemIdSpecified = true;
    }

    @JsonIgnore
    public boolean isSystemVersionIdSpecified() {
        return systemVersionIdSpecified;
    }
}
