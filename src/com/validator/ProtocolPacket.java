package com.validator;

public class ProtocolPacket {
    public String headerRaw = "";
    public String bodyRaw = "";

    public static ProtocolPacket parse(String msg) {
        ProtocolPacket pkt = new ProtocolPacket();
        if (msg == null) return pkt;

        String[] parts = msg.split("#", 2);
        pkt.headerRaw = parts[0].trim();
        pkt.bodyRaw = parts.length > 1 ? parts[1].trim() : "";
        return pkt;
    }
}