dependencies {
    api(project(":pylon-lite"))
    // 버전을 명시하지 않는다. Boot 2.3.4 BOM 이 3.14.x 를 고정한다 —
    // 로컬 캐시의 okhttp 5.3.2 는 Kotlin 기반이고 RequestBody.create(MediaType, String) 이
    // 사라진 별개 API 라, 실물(3.x) 미러링에 쓸 수 없다.
    api("com.squareup.okhttp3:okhttp")
}
