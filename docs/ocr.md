# 공고 본문 OCR

## 범위
- PaddleOCR은 description 보완에만 사용한다. HTML 본문 중 이미지 위치에 OCR 텍스트를 삽입한다.
- 텍스트가 80자 이상인 혼합형도 처리한다. 이미지 전용 공고는 OCR 후 합쳐진 본문 80자 기준으로 검증한다.
- 급여·학력·마감일·마감 방식은 OCR로 결정하지 않는다. 기존 요약과 OCR 전 HTML 텍스트만 사용한다.
- DB 스키마 변경 없음. 이미지 URL/문구/원본 이미지 좌표/신뢰도/모델/상태는 기존 source_metadata.bodyOcr에 저장한다. API 필드는 그대로다.
- 표의 글자를 읽는 기능이며 행·열·병합 셀 구조 복원은 하지 않는다.

## 실행 (명시적으로 활성화할 때만)
기본값은 비활성화다. 이번 구현 검증에서는 개발 서버/수집기를 실행하지 않았다.
Python 3.11과 ocr/requirements.txt의 버전을 사용한다. 기존 AI 서버와 독립된 로컬 처리기다.

```powershell
python -m pip install -r ocr/requirements.txt
python ocr/worker.py
```

모델은 첫 실행 때 내려받으며 준비가 끝난 뒤 요청을 받는다. 실제 수집을 사용하기로 한 시점에 애플리케이션 옵션을 추가한다:
`--compass.recruit.ocr.enabled=true --compass.recruit.ocr.endpoint=http://127.0.0.1:8091/ocr`
이 옵션 자체는 수집기를 실행하지 않는다. 현재 개발 서버는 재시작하지 않았으므로 새 코드가 적용되지 않았다.
로컬 실행은 기본 127.0.0.1 바인딩입니다.

## 처리와 제한
- 투명 배경을 흰색으로 합성한다. 1600px 높이 타일에 160px 겹침을 두고 원본 좌표로 환산한다.
- 메모리 사용을 제한하기 위해 검출 입력의 긴 변은 1280px로 제한하고 문자 인식 배치는 1개로 처리한다. 인식 좌표는 확대 전 원본 기준으로 저장한다.
- 폭 1200px 미만 이미지는 최대 2배/폭 1600px까지 확대한다. 겹친 영역에서 같은 문구와 겹치는 위치만 중복 제거한다.
- 같은 그림의 중복 URL은 공고에서 한 번만 처리/삽입한다. 동일 문구가 다른 위치에 있으면 보존한다.
- 허용 CDN: www.saraminimage.co.kr / saraminimage.co.kr / static.saraminimage.co.kr, HTTPS 443만. 다른 호스트, 로그인 주소, 리다이렉트 우회는 지원하지 않는다.
- 공고당 최대 20개 고유 URL 요청, 이미지 12MB/24M픽셀/폭4000/높이24000 제한. 이미지 다운로드 25초, Java 요청 10분 제한.
- 처리기는 타일 사이 8분 경과를 검사한다. 단일 타일의 실행을 강제 중단하는 기능은 없다. 큰 공고는 동기 수집 지연이 생길 수 있으므로 이후 큐 도입은 별도 범위다.
- 작은 아이콘(폭32/높이12 미만)은 텍스트 없음 처리한다. 그 외 로고 자동 분류는 하지 않아 일부 장식 글자도 포함될 수 있다.
- 처리기 메모리에 최근 16개 이미지 지문 결과를 보관한다. 원본 이미지를 파일/DB에 새로 저장하지 않는다.
- SUCCESS / NO_TEXT / FAILED / DISABLED / UNSUPPORTED_URL / DUPLICATE / LIMIT 상태를 구분한다.
- 실패하더라도 기존 HTML이 충분하면 저장할 수 있고 메타데이터에 실패가 남는다. 이미지 전용 본문이 부족하면 기존처럼 공고 검증에서 제외한다. 처리 중 인터럽트는 전파한다.
- OCR 출력을 HTML로 해석하지 않는다. 원문 표 구조와 철자 정확도를 보장하지 않으며 신뢰도만으로 정답 판정하지 않는다.

## 검증
```powershell
.\gradlew.bat test bootJar --no-daemon '-Dorg.gradle.java.installations.paths=C:/Program Files/JAVA/jdk-17,C:/Program Files/JAVA/jdk-21'
# ocr 의존성이 있는 별도 Python 환경에서:
python -m unittest discover -s ocr -p test_worker.py -v
```
Java 테스트는 테스트용 H2와 로컬 가짜 OCR 응답을 사용한다. Python 단위 테스트는 Paddle 모델/외부 공고 다운로드 없이 타일·좌표·배경·캐시·URL 제한을 검증한다.


