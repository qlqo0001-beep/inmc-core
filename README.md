# inmc-core

INMC 마인크래프트 플러그인들이 공통으로 올라타는 **런타임 플러그인**입니다.
게임 안에서는 명령어도 GUI도 설정도 없습니다. 직접 하는 일은 접속·퇴장에 플레이어 이름을 기록하는 것 하나뿐입니다.

| | |
|---|---|
| 서버 | Paper **26.1** 이상 |
| Java | **25** |
| 필수 의존성 | **없음** |
| 하는 일 | Kotlin 런타임 제공 · 공용 프레임워크 · 공용 도메인 서비스 · 공유 저장소 |

## 왜 있는가

이것 없이도 각 플러그인은 돌 수 있었습니다. 실제로 4세대까지는 그랬고, 대신 이런 상태였습니다.

- `Menu` · `Icon` · `ItemRef` · `ItemMatcher` · `StoredItem` · `ConfigService` · `Text` · `Numbers`
  · `PluginClasses` · `MMOItemsHook` 이 플러그인마다 한 벌씩, **패키지명만 빼면 글자 단위로 동일**하게 존재
- relocate 된 `kotlin-stdlib` 이 플러그인마다 약 2MB씩
- 훅 하나를 고치면 세 곳을 똑같이 고쳐야 했고, 실제로 한 곳만 고쳐진 적이 있었습니다

core 는 그 중복을 한 곳으로 모읍니다. 대신 **플러그인들의 유일한 필수 의존성**이 됩니다.

## 설치

`plugins/` 에 `inmc-core-<버전>.jar` 을 넣습니다. **다른 INMC 플러그인보다 먼저 로드되어야 하지만
순서를 직접 맞출 필요는 없습니다** — 각 플러그인이 `paper-plugin.yml` 에

```yaml
dependencies:
  server:
    inmc-core: { load: BEFORE, required: true, join-classpath: true }
```

을 선언하고 있어 Paper 가 알아서 처리합니다. core 가 없으면 그 플러그인들은 **아예 로드되지 않습니다.**

## 담고 있는 것

| 패키지 | 내용 |
|---|---|
| `gui/` | `Menu`(자작 인벤토리 베이스) · `Icon` |
| `listener/` | `MenuListener` — 모든 메뉴의 단일 라우팅 지점 · `ProfileListener` — core 가 등록하는 유일한 리스너 |
| `input/` | `ChatPrompt` — 채팅 한 줄 입력 |
| `config/` | `ConfigService` — 워커 1개로 직렬화된 비동기 I/O |
| `item/` | `ItemRef` · `ItemResolver` · `ItemMatcher` · `StoredItem` |
| `integration/` | `PluginClasses` · `MMOItemsHook` · `CustomItemHook` · `EconomyHook` (전부 리플렉션) |
| `util/` | `Text`(MiniMessage) · `Numbers` · `Durations` · `Placeholders` |
| `rank/` | `RankService` · `RankBoard` · `RankConfig` · `ResetSchedule` · `Outcome` · `Rankable` |
| `reward/` | `RewardService` · `Mailbox` · `RewardTable` · `RewardEntry` · `RewardHost` |
| `store/` | `PlayerStore` — 네임스페이스로 나눠 담는 공유 플레이어 저장소 · `Profile` — uuid→이름 |

## 호스트 플러그인이 구현할 것

core 가 요구하는 것은 세 개뿐입니다.

```kotlin
interface InmcHost {
    val plugin: JavaPlugin
    val io: ConfigService
    fun tell(target: CommandSender, key: String, ph: Placeholders? = null)
}
```

랭킹·보상까지 쓰려면 `RewardHost`(+9개)를 구현하고, 랭킹 대상이 `Rankable`(6개)을 구현합니다.

**이 표면은 일부러 좁게 유지합니다.** 넓어질수록 새 플러그인을 붙이는 비용이 커집니다.
`src/test/kotlin/kr/inmc/core/ContractTest.kt` 가 각 인터페이스의 멤버 수를 못박고 있어,
늘리려면 테스트를 같이 고쳐야 합니다 — 고치는 김에 한 번 생각하게 만드는 것이 목적입니다.

## 지금 실제로 쓰이는 것

`PlayerStore` 는 더 이상 놀고 있지 않습니다.

- **`profile`** (core 소유) — uuid→이름·마지막 접속. 숫자야구와 urb 가 각자 들고 있던 이름 캐시를 대체했고, 몬스터는 원래 없던 것을 얻었습니다.
- **`monster-trigger`** — 몬스터의 등장 조건 진행도. `uuid → 트리거id → 필드` 3단으로 들어갑니다.

각 플러그인은 부팅 때 옛 파일을 한 번 읽어 옮기고(이미 있는 값은 덮지 않습니다), 몬스터는 원본을 지우지 않고 `.imported` 로 이름만 바꿉니다.

## 빌드

```bash
./gradlew build
```

산출물은 `build/libs/inmc-core-<버전>.jar` 입니다.

이 폴더는 `제작플긴/` 안에 있지만 **독립된 Gradle 빌드**입니다 — 서브프로젝트가 아니라
상위 빌드가 `includeBuild("inmc-core")` 로 물립니다. `build-logic/` 의 관례 플러그인과
`gradle/libs.versions.toml` 카탈로그도 여기가 갖고 있고, 플러그인들이 그것을 끌어씁니다.

상위에서 `./gradlew build` 를 해도 core 소스가 바로 물리므로 **publish 단계가 없습니다.**
릴리스할 때만 여기서 버전을 올립니다.

## 버전 올리기

`gradle/libs.versions.toml` 의 `inmc-core` 한 줄만 고칩니다.
core 자신의 `version` 과 플러그인들이 참조하는 좌표가 같은 값을 읽으므로 갈라질 수 없습니다.

```toml
[versions]
inmc-core = "1.1.0"
```

## 주의

- **`kotlin-stdlib` 을 relocate 하지 않습니다.** 의존 플러그인들이 core 의 클래스로더에서
  이 stdlib 을 그대로 집어가야 하기 때문입니다. 접두사를 붙이면 core 가 넘겨주는 람다의
  `kotlin.jvm.functions.Function1` 이 상대편 것과 달라져 링크가 깨집니다.
  다른 서드파티 Kotlin 플러그인과 충돌하지 않는 이유는 Paper 가 플러그인마다 클래스로더를
  따로 두고 `join-classpath` 를 선언한 플러그인만 여기를 들여다보기 때문입니다.
- **bStats 를 넣게 되면 그것만은 반드시 relocate 해야 합니다.** relocate 하지 않으면
  라이브러리가 스스로 로드를 거부합니다.
- **저장소 내용이 필요한 일은 `PlayerStore.whenReady` 안에 넣으세요.** `load: BEFORE` 는
  플러그인 enable 순서만 정합니다. 저장소의 로드 콜백은 `runTask` 로 걸려 **첫 틱**에야 돌고,
  첫 틱은 모든 플러그인의 onEnable 이 끝난 뒤입니다.
- **`PlayerStore` 의 키에 점(`.`)을 쓸 수 없습니다.** `YamlConfiguration` 이 점을 경로로
  해석해 계층을 만들고, 그러면 저장은 되는데 같은 키로 되읽히지 않습니다. `set()` 이 거부합니다.
