# Proto / gRPC 확장 구조

현재 프로젝트에 `.proto` 파일이 없으므로 플레이스홀더입니다.

## 향후 추가 시

1. `.proto` 파일을 이 디렉토리에 추가
2. `buf.yaml` / `buf.gen.yaml` 작성
3. 루트 `build.gradle`의 `redocly:bundle` 태스크에 아래 연동 추가:

```bash
npx @redocly/cli bundle docs/proto/<service>.openapi.yaml -o build/docs/proto-bundled.yaml
```

4. `docs/redocly.yaml`의 `apis` 섹션에 proto API 항목 추가
