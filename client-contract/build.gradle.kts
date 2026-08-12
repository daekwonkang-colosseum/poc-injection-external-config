// 이 파일에 dependencies 블록이 없다는 것이 이 모듈의 설계 단언이다.
//
// 계약(ClientOptions, ClientPool)은 java.* 만 참조한다. Spring 도, pylon 도,
// 어떤 전송 구현체도 컴파일 클래스패스에 없으므로 위반은 리뷰가 아니라
// 컴파일 에러로 잡힌다.
//
// 루트 build.gradle.kts 의 subprojects 블록이 java-library 플러그인과
// spring-boot-dependencies BOM 을 주입하지만, BOM 은 버전 관리일 뿐
// 클래스를 컴파일 클래스패스에 올리지 않는다. spring-boot-starter-test 는
// test 클래스패스에만 올라간다. 따라서 main 소스의 순수성은 그대로 강제된다.
