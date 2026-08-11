package poc.client.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * API-GW(pylon) HTTP Client 옵션.
 *
 * 생성된 어댑터는 옵션을 받는 자리가 없다. 어댑터는 specId 를 들고 공용
 * DynamicApiClient 에 위임하므로, 옵션은 그 아래 Spec / RestTemplatePool 레이어에 준다.
 * 그 입구가 PylonConfiguration 이고 이 클래스가 그것을 프로파일별 yml 로 뽑아낸 것이다.
 *
 * providers 를 비워두면 라이브러리 기본 동작과 동일하다.
 */
@ConstructorBinding
@ConfigurationProperties("pylon.client")
data class PylonClientProperty(

    /** 전역 connect timeout(ms). provider 단위로는 줄 수 없다. */
    val connectTimeout: Int = DEFAULT_CONNECT_TIMEOUT,

    /** 전역 공용 커넥션 풀 크기. null 이면 min(provider 수 * 500, 4000). */
    val maxConnection: Int? = null,

    /** 라우팅 정보 갱신 주기(ms). */
    val routingInfoDuration: Int = DEFAULT_ROUTING_INFO_DURATION,

    /** key = pylon provider 명(예: order_api). map key 는 대괄호 표기가 필요하다. */
    val providers: Map<String, Provider> = emptyMap(),
) {

    data class Provider(
        /**
         * provider 의 모든 spec 에 적용되는 read timeout(ms).
         *
         * 필수다. 라이브러리 TimeoutCustomizer 조립부가 per-spec 등록을
         * `defaultTimeout != null` 블록 안에서 하기 때문에, 이 값이 없으면
         * readTimeoutPerSpec 이 조용히 무시된다.
         *
         * 주의: 이 값은 생성된 스펙의 timeout 을 provider 전체에 대해 덮어쓴다.
         * 오래 걸리는 spec 이 섞인 provider 라면 readTimeoutPerSpec 으로 되살려야 한다.
         */
        val readTimeout: Int,

        /** provider 전용 커넥션 풀 크기. null 이면 전역 공용 풀을 쓴다. */
        val maxConnection: Int? = null,

        /** key = specId. readTimeout 보다 우선한다. */
        val readTimeoutPerSpec: Map<String, Int> = emptyMap(),

        /** 환경별 scheme/port 치환. 둘 다 있어야 적용된다. host 는 건드리지 않는다. */
        val scheme: String? = null,
        val port: Int? = null,
    ) {
        val schemeAndPortOverridden: Boolean
            get() = scheme != null && port != null
    }

    companion object {
        /** PylonConfiguration.Builder 기본값과 동일. */
        const val DEFAULT_CONNECT_TIMEOUT = 3_000

        /** PylonConfiguration.Builder 기본값과 동일. */
        const val DEFAULT_ROUTING_INFO_DURATION = 60_000
    }
}
