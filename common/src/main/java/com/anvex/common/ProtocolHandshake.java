package com.anvex.common;

public record ProtocolHandshake(
        String product,
        String version,
        String apiVersion,
        String protocol,
        boolean compatible
) {
    public static ProtocolHandshake compatibleWith(Identity identity) {
        boolean ok = "ANVEX".equals(identity.product())
                && "anvex/1".equals(identity.protocol())
                && "v1".equals(identity.apiVersion());
        return new ProtocolHandshake(identity.product(), identity.version(), identity.apiVersion(), identity.protocol(), ok);
    }
}
