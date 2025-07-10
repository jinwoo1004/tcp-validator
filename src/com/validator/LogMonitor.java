package com.validator;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

public class LogMonitor implements Runnable {

    private final List<Scenario> scenarios;
    private final String logFilePath;
    private final Map<String, String> results = new LinkedHashMap<>();
    private final Map<String, String> remarks = new HashMap<>();

    public LogMonitor(List<Scenario> scenarios, String logFilePath) {
        this.scenarios = scenarios;
        this.logFilePath = logFilePath;
    }

    public Map<String, String> getResults() {
        return results;
    }

    public Map<String, String> getRemarks() {
        return remarks;
    }

    @Override
    public void run() {
        results.clear();

        List<String> allLines = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(logFilePath), Charset.forName("EUC-KR")))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    allLines.add(line);
                }
            }
        } catch (Exception e) {
            results.put("ERROR", "로그 파일 읽기 실패: " + e.getMessage());
            return;
        }

        for (Scenario sc : scenarios) {
            boolean matched = false;
            String send = "";
            boolean scenarioHasMode = sc.params.stream().anyMatch(p -> p.startsWith("#mode="));

            for (int i = 0; i < allLines.size(); i++) {
                String line = allLines.get(i);
                int lineNo = i + 1;
                // #err= 포함된 로그는 무시
                if (line.contains("#err=") || line.contains("2270&NoData")) {
                    continue;
                }
                if (!line.contains("[R:") && !line.contains("[S:")) {
                    continue;
                }
                
                boolean lineHasMode = line.contains("#mode=");
                if (scenarioHasMode != lineHasMode) continue;

                if (!line.contains("$cmd=" + sc.cmd) || !line.contains("$target=" + sc.target)) {
                    continue;
                }

                boolean paramsOk = true;
                for (String param : sc.params) {
                    if (!line.contains(param)) {
                        paramsOk = false;
                        break;
                    }
                }
                if (!paramsOk) continue;

                send = extractSendPart(line);
                if (send == null) continue;

                String key = sc.name + " (Line " + lineNo + ")";
                if (!checkOrderedVariables(sc.send, send)) {
                    results.put(key, "FAIL: 변수 순서 불일치");
                    remarks.put(key, "⛔ 예상 " + extractVariableSummary(sc.send) +
                            " / 실제 " + extractVariableSummary(send) + "\n" + send);
                    continue;
                }

                results.put(key, "PASS");
                remarks.put(key, "");
                matched = true;
                break;
            }

            if (!matched) {
                results.put(sc.name, "FAIL: 시나리오 조건 일치 로그 없음");
                remarks.put(sc.name, "⛔ 로그 " + send);
            }
        }
    }

    private String extractSendPart(String line) {
        int startIdx = line.indexOf("<start=");
        String sendPart;
        if (startIdx == -1) {
            int dollarIndex = line.indexOf("$cmd=");
            if (dollarIndex == -1) return null;
            sendPart = line.substring(dollarIndex);
        } else {
            sendPart = line.substring(startIdx);
        }
        sendPart = sendPart.trim();

        int tabIdx = sendPart.indexOf('\t');
        if (tabIdx != -1) {
            sendPart = sendPart.substring(0, tabIdx);
        } else {
            int spaceIdx = sendPart.indexOf(' ');
            if (spaceIdx != -1) {
                sendPart = sendPart.substring(0, spaceIdx);
            }
        }

        // --- 여기에 첫번째 #no= 이후 모두 자르기 추가 ---
        int firstNoIdx = sendPart.indexOf("#no=");
        if (firstNoIdx != -1) {
            // #no=로 시작하는 첫 번째 위치에서 잘라냄
            // 여기서 자르는 범위는 첫 #no=가 나온 위치부터 그 다음 #no=들 무시하려는 것
            // 즉, 첫 #no=가 나오면 그 뒤부터 다 잘라야 하므로
            // 첫 #no= 이후 중복 제거니까, 뒤에 #no=2, #no=3 등 무조건 무시

            // 다만, 질문 주신 내용 보면 "첫번째 #no=는 포함"
            // 그래서 #no=부터 그 뒤에 또 #no=가 있으면 그 뒤는 잘라야함

            // 따라서, #no=가 여러개면 첫번째 #no= 이후부터 다시 #no=가 있는 위치를 찾아 자름
            // 하지만 #no=는 첫번째만 살리고 뒤는 자름

            // 이 부분을 위해 #no= 위치 찾기
            // 사실 뒤의 #no=는 두번째 #no=부터 시작하니
            // 다시 첫번째 #no=부터 뒤에 또 #no=가 있는지 체크

            // 첫번째 #no= 위치 이후 substring을 다시 검사해서 두번째 #no= 찾기
            int secondNoIdx = sendPart.indexOf("#no=", firstNoIdx + 1);

            if (secondNoIdx != -1) {
                // 두번째 #no=부터 자름
                sendPart = sendPart.substring(0, secondNoIdx).trim();
            } else {
                // 두번째 #no= 없으면 첫번째 #no=부터 끝까지 유지
                // 그대로 둠
            }
        }

        return sendPart;
    }

    private boolean checkOrderedVariables(String expectedSend, String actualSend) {
        List<String> expectedDollarVars = extractOrderedVariableNames(expectedSend, '$');
        List<String> actualDollarVars = extractOrderedVariableNames(actualSend, '$');
        if (!expectedDollarVars.equals(actualDollarVars)) return false;

        List<String> expectedHashVars = extractOrderedVariableNames(expectedSend, '#');
        List<String> actualHashVars = extractOrderedVariableNames(actualSend, '#');
        return expectedHashVars.equals(actualHashVars);
    }

    private List<String> extractOrderedVariableNames(String send, char delimiter) {
        List<String> varNames = new ArrayList<>();
        int pos = 0;

        while (pos < send.length()) {
            int idx = send.indexOf(delimiter, pos);
            if (idx == -1) break;

            int nextDollar = send.indexOf('$', idx + 1);
            int nextHash = send.indexOf('#', idx + 1);

            int nextIdx = send.length();
            if (nextDollar != -1) nextIdx = Math.min(nextIdx, nextDollar);
            if (nextHash != -1) nextIdx = Math.min(nextIdx, nextHash);

            String token = send.substring(idx + 1, nextIdx).trim();
            int eq = token.indexOf('=');
            if (eq != -1) {
                String varName = token.substring(0, eq).trim().replaceAll("[\\u200B\\uFEFF]", "");
                varNames.add(varName);
            }

            pos = nextIdx;
        }

        return varNames;
    }

    private String extractVariableSummary(String send) {
        List<String> dollarVars = extractOrderedVariableNames(send, '$');
        List<String> hashVars = extractOrderedVariableNames(send, '#');
        return "[$" + String.join(", $", dollarVars) + "] [#" + String.join(", #", hashVars) + "]";
    }

    public void setProgressListener(Object object) {
        // 추후 UI 연동용
    }
}