package inmc.build

import org.gradle.api.provider.Property

/**
 * 각 모듈의 `inmc { ... }` 블록. 기본값은 inmc.paper-base 가 convention 으로 채운다.
 */
abstract class InmcPluginExtension {
    /** 컴파일 대상 Paper 버전. 예: `26.1.2` */
    abstract val paper: Property<String>

    /** jar 이름. 기본값은 프로젝트 이름. */
    abstract val pluginName: Property<String>

    /** runServer 의 -Xmx 값. 예: `2G` */
    abstract val runMemory: Property<String>
}
