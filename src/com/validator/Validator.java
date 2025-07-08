package com.validator;

import java.util.ArrayList;
import java.util.List;

public class Validator {
    public static List<String> validate(ProtocolPacket expected, ProtocolPacket actual) {
        List<String> result = new ArrayList<>();

        if (!expected.headerRaw.equals(actual.headerRaw)) {
            result.add("❌ Header 불일치\n예상: " + expected.headerRaw + "\n실제: " + actual.headerRaw);
        }

        if (!expected.bodyRaw.equals(actual.bodyRaw)) {
            result.add("❌ Body 불일치\n예상: " + expected.bodyRaw + "\n실제: " + actual.bodyRaw);
        }

        return result;
    }
}
