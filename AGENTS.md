# AI Diary - AGENTS.md

## 프로젝트 구조 참고

- 코드 수정/리팩토링 전 반드시 `docs/structure.md` 를 읽고 현재 아키텍처(MVI, 디렉토리, 데이터 흐름)를 숙지할 것.
- 구조 문서는 코드와 함께 갱신한다(새 패키지, 새 상태 필드, 새 디렉토리 추가 시 `docs/structure.md` 도 업데이트).

## 프로젝트 정책

### 음성인식 모델 (Sherpa-Onnx)

- **절대 변경 금지**: 오프라인 모델을 온라인(스트리밍) 모델로 임의 변경하지 않는다.
- 현재 사용 모델: `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17` (Offline, SenseVoice)
- 스트리밍 모델(`sherpa-onnx-streaming-zipformer-korean-2024-06-16`)은 Sherpa-Onnx v1.13.4와 호환성 문제(native crash)가 확인되었으므로 사용 금지.
- 모델 변경 시 반드시 실제 기기에서 테스트 후 커밋할 것.

### 빌드 및 테스트

- `.\gradlew assembleDebug` 성공 후 실제 기기에서 테스트 필수.
- 녹음 기능(마이크 버튼, 볼륨 미터, 텍스트 변환) 정상 동작 확인할 것.

### 의존성

- Sherpa-Onnx: `libs/jniLibs/` 내 .so 파일 직접 관리 (v1.13.4)
- Kotlin API: `com.k2fsa.sherpa.onnx` 패키지의 .kt 파일들은 GitHub에서 직접 가져옴
- `OfflineRecognizer` 사용, `OnlineRecognizer`로 임의 변경 금지

### 서비스 정체성 정의

- 이 서비스는 단순 단발성 '일기 앱'이 아닌, 플래너, 장기 목표, 챗봇 비서, 개별 기록 블록이 융합된 **'다이어리 앱'**입니다.
- 사용자 대면 다이얼로그나 설명 문구, 번역 가이드, AI 프롬프트 제작 시 단순 '일기' 단어에만 치우치지 않고 전체 '다이어리 기록' 맥락을 고려하여 단어를 조율해 주세요.

