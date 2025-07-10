# LogMonitor

`LogMonitor`는 시나리오 기반 로그 분석을 수행하는 Java 프로그램입니다.  
로그 파일 내 특정 패턴과 변수들을 시나리오와 비교하여 정확한 일치 여부를 검사합니다.  
해당 프로그램은 `JAVA 1.8`, `SWING GUI`, `MAVEN`이 적용되었습니다.

---

## 주요 기능

- 특정 조건 및 로그 분석 커스터마이징 가능
- `$cmd`, `$target` 및 `#mode` 포함 여부 기준 엄격 비교
- 변수 이름과 순서까지 일치해야 PASS 처리
- 첫 번째 `#no=` 변수 이후 중복된 `#no=` 변수는 무시 (첫 번째만 포함)
- `#err=`가 포함된 로그 라인은 비교 대상에서 제외
- `[R: [S:` 문자열이 포함된 로그만 분석

---

## 코드 핵심: `extractSendPart` 메서드

```java
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

    int firstNoIdx = sendPart.indexOf("#no=");
    if (firstNoIdx != -1) {
        int secondNoIdx = sendPart.indexOf("#no=", firstNoIdx + 1);
        if (secondNoIdx != -1) {
            sendPart = sendPart.substring(0, secondNoIdx).trim();
        }
    }
    return sendPart;
}

![TCP 로그 내 패킷 검수 완료 화면](https://raw.githubusercontent.com/jinwoo1004/tcp-validator/master/scenarios/TCP%20%EB%A1%9C%EA%B7%B8%20%EB%82%B4%20%ED%8C%A8%ED%82%B7%20%EA%B2%80%EC%88%98%20%EC%99%84%EB%A3%8C%20%ED%99%94%EB%A9%B4.png)
