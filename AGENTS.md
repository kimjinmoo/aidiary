# AI Diary - AGENTS.md

## 프로젝트 정책

### 음성인식 모델 (Sherpa-Onnx)

- **절대 변경 금지**: 오프라인 모델을 온라인(스트리밍) 모델로 임의 변경하지 않는다.
- 현재 사용 모델: `sherpa-onnx-zipformer-korean-2024-06-24` (Offline)
- 스트리밍 모델(`sherpa-onnx-streaming-zipformer-korean-2024-06-16`)은 Sherpa-Onnx v1.13.4와 호환성 문제(native crash)가 확인되었으므로 사용 금지.
- 모델 변경 시 반드시 실제 기기에서 테스트 후 커밋할 것.

### 빌드 및 테스트

- `.\gradlew assembleDebug` 성공 후 실제 기기에서 테스트 필수.
- 녹음 기능(마이크 버튼, 볼륨 미터, 텍스트 변환) 정상 동작 확인할 것.

### 의존성

- Sherpa-Onnx: `libs/jniLibs/` 내 .so 파일 직접 관리 (v1.13.4)
- Kotlin API: `com.k2fsa.sherpa.onnx` 패키지의 .kt 파일들은 GitHub에서 직접 가져옴
- `OfflineRecognizer` 사용, `OnlineRecognizer`로 임의 변경 금지
