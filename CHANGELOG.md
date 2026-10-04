# inmc-core 변경 기록

core 에 **무엇이 지금 있는지**는 상위 `ARCHITECTURE.md` 가 진실입니다. 이 파일은 언제 무엇이 올라왔는지의 기록입니다.

---

## 미배포 — TitleForge 표시명 헬퍼

- `integration.TitleForgeNames.displayName(uuid, fallback)` — 닉네임 변경자는 바뀐 이름으로(리플렉션, 없으면 실명).
  보상 공지 등 지금 접속 중인 사람을 가리킬 때만 쓴다. 저장 이름·명령어 치환은 실명 유지

## 2026-10-02 — 개인 설정 창구(PlayerSettings)

- `integration/PlayerSettings` — 플러그인이 플레이어 개인 설정(켜고 끄기·고르기)을 올리고, 플레이어 메뉴(inmc-menu)가 한 화면에 모아 그린다.
  값은 PlayerStore 네임스페이스 `settings`(열쇠의 점은 쌍점으로). 정하지 않았으면 기본값, 정의가 없으면 부른 쪽의 기본값, 권한이 필요한데 없으면 기본값.
  바꾸면 `listen` 한 쪽에 알린다. 기본값과 같은 값은 지운다. 테스트 `PlayerSettingsTest`
- core 가 공용 설정 **`core.rare-announce`**(희귀 드랍·당첨 공지 받기)를 올린다 — 공지하는 플러그인이 여럿(드랍·랜덤박스)이다

## 2026-09-30 — 들고 다니는 보관함(CarriedStorage)

- `integration/CarriedStorage`(object) — 플레이어가 들고 다니는 보관함(커스텀아이템의 배낭)을 **가방처럼 세고 빼는** 창구. `Provider`
  (containers) · `Container`(contents · write — 곧바로 저장) · `count` · `take` · 순수 `plan`. 사망 처리 창구(`ExtraInventory`)와 따로다 —
  배낭 내용물은 배낭 아이템에 붙어 다니므로 죽을 때 따로 떨구면 두 번 나온다(사용자 결정: 배낭 안 물건도 인벤처럼)
- `ItemMatcher` 의 `has`·`consumeOne` 이 가방 다음에 보관함까지 본다. `count(player, spec|test)` · `takeOne(player, spec)`(뺀 것의
  사본 — 되돌려 주기용) 추가. `findSlot` 은 가방 칸 번호라 그대로 가방만
- 테스트: `CarriedStorageTest`(같은 이름 교체 · 빼는 계획)

## 2026-09-26 — 유령 좌클릭

- `input/Clicks` — 아이템을 쓰는 우클릭 뒤 같은 틱에 따라오는 손 흔들기 `LEFT_CLICK_AIR` 를 가린다(`isGhost(event)` · `forget(uuid)`).
  core 가 LOWEST 에서 판정해 이벤트에 붙인다. 2세대 낚시의 `lastRightClickTick` 규칙을 모든 플러그인이 쓰게 올린 것

## 2026-09-25 — 세트의 인첸트 효과

- `CustomItemHook.Provider.setEffects(player)` · `CustomItemHook.setEffects(player)` · `SetEffects(set, name, pieces, effects)` —
  입고 든 세트 단계 가운데 다른 플러그인용 효과가 붙은 것(커스텀아이템이 세고 인첸트가 돌린다)
- `CustomEnchantHook.editEffects` · `describeEffects` — 효과 트리 편집 화면과 로어 한 줄은 문법을 아는 인첸트가
- `ItemRoles.defineSet` — 다른 플러그인의 옛 세트를 커스텀아이템 세트로 옮길 때

## 2026-09-25 — 아이템 연동 허브 `ItemRoles`

- 아이템을 커스텀아이템 한 곳에서 관리하는 창구(사용자 결정). 쓰는 플러그인이 역할(설정 칸 모양까지)을 내놓고, 커스텀아이템이
  아이템마다 역할과 값을 쥔다. 양쪽 어디서 등록해도 같은 곳에 적힌다. 꽂힘·빠짐·변경 알림(`listen`), 옮기기 도구(`retire`·`legacy`·`sample`),
  역할이 아이템을 직접 만드는 `factory`(인첸트 가루처럼 제 표식이 있어야 하는 것). 테스트 `ItemRolesTest`

## 2026-09-25 — 로어를 쥔 쪽과 인첸트 줄

- `CustomItemHook.Provider.redraw(stack)` + companion `redraw` — "이 아이템의 로어는 내가 통째로 쥔다"(커스텀아이템).
  인첸트 플러그인이 자기 줄을 얹는 대신 부른다
- `CustomEnchantHook.Provider.lines(stack)` → `EnchantLines(enchants, status)` · `slots(stack)` — 로어를 쥔 쪽이 인첸트 줄을
  제자리에 끼우고, 인첸트 칸 수는 관리 화면에서만 본다. 둘 다 기본 구현이 있어 공급처를 안 고쳐도 된다

## 2026-09-25 — `ItemMatcher`: 바닐라는 바닐라만

- 바닐라 참조(`ItemRef.Vanilla`)가 **플러그인 아이템과 맞지 않는다** — MMOItems·커스텀아이템·ItemsAdder 등이 알아보는 아이템은
  재질이 같아도 그 플러그인의 것이다. 전에는 에메랄드 화폐가 에메랄드로 만든 커스텀 아이템("타임 스톤", 유물)까지 잔고로 세고
  낼 때 가져갔다. 열쇠·조합 재료·보호권 등 `ItemMatcher` 를 쓰는 모든 플러그인이 같이 바뀐다(인게임에서 발견)

## 2026-09-24 — 화폐 창구 `economy/`

- `Currency`(금액은 `Long`) · `Currencies`(공급처 하나 — inmc-economy). 테스트 `CurrenciesTest`
- `EconomyHook` 이 **`Currencies.default()` 를 먼저**, 없으면 Vault. 둘 다 **부를 때마다 찾는다** — 전에는 켜질 때 한 번만 찾아서
  경제 플러그인보다 먼저 켜진 플러그인은 영영 돈을 못 줬다. Double 금액은 반올림, 거래 기록 까닭은 `<로거 이름>:deposit|withdraw`.
  쓰는 플러그인은 한 줄도 안 바뀌었다

## 2026-09-24 — `ExtraInventory` (가방 밖 칸의 사망 처리 창구)

- `integration/ExtraInventory` 추가. 커스텀아이템의 장착 칸(`/장비`)처럼 바닐라 가방 밖에 아이템을 든 곳을 사망 처리
  플러그인(인벤키퍼)에게 알린다. 같은 이름으로 다시 꽂으면 교체(리로드가 쌓이지 않게). 테스트 `ExtraInventoryTest`

## 2026-09-24 — 화면 오류가 엉뚱한 플러그인 이름으로 찍히던 것

- `MenuListener` 가 화면 오류를 **그 화면의 주인**(`Menu.owner` 가 `InmcHost` 면 그 플러그인) 이름으로 찍습니다.
  리스너가 플러그인마다 하나씩 있고 먼저 받은 쪽이 처리해서, 커스텀아이템 화면의 오류가 `[inmc-urb]` 로 나왔습니다

## 2026-09-23 — 인게임 테스트: 메뉴 클릭이 플러그인 수만큼 처리되던 결함

- **`MenuListener` 가 같은 이벤트를 한 번만 처리하게** 했습니다(`firstDelivery`).
  이 리스너는 core `Menu` 를 쓰는 플러그인 7개가 **각자** 등록하고, 각각이 **모든** core `Menu` 를 받습니다.
  그래서 클릭·드래그·닫기 한 번이 7번 처리됐습니다. 실제 클라이언트로 낚시 대회 사전 신청을 한 번
  누르자 "신청/취소" 가 7줄 번갈아 찍혀 드러났습니다. 토글은 짝수 개 플러그인이면 아무 일도 안 한 것처럼
  보이고, 지급 버튼은 7번 주고, 맡긴 아이템을 돌려주는 닫기 처리는 7번 돌려줍니다
- 처음엔 "마지막 이벤트 하나"만 기억했는데 **그것도 틀렸습니다** — 클릭 처리 안에서 창을 닫으면 닫기
  이벤트가 끼어들어 "마지막"을 덮고, 다음 리스너가 같은 클릭을 또 처리했습니다(무덤 "모두 회수" 한 번에
  "무덤이 사라졌습니다" 두 줄). 처리한 이벤트 **집합**(약한 참조)으로 바꿨습니다
- 등록을 하나로 줄이거나 `owner` 로 가르지 않은 이유는 KDoc 에 — 전자는 그 플러그인이 꺼지면 남의 화면이
  죽고, 후자는 `owner` 없는 화면의 클릭이 취소되지 않습니다
- `MenuListenerDedupTest` 3개. 인게임 재확인: 사전 신청 한 번 = 한 줄, 모두 회수 한 번 = 한 줄

## 2026-09-23 — 실서버 재기동에서 찾은 저장 결함

- **`YamlFileStore`·`YamlFolder` 의 머리말을 주석으로 정규화** (`commentedHeader`).
  머리말은 본문 앞에 글자 그대로 붙는데, 낚시 5곳 · 인벤키퍼 2곳 · 업적 2곳이 `#` 없이
  (대부분 끝 줄바꿈도 없이) 넘기고 있었습니다. 저장된 파일 첫 줄이 `…습니다.items:` 가 되어
  - 낚시 `fish/rods/baits/potions/fillet-items.yml` — 다음 부팅에 파싱 오류, 물고기 0마리
  - 인벤키퍼 `items.yml`·`graves.yml` — **오류 없이** 뿌리 키가 사라져 등록 아이템과
    **살아 있는 무덤(안의 아이템 포함)이 재시작마다 소멸**
  - 업적 `achievements.yml`·`regions.yml` — 오류 없이 업적·지역 0개
- 종료 경로 `flushBlocking` 이 dirty 와 무관하게 쓰므로 **첫 정상 종료에 곧바로 발현**합니다.
  단위 테스트는 저장과 읽기를 따로 봐서 못 잡았고, 테스트 서버를 두 번째로 켰을 때 드러났습니다
- 이미 `# ` 로 시작하는 머리말(몬스터·urb)은 한 글자도 바뀌지 않습니다
- `YamlHeaderTest` 5개 추가 — 머리말 + 저장본을 다시 읽어 뿌리 키가 살아 있는지 확인
- 실서버 확인: 수정본으로 종료(9개 파일 재기록) → 재부팅에서 물고기 70 · 낚싯대 10 ·
  인벤키퍼 아이템 6 · 업적 9 그대로, 오류 0
- 5세대 jar 는 아직 배포 전이라 이미 깨진 파일을 되살리는 코드는 넣지 않았습니다

## 2026-09-23 — 검증

- core 테스트 **52개 통과** (루트 빌드는 core 테스트를 돌리지 않으므로 따로 확인)
- 이번 검증에서 core 코드는 바꾸지 않았습니다. 발견한 결함은 전부 각 플러그인 쪽이었습니다
- `GUIDE.md` 추가

## 2026-09-22 — 15단계 (업적)

- **`event/InmcSignalEvent`** — 플러그인 사이 사건 버스. 구독자가 없으면 객체조차 만들지 않음. 메인 스레드 전용
- **`event/SignalCatalog`** — 발행자가 "이런 값이 온다" 를 등록 → 업적 편집기가 진짜 목록을 그림.
  열쇠 `(source, type)`, 재등록은 교체, `unregisterAll(source)`
- 올리지 않기로 한 것: `ProgressCounters` · `Setting`/`MenuSettingsPage` · `Allowances` —
  소비자가 같은 계약을 원하지 않았습니다(몬스터는 부재삭제가 필요하고 업적은 금지)

## 2026-09-14 — 14단계 (커스텀아이템)

- `CustomItemHook.Provider` 공개 · `data(ref)` · companion 공유 공급처 목록 (`register`/`unregister`)

## 2026-09-14 — 13단계 (낚시)

- `RankBoard.put/remove` · `RankService.put/drop` — 누적이 아니라 **덮어쓰는** 파생 랭킹. 기존 `record()` 경로는 그대로
- `store/DefinitionKey` — 정의 이름 규칙(점 금지) 승격 (인벤키퍼 → 낚시가 두 번째 소비자)

## 2026-09-11 — 12단계 (인벤키퍼)

- `item/BlockRef` · `BlockPlacer` 승격 (랜덤박스 → 인벤키퍼가 두 번째 소비자)

## 2026-09-11 — 10단계 (타이틀포지 편입)

- 레거시 스케줄러 4곳 → 리전 스케줄러 (`ConfigService.async` · 플러시 타이머 · `TickerBase` · `ChatPrompt`)
- 타이틀포지의 `VaultHook` 을 core `EconomyHook` 으로 대체

## 2026-09-11 — 9단계

- `util/TokenBag` · `config/MessageCatalog` · `scheduler/TickerBase` — 세 플러그인의 `Ph` · `Messages` · `Ticker` 공통부
- `InmcHostBase` 는 만들지 않기로 결정 (공유 멤버가 넷뿐이고 `prompts` 는 숫자야구에 없음)

## 2026-09-11 — 8단계

- `store/YamlFileStore` · `YamlFolder` — 저장 골격. **YAML 은 메인에서 만들고 파일 쓰기만 워커**

## 2026-09-11 — 1~3단계

- 이중 기록 제거 (몬스터 `trigger-progress.yml`, 랜덤박스 이름 캐시) — 공유 저장소 `PlayerStore` 로
- `gui/Paging` · `Menu.owner`(리로드 청소 기준) · 페이지 버튼 자리 통일 (뒤로 45 · 이전 46 · 다음 47 · 닫기 53)
- `gui/Editors` · `ConfirmMenu` 승격
