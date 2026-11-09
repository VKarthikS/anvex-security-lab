package com.anvex.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AwarenessNotice(
        String level,
        String title,
        String message,
        String scope,
        List<String> relatedEventIds
) {
    public static AwarenessNotice info(String title, String message) {
        return new AwarenessNotice("info", title, message, "session", List.of());
    }

    public static AwarenessNotice warning(String title, String message, List<String> relatedEventIds) {
        return new AwarenessNotice("warning", title, message, "account", relatedEventIds == null ? List.of() : relatedEventIds);
    }
}
