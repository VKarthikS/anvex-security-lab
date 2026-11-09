package com.anvex.simulator.api;

import com.anvex.common.RunSnapshot;
import com.anvex.common.RunConfig;
import com.anvex.common.ScenarioDescriptor;
import com.anvex.simulator.run.RunManager;
import com.anvex.simulator.scenario.ScenarioCatalog;
import com.anvex.simulator.live.RunLiveHub;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RunController {

    private final RunManager runManager;
    private final ScenarioCatalog scenarioCatalog;
    private final RunLiveHub runLiveHub;

    public RunController(RunManager runManager, ScenarioCatalog scenarioCatalog, RunLiveHub runLiveHub) {
        this.runManager = runManager;
        this.scenarioCatalog = scenarioCatalog;
        this.runLiveHub = runLiveHub;
    }

    @GetMapping("/scenarios")
    public List<ScenarioDescriptor> scenarios() {
        return scenarioCatalog.descriptors();
    }

    @PostMapping("/runs")
    public ResponseEntity<RunSnapshot> start(@Valid @RequestBody RunConfig config) {
        RunSnapshot snapshot = runManager.start(config);
        return ResponseEntity.ok(snapshot);
    }

    @PostMapping("/runs/manual")
    public RunSnapshot manualLogin(@Valid @RequestBody SimulatorApiRequests.ManualLoginRequest request) {
        return runManager.manualLogin(request.username(), request.password());
    }

    @PostMapping("/runs/{runId}/stop")
    public RunSnapshot stop(@PathVariable String runId) {
        return runManager.stop(runId);
    }

    @GetMapping("/runs/current")
    public ResponseEntity<RunSnapshot> current() {
        return runManager.current()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).build());
    }

    @GetMapping(value = "/runs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return runLiveHub.subscribe();
    }

    @GetMapping("/runs")
    public List<RunSnapshot> runs() {
        return runManager.history();
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<RunSnapshot> run(@PathVariable String runId) {
        return runManager.find(runId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
