package com.validator;

import com.opencsv.CSVReader;
import java.io.BufferedReader;
import java.util.*;

public class ScenarioLoader {
    public static List<Scenario> loadFromCSV(BufferedReader br) throws Exception {
        List<Scenario> scenarios = new ArrayList<>();
        try (CSVReader reader = new CSVReader(br)) {
            String[] line;
            boolean first = true;
            while ((line = reader.readNext()) != null) {
                if (first) {
                    first = false; // header skip
                    continue;
                }
                if (line.length < 5) continue;

                String name = line[0].trim();
                String cmd = line[1].trim();
                String target = line[2].trim();
                String params = line[3].trim();
                String send = line[4].trim();  // 쉼표 포함된 것도 올바르게 파싱됨

                scenarios.add(new Scenario(name, cmd, target, params, send));
            }
        }
        return scenarios;
    }
}