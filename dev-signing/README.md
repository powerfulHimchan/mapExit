# 개발 APK 서명

서명 키는 저장소에 포함하지 않는다. 기본적으로 Android Gradle Plugin이 개발 환경의 debug 키를 사용한다.

같은 인증서로 APK를 계속 업데이트하려면 소유자가 보관한 개발 키를 Git에서 제외되는 `dev-signing/mapexit-debug.keystore`에 배치한다. 이 파일이 있으면 debug 서명에 사용하고, 없으면 기본 debug 서명을 사용한다.

기존 APK와 인증서가 다르면 덮어 설치할 수 없다. 앱을 삭제하면 기기 내부 게임 데이터도 삭제되므로, 실제 플레이 데이터를 유지할 업데이트에는 기존 서명 키를 사용해야 한다. GitHub Actions에서 생성한 debug 키와 제공 APK의 인증서는 다를 수 있다.

실제 배포용 release 키와 서명 설정은 별도로 관리한다.
