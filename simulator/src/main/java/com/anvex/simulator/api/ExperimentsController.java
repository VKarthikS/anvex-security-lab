package com.anvex.simulator.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.anvex.common.ComparisonReport;
import com.anvex.simulator.experiment.ExperimentService;

/**
 * Retained OFF-versus-ON comparison reports. Creating a comparison never deletes the runs it
 * compares: both run reports stay available in the run history.
 */
@RestController
@RequestMapping("/api/v1/experiments")
public class ExperimentsController {

    private final ExperimentService experimentService;

    public ExperimentsController(ExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @GetMapping
    public List<ComparisonReport> reports() {
        return experimentService.reports();
    }

    @PostMapping
    public ComparisonReport create(@RequestBody Map<String, String> body) {
        String baselineRunId = body.get("baselineRunId");
        String candidateRunId = body.get("candidateRunId");
        if (baselineRunId == null || baselineRunId.isBlank() || candidateRunId == null || candidateRunId.isBlank()) {
            throw new IllegalArgumentException("baselineRunId and candidateRunId are required");
        }
        return experimentService.compare(baselineRunId, candidateRunId);
    }
}
