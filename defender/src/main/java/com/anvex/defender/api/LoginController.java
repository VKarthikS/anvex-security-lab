package com.anvex.defender.api;

import com.anvex.common.LoginRequest;
import com.anvex.common.LoginResponse;
import com.anvex.defender.security.LoginPipeline;
import com.anvex.defender.security.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    private final LoginPipeline loginPipeline;

    public LoginController(LoginPipeline loginPipeline) {
        this.loginPipeline = loginPipeline;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String requestId = UUID.randomUUID().toString();
        String sourceIp = RequestSupport.remoteAddress(servletRequest);

        LoginResult result = loginPipeline.process(request, sourceIp, requestId);

        LoginResponse body = new LoginResponse(
                result.status(),
                result.requestId(),
                result.serverTime(),
                result.user(),
                result.awareness(),
                result.retryAfterSeconds());

        ResponseEntity.BodyBuilder response = ResponseEntity.status(httpStatusFor(result.status()))
                .header("X-ANVEX-Request-Id", result.requestId())
                .header("X-ANVEX-Source-Ip", sourceIp);
        if (result.retryAfterSeconds() != null && result.retryAfterSeconds() > 0) {
            response.header("Retry-After", String.valueOf(result.retryAfterSeconds()));
        }
        return response.body(body);
    }

    private static HttpStatus httpStatusFor(String status) {
        if (LoginResponse.RATE_LIMITED.equals(status) || LoginResponse.SOURCE_BLOCKED.equals(status)) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (LoginResponse.SERVICE_OVERLOADED.equals(status)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.OK;
    }
}
