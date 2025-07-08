package com.validator;

import java.util.ArrayList;
import java.util.List;

public class ScenarioResult {
    String result;  // PASS, FAIL, ERROR
    List<String> failLogs = new ArrayList<>();

    ScenarioResult(String result) {
        this.result = result;
    }
}