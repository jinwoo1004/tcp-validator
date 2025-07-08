package com.validator;

import java.util.ArrayList;
import java.util.List;

public class Scenario {
    public String name;
    public String cmd;
    public String target;
    public List<String> params = new ArrayList<>();
    public String send;

    public Scenario(String name, String cmd, String target, String paramsStr, String send) {
        this.name = name;
        this.cmd = cmd;
        this.target = target;
        this.send = send;

        if (paramsStr != null && !paramsStr.isEmpty()) {
            String[] tokens = paramsStr.split(";");
            for (String token : tokens) {
                token = token.trim();
                if (!token.isEmpty()) {
                    params.add(token);
                }
            }
        }
    }
}