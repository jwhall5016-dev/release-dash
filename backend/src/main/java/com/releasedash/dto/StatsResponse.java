package com.releasedash.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {
    private Long totalReleases;
    private List<String> repositories;
    private List<String> environments;
}
