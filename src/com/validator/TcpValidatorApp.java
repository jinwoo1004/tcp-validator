package com.validator;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.ListSelectionListener;

import java.awt.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class TcpValidatorApp extends JFrame {
    private JTable resultTable;
    private DefaultTableModel tableModel;
    private JTable summaryTable;
    private DefaultTableModel summaryTableModel;

    private JButton startButton, saveButton, logMonitorButton, extractButton, loadScenarioButton;
    private JLabel statusLabel, summaryLabel;
    private JProgressBar progressBar;
    private List<Scenario> scenarios = new ArrayList<>();
    private JButton maskIpButton;

    private JComboBox<String> resultFilter;
    private JComboBox<String> nameFilter;

    private boolean isUpdatingNameFilter = false;

    public TcpValidatorApp() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            System.err.println("❌ FlatLaf 설정 실패: " + e.getMessage());
        }

        setTitle("TCP 자동 검수 프로그램");
        setSize(1100, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        logMonitorButton = new JButton("로그 기반 검수");
        logMonitorButton.addActionListener(e -> startLogMonitoring());

        saveButton = new JButton("검수 결과 저장");
        saveButton.addActionListener(e -> saveResultToExcel());

        extractButton = new JButton("시나리오 자동 생성");
        extractButton.addActionListener(e -> extractScenariosFromLog());

        loadScenarioButton = new JButton("시나리오 불러오기");
        loadScenarioButton.addActionListener(e -> loadScenarioFromFile());
        
        maskIpButton = new JButton("로그 IP 마스킹");
        maskIpButton.addActionListener(e -> maskSensitiveInfo());
        
        resultFilter = new JComboBox<>(new String[]{"전체", "PASS", "FAIL", "ERROR"});
        resultFilter.addActionListener(e -> applyFilters());

        nameFilter = new JComboBox<>(new String[]{"전체"});
        nameFilter.addActionListener(e -> applyFilters());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(saveButton);
        topPanel.add(logMonitorButton);
        topPanel.add(extractButton);
        topPanel.add(loadScenarioButton);
        topPanel.add(new JLabel("결과 "));
        topPanel.add(resultFilter);
        topPanel.add(new JLabel("시나리오 "));
        topPanel.add(nameFilter);
        topPanel.add(maskIpButton);
        add(topPanel, BorderLayout.NORTH);

        statusLabel = new JLabel("준비됨");
        summaryLabel = new JLabel("총 시나리오: 0, PASS: 0, FAIL: 0, ERROR: 0");
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setIndeterminate(true);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        infoPanel.add(statusLabel);
        infoPanel.add(summaryLabel);
        bottomPanel.add(infoPanel, BorderLayout.WEST);
        bottomPanel.add(progressBar, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        tableModel = new DefaultTableModel(new String[]{"시나리오", "결과", "비고", "cmd", "target"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        resultTable = new JTable(tableModel);
        resultTable.setAutoCreateRowSorter(true);

        TableColumnModel colModel = resultTable.getColumnModel();
        colModel.getColumn(3).setMinWidth(0);
        colModel.getColumn(3).setMaxWidth(0);
        colModel.getColumn(3).setWidth(0);
        colModel.getColumn(4).setMinWidth(0);
        colModel.getColumn(4).setMaxWidth(0);
        colModel.getColumn(4).setWidth(0);

        summaryTableModel = new DefaultTableModel(new String[]{"시나리오", "전체", "PASS", "FAIL", "ERROR"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        summaryTable = new JTable(summaryTableModel);
        summaryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        summaryTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = summaryTable.getSelectedRow();
                if (selectedRow >= 0) {
                    int modelRow = summaryTable.convertRowIndexToModel(selectedRow);
                    String scenarioName = (String) summaryTableModel.getValueAt(modelRow, 0);
                    if (scenarioName != null && !scenarioName.isEmpty()) {
                        applyNameFilterLike(scenarioName);
                    }
                }
            }
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(summaryTable), new JScrollPane(resultTable));
        splitPane.setDividerLocation(150);
        add(splitPane, BorderLayout.CENTER);

        loadScenarios("scenarios/extracted_scenarios.csv");
    }
    
    // 로그 파일에서 민감정보(IP 등) 마스킹 후 저장 (Java 1.8 호환)
    private void maskSensitiveInfo() {
        JFileChooser chooser = new JFileChooser(".");
        chooser.setDialogTitle("로그 파일 선택");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File inputFile = chooser.getSelectedFile();
        String outputFilePath = inputFile.getAbsolutePath().replaceAll("\\.txt$", "") + "_masked.txt";

        // 패턴 리스트 (Java 8 호환용)
        List<Pattern> simpleReplacements = Arrays.asList(
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),                    // IP
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}:\\d+\\b")                 // IP:Port
        );

        List<Pattern> keyValuePatterns = Arrays.asList(
            Pattern.compile("(?i)(danji=)[^#&\\s]+"),
            Pattern.compile("(?i)(dongho=)[^#&\\s]+"),
            Pattern.compile("(?i)(copy=)[^#&\\s]+"),
            Pattern.compile("(?i)(hwversion=)[^#&\\s]+"),
            Pattern.compile("(?i)(swversion=)[^#&\\s]+"),
            Pattern.compile("(?i)(specversion=)[^#&\\s]+"),
            Pattern.compile("(?i)(spectype=)[^#&\\s]+"),
            Pattern.compile("(?i)(mac=)[^#&\\s]+"),
            Pattern.compile("(?i)(phone=)[^#&\\s]+"),
            Pattern.compile("(?i)(uuid=)[^#&\\s]+"),
            Pattern.compile("(?i)(userId=)[^#&\\s]+"),
            Pattern.compile("(?i)(carno=)[^#&\\s]+"),
            Pattern.compile("(?i)(curtime=)[^#&\\s]+"),
            Pattern.compile("(?i)(session=)[^#&\\s]+"),
            Pattern.compile("(?i)(token=)[^#&\\s]+")
        );

        Pattern workerIdPattern = Pattern.compile("(?i)(Worker ID: )\\d+(:\\d+)?");
        Pattern visitImgPattern = Pattern.compile("(?i)(방문자 사진 : 파일 경로 - )[^#&\\s]+");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), "EUC-KR"));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilePath), "EUC-KR"))) {

            String line;
            while ((line = reader.readLine()) != null) {
                // 단순 대체: 전체를 [empty]로 치환
                for (Pattern p : simpleReplacements) {
                    line = p.matcher(line).replaceAll("[empty]");
                }

                // key=value 형태는 값만 마스킹
                for (Pattern p : keyValuePatterns) {
                    line = p.matcher(line).replaceAll("$1[empty]");
                }

                // 특수 형태
                line = workerIdPattern.matcher(line).replaceAll("$1[empty]");
                line = visitImgPattern.matcher(line).replaceAll("$1[empty]");

                writer.write(line);
                writer.newLine();
            }

            JOptionPane.showMessageDialog(this, "✅ 마스킹 완료!\n결과 파일: " + outputFilePath);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "❌ 마스킹 실패: " + ex.getMessage());
        }
    }
    
    private void applyFilters() {
        if (resultFilter == null || nameFilter == null) return;

        Object resultSelectedObj = resultFilter.getSelectedItem();
        Object nameSelectedObj = nameFilter.getSelectedItem();

        if (resultSelectedObj == null || nameSelectedObj == null) return;

        String resultSelected = resultSelectedObj.toString();
        String nameSelected = nameSelectedObj.toString();

        TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) resultTable.getRowSorter();
        if (sorter == null) {
            sorter = new TableRowSorter<>(tableModel);
            resultTable.setRowSorter(sorter);
        }

        List<RowFilter<TableModel, Object>> filters = new ArrayList<>();

        if (!resultSelected.equals("전체")) {
            filters.add(RowFilter.regexFilter(resultSelected, 1));
        }
        if (!nameSelected.equals("전체")) {
            filters.add(RowFilter.regexFilter(Pattern.quote(nameSelected), 0));
        }

        sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    private void applyNameFilterLike(String partialName) {
        TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) resultTable.getRowSorter();
        if (sorter == null) {
            sorter = new TableRowSorter<>(tableModel);
            resultTable.setRowSorter(sorter);
        }

        List<RowFilter<TableModel, Object>> filters = new ArrayList<>();

        String resultSelected = (String) resultFilter.getSelectedItem();
        if (!"전체".equals(resultSelected)) {
            filters.add(RowFilter.regexFilter(resultSelected, 1));
        }

        filters.add(RowFilter.regexFilter(Pattern.quote(partialName), 0));

        sorter.setRowFilter(RowFilter.andFilter(filters));
    }

    private void updateNameFilterItems(Set<String> names) {
        if (isUpdatingNameFilter) return;
        isUpdatingNameFilter = true;

        nameFilter.removeAllItems();
        nameFilter.addItem("전체");
        for (String name : names) {
            nameFilter.addItem(name);
        }

        isUpdatingNameFilter = false;
    }

    private void extractScenariosFromLog() {
        JFileChooser chooser = new JFileChooser(".");
        chooser.setDialogTitle("로그 파일 선택 (시나리오 자동 생성용)");
        int ret = chooser.showOpenDialog(this);
        if (ret != JFileChooser.APPROVE_OPTION) return;

        File logFile = chooser.getSelectedFile();
        String outputName = "scenarios/extracted_scenarios.csv";

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(logFile), "EUC-KR"));
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputName), "EUC-KR"))) {

            bw.write("name,cmd,target,params,send\n");
            Set<String> seenKeys = new HashSet<>();
            Set<String> newNames = new TreeSet<>();
            String line;

            while ((line = br.readLine()) != null) {
                int idx = line.indexOf("<start=");
                if (idx == -1) continue;
                String send = line.substring(idx).trim();
                if (!send.contains("$cmd=") || !send.contains("$target=")) continue;
                if (send.contains("#err=")) continue; // <- #err 필드는 무시

                String cmd = extractValue(send, "$cmd=");
                String target = extractValue(send, "$target=");
                String mode = extractValue(send, "#mode=");

                String params = "$cmd=" + cmd + ";$target=" + target;
                if (!mode.isEmpty()) {
                    params += ";#mode=" + mode;
                }

                String name = target + " => cmd=" + cmd;
                String dedupKey = "cmd=" + cmd + "|target=" + target;
                if (!mode.isEmpty()) name += ", mode=" + mode; dedupKey += "|mode=" + mode;

                if (seenKeys.contains(dedupKey)) continue;
                seenKeys.add(dedupKey);
                newNames.add(name);
                send = cleanMultiNoPackets(send);
                if (!hasMeaningfulDataAfterNo(send)) continue; // 의미 없는 #no= 이면 skip
                bw.write(String.format("%s,%s,%s,%s,%s\n",
                    escapeCsvField(name),
                    escapeCsvField(cmd),
                    escapeCsvField(target),
                    escapeCsvField(params),
                    escapeCsvField(send)
                ));
            }

            bw.flush();

            try (BufferedReader br2 = new BufferedReader(new InputStreamReader(new FileInputStream(outputName), "EUC-KR"))) {
                scenarios = ScenarioLoader.loadFromCSV(br2);
            }

            updateNameFilterItems(newNames);
            if (!newNames.isEmpty()) {
                nameFilter.setSelectedItem(newNames.iterator().next());
            } else {
                nameFilter.setSelectedItem("전체");
            }

            JOptionPane.showMessageDialog(this, "✅ 시나리오 자동 추출 완료: " + outputName);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "❌ 시나리오 추출 실패: " + e.getMessage());
        }
    }
    
    private boolean hasMeaningfulDataAfterNo(String send) {
        int noIdx = send.indexOf("#no=");
        if (noIdx == -1) return true; // #no= 없으면 OK

        // 다음 #no= 이후 전체 문자열 추출
        String afterNo = send.substring(noIdx + 4);

        // 다음 '#' 기준으로 쪼개서 유의미한 변수 있는지 확인
        String[] tokens = afterNo.split("#");

        for (String token : tokens) {
            if (token.contains("=")) {
                String[] kv = token.split("=", 2);
                if (kv.length == 2 && !kv[0].trim().isEmpty() && !kv[1].trim().isEmpty()) {
                    return true; // 의미 있는 변수 있음
                }
            }
        }

        return false; // 아무것도 없음 (ex. #no=1 만 있거나 비어 있음)
    }
    
    private String cleanMultiNoPackets(String sendPart) {
        int firstNoIdx = sendPart.indexOf("#no=");
        if (firstNoIdx == -1) return sendPart;

        int secondNoIdx = sendPart.indexOf("#no=", firstNoIdx + 1);
        if (secondNoIdx == -1) return sendPart;

        return sendPart.substring(0, secondNoIdx);
    }
    
    private String escapeCsvField(String field) {
        if (field.contains("\"")) {
            field = field.replace("\"", "\"\"");
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            field = "\"" + field + "\"";
        }
        return field;
    }

    private String extractValue(String input, String key) {
        int start = input.indexOf(key);
        if (start == -1) return "";
        start += key.length();
        if (start < input.length() && input.charAt(start) == '#') return "";
        int endDollar = input.indexOf('$', start);
        int endHash = input.indexOf('#', start);
        int end = (endDollar == -1 ? input.length() : endDollar);
        if (endHash != -1) end = Math.min(end, endHash);
        return input.substring(start, end);
    }

    private void startLogMonitoring() {
        JFileChooser chooser = new JFileChooser(".");
        chooser.setDialogTitle("검수할 로그 파일 선택");
        int ret = chooser.showOpenDialog(this);
        if (ret != JFileChooser.APPROVE_OPTION) return;

        File logFile = chooser.getSelectedFile();

        tableModel.setRowCount(0);
        summaryTableModel.setRowCount(0);
        saveButton.setEnabled(false);
        logMonitorButton.setEnabled(false);
        statusLabel.setText("로그 모니터링 중... 잠시만 기다려주세요.");
        progressBar.setVisible(true);

        LogMonitor monitor = new LogMonitor(scenarios, logFile.getAbsolutePath());
        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.submit(() -> {
            monitor.run();
            Map<String, String> resMap = monitor.getResults();
            Map<String, String> remarkMap = monitor.getRemarks();  // <-- remarks 가져오기

            Map<String, ScenarioStat> statMap = new HashMap<>();

            for (Scenario sc : scenarios) {
                statMap.put(sc.name, new ScenarioStat(sc.name));
            }

            for (Map.Entry<String, String> entry : resMap.entrySet()) {
                String fullName = entry.getKey();
                String result = entry.getValue();

                Scenario foundSc = null;
                for (Scenario sc : scenarios) {
                    if (fullName.startsWith(sc.name)) {
                        foundSc = sc;
                        break;
                    }
                }

                final String scenarioName = (foundSc != null) ? foundSc.name : fullName;

                ScenarioStat stat = statMap.computeIfAbsent(scenarioName, ScenarioStat::new);

                if ("PASS".equals(result)) stat.passCount++;
                else if (result.startsWith("FAIL")) stat.failCount++;
                else stat.errorCount++;

                final String cmdVal = (foundSc != null) ? foundSc.cmd : "";
                final String targetVal = (foundSc != null) ? foundSc.target : "";

                String remark = remarkMap.getOrDefault(fullName, "");

                final String finalResult = result;
                final String finalRemark = remark;

                SwingUtilities.invokeLater(() -> {
                    tableModel.addRow(new Object[]{scenarioName, finalResult, finalRemark, cmdVal, targetVal});
                });
            }

            SwingUtilities.invokeLater(() -> {
                summaryTableModel.setRowCount(0);
                for (ScenarioStat stat : statMap.values()) {
                    int total = stat.passCount + stat.failCount + stat.errorCount;
                    summaryTableModel.addRow(new Object[]{
                            stat.name, total, stat.passCount, stat.failCount, stat.errorCount
                    });
                }

                summaryLabel.setText(String.format("총 시나리오: %d, PASS: %d, FAIL: %d, ERROR: %d",
                        scenarios.size(),
                        statMap.values().stream().mapToInt(s -> s.passCount).sum(),
                        statMap.values().stream().mapToInt(s -> s.failCount).sum(),
                        statMap.values().stream().mapToInt(s -> s.errorCount).sum()));

                saveButton.setEnabled(true);
                logMonitorButton.setEnabled(true);
                progressBar.setVisible(false);
                statusLabel.setText("로그 모니터링 완료.");
            });
        });
        exec.shutdown();
    }

    private void loadScenarios(String relativePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(relativePath)) {
            if (is == null) throw new FileNotFoundException("리소스 파일 없음: " + relativePath);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "EUC-KR"))) {
                scenarios = ScenarioLoader.loadFromCSV(br);
            }

            Set<String> nameSet = new TreeSet<>();
            for (Scenario s : scenarios) {
                nameSet.add(s.name);
            }
            updateNameFilterItems(nameSet);
            nameFilter.setSelectedItem("전체");

            statusLabel.setText("시나리오 로드 완료");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "❌ 시나리오 로드 실패: " + e.getMessage());
            statusLabel.setText("시나리오 로드 실패");
        }
    }

    private void loadScenarioFromFile() {
        JFileChooser chooser = new JFileChooser(".");
        chooser.setDialogTitle("시나리오 CSV 파일 선택");
        int ret = chooser.showOpenDialog(this);
        if (ret != JFileChooser.APPROVE_OPTION) return;

        File csvFile = chooser.getSelectedFile();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), "EUC-KR"))) {
            scenarios = ScenarioLoader.loadFromCSV(br);

            Set<String> nameSet = new TreeSet<>();
            for (Scenario s : scenarios) {
                nameSet.add(s.name);
            }
            updateNameFilterItems(nameSet);
            nameFilter.setSelectedItem("전체");

            statusLabel.setText("시나리오 수동 로드 완료");
            JOptionPane.showMessageDialog(this, "✅ 시나리오 불러오기 성공!");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "❌ 시나리오 불러오기 실패: " + e.getMessage());
        }
    }

    private void saveResultToExcel() {
        // 버튼 및 필터 비활성화
        saveButton.setEnabled(false);
        logMonitorButton.setEnabled(false);
        extractButton.setEnabled(false);
        loadScenarioButton.setEnabled(false);
        resultFilter.setEnabled(false);
        nameFilter.setEnabled(false);

        progressBar.setVisible(true);
        statusLabel.setText("Excel 저장 중... 잠시만 기다려주세요.");

        // 백그라운드 작업 실행

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {            
        	@Override
            protected Void doInBackground() throws Exception {
                String fname = "검수결과_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx";
                Workbook workbook = new XSSFWorkbook();
                Sheet sheet = workbook.createSheet("검수 결과");

                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);

                Row headerRow = sheet.createRow(0);
                String[] headers = {"시나리오", "결과", "비고", "cmd", "target"};
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    Row row = sheet.createRow(i + 1);
                    for (int j = 0; j < tableModel.getColumnCount(); j++) {
                        Object val = tableModel.getValueAt(i, j);
                        row.createCell(j).setCellValue(val == null ? "" : val.toString());
                    }
                }

                for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

                try (FileOutputStream fos = new FileOutputStream(fname)) {
                    workbook.write(fos);
                }
                workbook.close();

                JOptionPane.showMessageDialog(TcpValidatorApp.this, "✅ Excel 저장 완료: " + fname);

                return null;
            }

            @Override
            protected void done() {
                // 완료 시 버튼 활성화 및 프로그래스바 숨김
                saveButton.setEnabled(true);
                logMonitorButton.setEnabled(true);
                extractButton.setEnabled(true);
                loadScenarioButton.setEnabled(true);
                resultFilter.setEnabled(true);
                nameFilter.setEnabled(true);

                progressBar.setVisible(false);
                statusLabel.setText("준비됨");
            }
        };

        worker.execute();
    }

    // 내부 클래스 - 시나리오별 통계 집계용
    private static class ScenarioStat {
        String name;
        int passCount;
        int failCount;
        int errorCount;

        public ScenarioStat(String name) {
            this.name = name;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TcpValidatorApp().setVisible(true));
    }
}