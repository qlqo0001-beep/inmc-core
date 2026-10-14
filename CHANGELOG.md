# inmc-core 변경 기록

core 에 **무엇이 지금 있는지**는 상위 `ARCHITECTURE.md` 가 진실입니다. 이 파일은 언제 무엇이 올라왔는지의 기록입니다.

---

## 미배포 — 모드 창구 `Realms` · 장소 이름 창구 `PlaceNames` · `ItemCodec`(2026-10-15)

inmc-worlds(Multiverse 3종 대체 — 서버를 모드로 나눠 모드마다 다른 서버처럼)의 1단계. 계획 `~/.claude/plans/validated-questing-sparrow.md`.
- `integration/Realms` — 공급처 하나(inmc-worlds) + 기능 등록부(`Feature`, 관리자가 "모드마다 따로 / 같이" 를 고름 · 기본 따로). 쓰는 쪽은 `scope(key, uuid)` 를
  열쇠에 붙인다(`key(base, scope)` = `base@모드`). **공급처가 없거나 "같이" 면 `""` — 오늘의 열쇠 그대로**라 플러그인을 하나씩 바꿔도 안전하다.
  `scope` 는 공급처가 던지면 그대로 던진다(같이 칸으로 물러나면 물건이 샌다). `RealmsTest` 7개.
- `event/RealmChangeEvent`(몸을 바꾼 뒤) · `RealmFeatureChangeEvent`(토글이 바뀐 즉시 — 재시작 없이).
- `integration/PlaceNames` — 월드·생물군계 한글 이름. 공급처 = 타이틀포지 `places.yml`. essentials `/바이옴찾기` 의 리플렉션을 이것으로 바꿨다.
- `item/ItemCodec` — essentials `InvData` 의 칸·효과 직렬화를 올렸다(inmc-worlds 가 같은 것을 쓴다). essentials 는 이것을 부른다 — 저장 모양은 그대로.

## 미배포 — 우편함 접속 알림은 공급처가 있으면 남은 것만 센다(2026-10-14)

- `reward/Mailbox.notifyOnJoin` — 우편 공급처(inmc-essentials)가 꽂혀 있으면 넘긴 몫은 그쪽 접속 알림(`/우편`)이 이미 세므로 **여기 남은 것만** 센다.
  전에는 제 몫 + 공급처 몫을 합쳐 세어 같은 소포를 두 번 알렸다(숫자게임 "받지 않은 보상이 N개 — /숫자게임 우편함" + 에센셜 "✉ 받을 것 N통 — /우편").
  남은 것은 공급처가 거절해 아직 못 넘긴 것뿐이라 평소에는 0 — 알림이 안 뜬다. 공급처가 없으면 전과 같다. `MailTest` 에 검사.

## 미배포 — 관리 화면 공용 부품: 아이템 올리기 · 가방 클릭 · 색·입자·물약 고르기(2026-10-14)

사용자 요청 "손에 든 아이템으로 등록하는 것을 GUI 에 올리는 방식으로 · 목록은 Shift+클릭으로 · 색은 RGB 로 · 입자·물약은 GUI 로 고르고 한국 이름으로" — 모든 INMC 플러그인이 쓴다.

- `gui/Menu.onPlayerInventoryClick(event)` — 창 아래 **내 가방 칸**을 눌렀을 때. true 면 사건 취소(아이템 제자리). 기본 false 라 지금 화면은 그대로.
- `gui/ItemInputMenu` — 아이템 올리기(27칸): 가방의 아이템을 누르거나 커서로 놓으면 사본이 올라가고 [등록] 이 `onSubmit`.
- `gui/ColorPickMenu` — 색표 27(타이틀포지 칭호 꾸미기의 색표를 늘린 것) + R·G·B 한 칸씩(Shift ±16 · 숫자키 직접) · 밝게/어둡게 · `#RRGGBB`/`r,g,b`/색 이름 직접 입력,
  미리보기는 물들인 가죽 갑옷. `parse(raw)` 는 순수(`ColorPickParseTest`).
- `gui/ParticlePickMenu` · `util/Particles` — 입자 전부를 한국어 이름(표 — 바닐라 번역이 없다)·대표 아이콘으로, 우클릭은 창을 닫고 눈앞에 3초 뿌린 뒤 다시 연다.
  `Particles.spawn` 은 색·블록 자료가 필요한 입자에 기본 자료를 넣는다.
- `gui/PotionPickMenu` · `util/PotionEffects` — 효과 전부를 게임 번역 이름·효과 색 물약으로, 우클릭은 나에게 5초.

## 미배포 — CMI 대체 준비: 우편·투명·WorldEdit 선택·보상 편집 화면(2026-10-10)

inmc-essentials(CMI 대체)가 기대는 것들. 계획 `~/.claude/plans/inmc-essentials.md` 3절.

- `integration/Mail`(object) — **우편 창구**. 공급처 하나(`Provider`: deposit·count·entries·claim·discard)가 꽂히고(`register`/`unregister` — 꽂은 그것일 때만),
  보상을 미루는 쪽은 `send(playerId, origin, source, stacks, money, moneyBy, commands)` 로 넣는다(공급처가 없거나 넣을 것이 없으면 false — 제 길로).
  `Parcel`(kind REWARD/LETTER · origin = 플러그인 이름 · source · 보낸 사람 · 글 · 아이템 바이트 · 돈 · 화폐별 돈 · 명령어 · 시각 · 만료). `listen(owner){}` 는 꽂힘·빠짐마다.
  모든 메서드는 메인에서 불러도 된다 — **공급처의 deposit 은 빠르고 돌아오면 잃지 않아야 한다**(DB 쓰기 스레드에 넘김).
- `reward/Mailbox` — 공급처가 있으면 넣기·세기·읽기·받기·버리기를 `Mail` 로(origin = 호스트 플러그인 이름). 꽂히는 순간·파일을 다 읽은 순간·저장 차례에
  제 메모리에 남은 것을 넘기고 비운 뒤 곧바로 빈 파일을 쓴다. 읽기(비동기)가 꽂힘보다 늦게 끝나도 읽힌 것까지 넘어가고 두 번 가지 않는다.
  공급처가 거절한 것은 제 우편함에 남고 다음 차례에 다시 넘긴다. 한도로 오래된 것 지우기는 공급처가 없을 때만. **다 읽기 전에는 파일을 쓰지 않는다**
  (읽기 전 상태로 덮지 않게). 파일 쓰기는 `AtomicFiles`. 공개 API·`Entry` 는 그대로(숫자야구 화면·PAPI·업적이 쓴다). 테스트 `MailTest`
- `integration/Vanish`(object) — **투명 창구**. 메타데이터 `vanished`(CMI·SuperVanish 관례, 어느 플러그인 값이든 하나라도 참이면 투명) ·
  `isVanished` · `canSee(viewer?, target)`(권한 `inmc.vanish.see` 또는 자기 자신, 누가 보는지 모르면 숨김) · `visibleOnline` · `visibleNames`(제안용) ·
  `set(plugin, player, vanished)`(메타데이터 + 바뀌었을 때만 메인에서 알림) · `listen(owner){ player, vanished -> }` · `unregisterAll(owner)`.
  `paper-plugin.yml` 에 권한 `inmc.vanish.see`(기본 op). 숨기기(hidePlayer)는 essentials vanish 기능이 한다. 테스트 `VanishTest`
  **스레드**: 메타데이터는 스레드에 안전하지 않아 메인만 읽는다. 메인의 읽기·`set`·알림이 `ConcurrentHashMap<UUID, Boolean>` 거울을 고치고, 다른 스레드의
  `isVanished`(디스코드·비동기 채팅)는 거울로 답한다(메인이 아직 못 본 사람은 보이는 것). 거울은 접속 MONITOR 에서 채우고 **퇴장 한 틱 뒤** 지운다
  (core 가 `Vanish.Listener` 를 등록 — 퇴장 MONITOR 까지는 메타데이터·거울 그대로라 타이틀포지가 읽는다. 그 한 틱 안에 다시 들어왔으면 새 객체로 다시 적는다).
  켤 때 접속자 전원을 한 번 읽고 **20틱마다 전원을 다시 읽는다**(`Listener.start`, `REFRESH_TICKS`) — `set` 을 거치지 않고 메타데이터만 건 `/cmi vanish` 도 다른 스레드가
  1초 안에 본다(메타데이터만 읽음 — 권한·PAPI 는 안 묻는다). 다른 스레드의 `set` 은 **접속 중인 사람만** 거울에 적고, 넘긴 알림을 스케줄러가 못 받으면(나간 사람) 지운다.
- `integration/Selections`(object) — WorldEdit/FAWE 선택을 **리플렉션으로** 읽는다(`of(player): Selection?`·`available`). 선택이 없거나 덜 됐거나 WorldEdit 이
  없으면 null(던지지 않는다). 메서드는 그 플러그인 인스턴스마다 한 번 찾는다. 쓰는 곳(예정): essentials 포탈·영역, urb 범위, 던전 방 저장
- `CustomItemHook.Provider.ids()`(기본 빈 목록) + companion `ids(): Map<namespace, ids>` — 지급 명령의 제안·검증용(커스텀아이템이 답한다)
- `CustomItemHook.Provider.makeOnly`(기본 false, 2026-10-13) — **만들기 전용** 공급처 표시(`identify` 늘 null · `create` 만: `inmcfish`·`inmcenchant`·`inmcshop`).
  `hasFirstParty()`·인스턴스 `isEnabled` 는 이것을 세지 않는다(만들기 전용만 있으면 "커스텀아이템 없음" — 몬스터·타이틀포지의 "연동 중" 표시·bStats 가 거짓 참이 되던 것).
  companion `makeOnlyNamespaces()` — 참조로 맞춰 보는 명령(essentials `/찾기`·`/비우기`)이 제안에서 빼고 입력을 거절한다. 테스트 `CustomItemHookMakeOnlyTest`
  `IntegrationReport` 의 부팅 알림도 고침(2026-10-14) — "아이템을 **알아보는** 공급처 없음 … inmc:·서드파티 참조는 스냅샷" 에 꽂힌 만들기 전용 네임스페이스는 "그대로 만듭니다" 라고 덧붙인다(전 문구는 `inmcfish:` 도 스냅샷이 되는 듯 읽혔다).
- `gui/RewardBundleEditor`(open class) — urb 보상 화면의 쓰는 법(빈 칸에 올리고 확인 · 제거 모드 · 쪽 · 상세: 확률/가중치·수량·참조 플러그인·참조/스냅샷·
  아이템 지급 여부·공지·명령어·돈과 화폐)을 core `RewardBundle` 위로. 지급 방식 순환·N개 추첨 개수. **보상에는 사본이 들어가고 올린 아이템은 돌려준다.**
  값 입력은 `DialogForm`. 생성자 `(owner, viewer, title, bundle, resolver, save, back)`. 테스트 `RewardBundleEditorLayoutTest`
  하위가 바꾸는 것: `commandHints`(명령어 입력창 설명) · `showAnnounce`(기본 true — false 면 상세의 공지 전환 칸과 ★ 줄, "＋ 돈·명령어 보상" 안내의 '공지' 를 숨긴다. 업적이 씀, 공지는 연출 층 전담)
- `reward/RewardService.give` 의 보상 공지(`reward-announce`, 타이틀포지 이름)는 받은 사람이 투명이면 그를 볼 수 있는 사람과 콘솔에게만 —
  투명이 새는 곳 막기(C8). 투명이 아니면 예전처럼 `Bukkit.getServer().sendMessage`. `RewardHost`·공개 API 는 그대로

## 미배포 — 커스텀아이템 능력치 다리(2026-10-09)

- `CustomItemHook.Provider.stats(stack)`·`invalidateStats(uuid)` + companion `stats`·`registerStatSource`/`unregisterStatSource`/`externalStats`/`invalidateStats`/`statSources`.
  MMOItems 기능을 쓰던 곳이 커스텀아이템으로도 되게(사용자 결정 "MI 를 쓰는 기능은 CI 로도"): 타이틀포지 칭호 능력치는 바깥 출처로, 몬스터 장비 능력치는 `stats` 로.
  바깥 출처는 바닐라가 아닌 능력치만 — 바닐라 속성은 출처가 직접 건다.

## 미배포 — 화폐 고르기 · 바닐라 한글 이름(2026-10-08)

- `integration/EconomyHook` 에 **화폐 id 인자**: `balance/has/withdraw/deposit/format(…, currency)` — `Currencies.get(id)` 로, 비었거나 모르면 기본 길
  (모르는 id 는 한 번만 경고). `currencies()`·`currencyName(id)`·`multiCurrency` 는 편집 화면용. 사용자 결정 "돈을 주는 곳 전부"(업적 보상·숫자게임 참가비/베팅·
  낚시 대회 참가비·urb 상자 비용·core 보상 묶음).
- `gui/CurrencyPick`(object) — "기본 화폐" + 화폐 목록을 `PickMenu` 로 고른다. 화폐가 하나뿐이면 부르지 않는 것이 규칙.
- `reward/RewardEntry.currency` · `RewardService.Payout.moneyBy`(화폐 id → 금액) · `Mailbox.Entry.moneyBy` — 이름 있는 화폐의 돈은 따로 모아 그 화폐로 준다.
  우편함은 준 것을 지워 가며 하나라도 막히면 나머지를 들고 남는다.
- `util/VanillaNames`(object) — 바닐라 재질·엔티티의 한글 이름(`of(Material)`·`of(EntityType)`). 출처는 inmc-discord 가 받아 둔 클라이언트 번역
  `plugins/inmc-discord/assets/objects/assets/minecraft/lang/ko_kr.json` — 없으면 null(영어 그대로). 상점·경매장 검색이 쓴다.

## 미배포 — 모든 화면이 같이 좋아지는 것(2026-10-08)

- `gui/Icon`: 우리가 만든 아이콘(`of`·`relabel`)은 재질이 딸고 오는 툴팁 줄(속성·내구·염색·물약·악기 …)을 숨긴다 — 검·갑옷·물약 재질 아이콘에
  "주로 사용하는 손에 있을 때…" 가 따라 나오던 것(10-07 메모). `blank` 은 툴팁 자체를 숨긴다(빈 칸에 빈 상자가 안 뜬다). `annotate` 는 실제 아이템이라 그대로.
- `gui/Menu`: 버튼(핸들러가 있는 칸)을 누르면 작은 클릭 소리. 개인 설정 `core.ui-click`(기본 켬)으로 끈다 — 설정 화면에 저절로 뜬다.
- `input/ChatPrompt`: 채팅 대신 **입력창(`DialogForm`)** 을 연다 — 첫 줄이 제목, 나머지가 설명, 칸 하나. 숫자는 범위까지 검사해 틀리면 친 값을 둔 채
  다시 연다. 빈 값·Esc = 취소. `Editors.prompt*` 를 거치는 ~210곳(여덟 플러그인)이 한 번에 바뀌었다. 채팅 경로는 `-Dinmc.prompts=chat`. 테스트 `ChatPromptSplitTest`.
- `gui/PickMenu<T>`: 인첸트·상점이 글자 단위로 같은 것을 각자 갖던 "고르는 화면"을 올렸다. `Editors.pickHint`·`PICK_FROM`(7) — 보기가 그 이상이거나
  가변 목록이면 `cycle` 대신 이것(사용자 2026-09-30).

## 미배포 — 원자 쓰기 · 켜진 뒤 한 번 보고 · 서버 API 판 경고(2026-10-08)

- `util/AtomicFiles.write(file, text)` — `.tmp` 에 다 쓴 뒤 `ATOMIC_MOVE`. `ConfigService.save`·`YamlFileStore`·`YamlFolder` 가 쓴다(지뢰 10 해소 —
  쓰다 죽어도 잘린 YAML 이 안 남는다). 던전이 각자 하던 것을 옮겼다. 테스트 `AtomicFilesTest`
- `integration/IntegrationReport` — 모든 플러그인이 켜진 뒤(`ServerLoadEvent`) **한 번만** "커스텀 아이템 공급처 없음"·"MMOItems 미설치".
  `CustomItemHook.setup`·`MMOItemsHook.setup` 의 그 줄을 뺐다 — 커스텀아이템이 15번째로 켜져 열 플러그인이 거짓 "없음", 열여섯이 "미설치" 를 찍었다.
  `CustomItemHook.hasFirstParty()`
- `ApiVersionCheck` — 우리 플러그인의 `api-version` 이 서버가 품은 paper-api(`libraries/io/papermc/paper/paper-api/<판>/`)보다 새로우면
  켜진 뒤 경고 한 줄(테섭 Leaf 26.2 build 46 = 26.1.2 — 업적 토스트의 `NoSuchMethodError` 가 여기서 왔다). 테스트 `ApiVersionCheckTest`

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
