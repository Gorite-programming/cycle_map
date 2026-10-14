# re_test プロジェクト詳細・ロジック仕様書

> 対象: `/Users/gorite/Developer/cycle_map/re_test`
> 根拠: `app/src/main/java/com/gorite/cyclemap/` 配下の実装、および共有モジュール `../application/routing-core` のソース(行番号・定数は作成時点のもの)。
> 「桁数」は、(a) 数値型の有効桁数、(b) 定数・閾値の有効桁、(c) 各計算が保証する実質精度(m・秒など)の意味で記載する。

---

## 1. プロジェクト概要

`re_test` は完全オフライン自転車ナビ「CycleMap」の**新正系統**プロジェクト(`application/` は Google Maps 互換度 ≥ 90% 達成まで本番として維持)。

| 項目 | 内容 |
|---|---|
| 言語/ビルド | Kotlin 2.0.21 / AGP 8.10.1 / Java 11 |
| SDK | compileSdk = targetSdk = 35, minSdk = 33 |
| UI | Jetpack Compose (MVI / UDF) |
| 地図 | MapLibre Native Android 11.8.0 + PMTiles(osmdroid は段階的撤廃) |
| ルーティング | 純 JVM A\*(`:routing-core`、`application/` と共有) |
| 検索DB | SQLite `*.search.db`(`places.search_text` による LIKE 検索。FTS5 非依存) |
| 標高 | 国土地理院 DEM10B PNG タイル(z = 14) |

### 1.1 モジュール構成(`settings.gradle.kts`)

```
:app           re_test/app                       … UI・実装(本書の主対象)
:routing-core  ../application/routing-core       … A*, コストモデル, 逸脱検知(共有・複製しない)
:osm-importer  ../application/osm-importer       … OSM .pbf → .graph 変換 CLI(共有)
```

### 1.2 `app` のパッケージ(ロジック関連)

| パッケージ | 役割 |
|---|---|
| `routing/` | モード別コストモデル、複数経路探索、所要時間推定、リルート管理 |
| `real/` | 契約(`contract/`)の本番実装(`RealRoutePlanner`, `RealReroutePolicy` …) |
| `search/` | 正規化 `SearchNormalizer`、スコアリング `SearchRanking`、営業時間パーサ |
| `data/` | `ElevationRepository`(DEM)、`ReverseGeocoder`、県切替 |
| `tracking/` | GPS 健全性、追跡ポリシー、GPX |
| `power/` | バッテリー、カメラ更新間引き |
| `net/` | `OnlineFeatureGate`(タイムアウト 3 s、失敗時 `Unavailable`) |

### 1.3 設計原則(`architecture_spec.md`)
- MVI/UDF。状態は **低頻度 `MapUiState` / 高頻度 `LocationUiState`(1 Hz GPS, 10–50 Hz 方位) / `NavProgressState`** に分離。
- 1 ファイル 500 行以内。重い処理(SQLite/DEM/グラフ)は `Dispatchers.IO`。
- 完全オフライン動作を保証し、オンラインは任意のフォールバック付き機能。

---

## 2. 数値表現と桁数の前提

| 型 | 有効桁(10進) | 機械イプシロン | 本プロジェクトでの用途 |
|---|---|---|---|
| `Double` (IEEE754 binary64, 仮数 53 bit) | 約 15–17 桁 | $2^{-52}\approx 2.22\times10^{-16}$ | 緯度経度・距離・コスト・スコア |
| `Float` (binary32, 仮数 24 bit) | 約 6–7 桁 | $2^{-23}\approx 1.19\times10^{-7}$ | 速度(`speedMps`)、方位、精度 |
| `Long` (64 bit) | 約 18–19 桁 | — | ノードID、時刻 ms |

### 2.1 緯度経度の桁数と実距離
緯度 1° ≈ 111.32 km(コード定数 `111320.0`)。

$$
\Delta s \approx 111{,}320\ \text{m} \times \Delta\varphi_{\deg}
$$

| 小数桁 | 角度分解能 | 地上距離 |
|---|---|---|
| 小数 5 桁 | $10^{-5}$° | ≈ 1.11 m |
| 小数 6 桁 | $10^{-6}$° | ≈ 0.11 m |
| 小数 7 桁 | $10^{-7}$° | ≈ 1.1 cm |

- `Double` の ULP(緯度 35° 付近): $2^{-47}\approx 7.1\times10^{-15}$° ≈ $7.9\times10^{-10}$ m → **実用上無視可能**。
- もし `Float` で緯度 35° を保持すると ULP $=2^{-18}\approx3.8\times10^{-6}$° ≈ **0.42 m**。よって位置座標は必ず `Double`(`speedMps`/方位のみ `Float`)。
- 経度方向の長さは緯度で縮む: $\Delta x = 111320\cdot\cos\varphi\cdot\Delta\lambda_{\deg}$(緯度 35° で cos≈0.819 → 91.2 km/°)。

---

## 3. 距離計算

### 3.1 Haversine(`SearchRanking.calculateDistanceMeters`, `CameraMotionPolicy.distanceMeters`, `ElevationRepository`, `routing-core: haversineMeters`)

$$
a=\sin^2\!\frac{\Delta\varphi}{2}+\cos\varphi_1\cos\varphi_2\sin^2\!\frac{\Delta\lambda}{2},\qquad
d = 2R\,\operatorname{atan2}\!\big(\sqrt{a},\sqrt{1-a}\big),\quad R=6{,}371{,}000\ \text{m}
$$

- $a$ は `coerceIn(0.0, 1.0)` でクランプ(BUG-82 ガード。丸め誤差で $\sqrt{1-a}$ が NaN になるのを防止)。
- 数値誤差: 計算自体は `Double` で 1e-9 m 級。**支配的誤差は球近似**(WGS84 楕円体との差、最大約 0.3–0.5%)。100 m で ≈ 0.5 m 以下、10 km で ≈ 50 m 以下。
- 表示/ランキング用途では整数 m で十分(桁数としては**距離 m の有効桁 = 3〜4 桁**が意味を持つ)。

### 3.2 点–線分距離(`RealReroutePolicy.distanceToSegmentMeters`)
局所等距円筒近似。基準緯度は現在位置 $\varphi_p$。

$$
x=\lambda\cdot 111320\cdot\cos\varphi_p,\quad y=\varphi\cdot 111320
$$

線分 $AB$ への投影パラメータ:

$$
t=\operatorname{clamp}_{[0,1]}\!\left(\frac{(P-A)\cdot(B-A)}{|B-A|^2}\right),\qquad
d=\big|P-(A+t(B-A))\big|
$$

- $|B-A|^2\le 10^{-4}\ \text{m}^2$(長さ ≤ 1 cm)なら点–点距離にフォールバック。
- 精度: 数 km スケールで誤差 < 0.1%。`111320.0`(6 桁)なので緯度 1° あたり ±0.5 m 程度の定数誤差(真値 110.57–111.69 km/°)。**リルート閾値 30 m に対しては十分**。

### 3.3 方位角(`calculateBearingDegrees`)

$$
\theta=\operatorname{atan2}\!\big(\sin\Delta\lambda\cos\varphi_2,\ \cos\varphi_1\sin\varphi_2-\sin\varphi_1\cos\varphi_2\cos\Delta\lambda\big),\qquad
\theta_{\deg}=(\theta\cdot180/\pi+360)\bmod 360
$$

角度差の正規化: $\delta=\operatorname{wrap}_{[-180,180]}(h-\theta)$。

---

## 4. ルーティング

### 4.1 A\* 探索(`routing-core/Routing.kt`)

$$
f(n)=g(n)+h(n),\qquad h(n)=m_{\min}\cdot d_{\text{haversine}}(n,\text{goal}),\quad m_{\min}=0.90
$$

- **許容性(admissibility)の条件**: すべてのエッジコスト倍率 $\ge m_{\min}$ であること。各モデルは `(base × penalty).coerceAtLeast(0.90)` で下限を保証する。
- `LazyMappedRoadGraph.route` は `heuristicMultiplier ≥ 1.0`(要 `require`)を追加乗算: $h=0.90\cdot m_h\cdot d$。代替ルートでは $m_h=1.15$(最適性を犠牲に探索を偏らせる)。
- 同一の 0.90 を JVM 側 `CyclingCostModel.minimumCostMultiplier()` と `LazyMappedRoadGraph` で**同期必須**(AGENTS.md)。

### 4.2 エッジコスト

$$
C_e = L_e\cdot\max\!\big(0.90,\ b(\text{roadType})\cdot p(\text{grade})\big)\quad[\text{m 相当}]
$$

$L_e$: エッジ長 (m)、$b$: 道路種別倍率、$p$: 勾配ペナルティ(勾配 % が無ければ $p=1$)。`null` は通行不可。

#### (a) 自転車 `CyclingCostModel`(`motorway`, `motorway_link` 不可)

| roadType | primary | secondary | tertiary | residential | unclassified | service | track | path | その他 |
|---|---|---|---|---|---|---|---|---|---|
| $b$ | 0.90 | 0.95 | 1.00 | 1.20 | 1.25 | 1.35 | 1.60 | 1.80 | 1.15 |

勾配 $g$ (%):

$$
p(g)=\begin{cases}
1+0.02\,g+0.002\,g^{2} & g\ge 0\\[2pt]
\max(0.95,\ 1+0.02\,g) & -4\le g<0\\[2pt]
1+0.03\,|g+4| & g<-4
\end{cases}
$$

計算例:
- $g=5\%$: $p=1+0.10+0.002\times25=1.150$。residential なら $1.20\times1.150=1.380$。
- $g=-2\%$: $p=\max(0.95,0.96)=0.96$。
- $g=-8\%$: $p=1+0.03\times4=1.12$。
- 注意: 下り $-4\le g<0$ の下限 0.95 は $g\le-2.5\%$ で飽和。

#### (b) 徒歩 `WalkCostModel`(`motorway`, `motorway_link`, `trunk` 不可)

| roadType | pedestrian/footway | path | steps | residential/living_street | service | tertiary | secondary | primary | その他 |
|---|---|---|---|---|---|---|---|---|---|
| $b$ | 0.90 | 0.95 | 1.30 | 1.00 | 1.10 | 1.20 | 1.40 | 1.60 | 1.20 |

$$
p(g)=\begin{cases}1+0.035\,g & g\ge0\\ 1+0.015\,|g| & g<0\end{cases}
$$

#### (c) 自動車 `CarCostModel`(`steps`, `footway`, `cycleway`, `pedestrian` 不可。勾配は無視)

| roadType | motorway | motorway_link | trunk | primary | secondary | tertiary | unclassified | residential | service | その他 |
|---|---|---|---|---|---|---|---|---|---|---|
| $b$ | 0.90 | 0.95 | 0.95 | 1.00 | 1.05 | 1.15 | 1.30 | 1.40 | 1.60 | 1.40 |

### 4.3 複数経路(ペナルティ法 `PenaltyMultiRouteFinder`)
最大 3 本($i=0,1,2$)。経路採用ごとに通過エッジの倍率を更新:

$$
\pi_{(u,v)}\leftarrow \pi_{(u,v)}\times 1.30,\qquad C'_e=C_e\cdot\pi_e
$$

類似度(現経路ノード集合 $S_c$、既採用 $S_k$):

$$
\text{overlap}=\frac{|S_c\cap S_k|}{|S_c|}>0.70\ \Rightarrow\ \text{棄却}\ (\text{ただし先頭の 1 本は常に採用})
$$

`RealRoutePlanner`(遅延 mmap グラフ側)も代替経路で同じ 0.70 判定を用い、`heuristicMultiplier=1.15`, `arterialPenaltyMultiplier=1.30` を使用。

---

## 5. 所要時間推定(`TravelTimeEstimator`)

### 5.1 セグメント

$$
v_{\text{eff}}=\operatorname{clamp}\big[v_{\min},v_{\max}\big]\Big(v_0\cdot f_{\text{road}}\cdot f_{\text{grade}}\cdot f_{\text{time}}\cdot f_{\text{traffic}}\Big),\qquad
t=\frac{L}{v_{\text{eff}}}
$$

$f_{\text{traffic}}=\operatorname{clamp}_{[0.5,2.0]}(\text{trafficMultiplier})$(既定 1.0、オンライン補正失敗時 1.0)。$L\le0$ なら $t=0$。

| モード | $v_0$ | $v_{\min}$ | $v_{\max}$ |
|---|---|---|---|
| 自転車 | 5.0 m/s (18 km/h) | 2.0 | 8.5 |
| 徒歩 | 1.33 m/s (4.8 km/h) | 0.8 | 1.8 |
| 自動車 | 11.1 m/s (40 km/h) | 4.0 | 25.0 |

### 5.2 道路種別係数 $f_{\text{road}}$
- 自転車: cycleway 1.10 / primary 1.05 / secondary 1.00 / tertiary 0.95 / residential 0.90 / track,path 0.70 / 他 0.90
- 徒歩: pedestrian,footway 1.05 / steps 0.65 / 他 1.00
- 自動車: motorway 2.00 / trunk 1.50 / primary 1.20 / secondary 1.00 / residential 0.75 / service 0.50 / 他 0.80

### 5.3 勾配係数 $f_{\text{grade}}$
- 自転車:
$$
f=\begin{cases}\max(0.40,\ 1-0.04g) & g>0\\ \min(1.30,\ 1+0.03|g|) & -4\le g\le0\\ 1.05 & g<-4\end{cases}
$$
- 徒歩: $g>0$: $\max(0.60,\ 1-0.025g)$ / $g\le0$: $\min(1.10,\ 1+0.01|g|)$
- 自動車: 常に 1.0
- 勾配不明(`null`)は 1.0。

### 5.4 時間帯係数 $f_{\text{time}}$
| 条件 | 係数 |
|---|---|
| 週末 10–17 時 | 0.95(それ以外の週末 1.05) |
| 平日 7–9 時 | 自動車 0.75 / 他 0.88 |
| 平日 17–19 時 | 自動車 0.70 / 他 0.85 |
| 平日 21–24, 0–5 時 | 1.10 |
| その他 | 1.00 |

### 5.5 合計と信号待ち

$$
T=\sum_e t_e+\tau\,N_{\text{signal}},\qquad \tau=\begin{cases}12\ \text{s}&\text{自転車}\\15\ \text{s}&\text{徒歩}\\20\ \text{s}&\text{自動車}\end{cases}
$$

### 5.6 計算例(自転車・平日 12 時・secondary・平坦、$L=1000$ m)
$v=5.0\times1.00\times1.0\times1.00\times1.0=5.0$ m/s → $t=200$ s(3 分 20 秒)。
上り 5%: $f_{\text{grade}}=1-0.20=0.80$ → $v=4.0$ → 250 s。
平日 8 時: $f_{\text{time}}=0.88$ → $v=4.4$ → 227.3 s。

### 5.7 桁数・実装上の注意
- 係数はすべて **小数 2 桁**。積は有効 2–3 桁程度の意味しかなく、**所要時間は秒単位 3 桁(分単位換算)で表示すれば十分**。
- 自転車の理論最大速度 $5\times1.10\times1.30\times1.10=7.87<8.5$ のため、$v_{\max}=8.5$ には到達しない(上限クランプは実質無効)。
- 下限は $5\times0.70\times0.40\times0.85\times0.5\approx0.60<2.0$ になり得るので、最悪ケースでは $v_{\min}=2.0$ が働く(過小推定ではなく過大速度側に補正される点に留意)。
- **既知の簡略化**: `RealRoutePlanner` は経路全体を roadType 固定(主ルート `"secondary"`、代替 `"tertiary"`、メモリグラフ側 `"secondary"`)・勾配 `null` の**1 セグメント**として推定し、`elevationGainMeters = 0.0` を設定している。エッジ単位の `estimateTotalSeconds` は未使用。

---

## 6. 検索ロジック

### 6.1 正規化(`SearchNormalizer.normalize`)
文字ごとに:
- 全角英大文字 `Ａ–Ｚ` → 半角小文字(コード: `ch - 'Ａ' + 'a'.code`)、`A–Z` → 小文字、`０–９` → `0–9`
- カタカナ `ァ–ヶ`(U+30A1–U+30F6) → ひらがな: $\text{code}-\text{0x60}$ (U+3041–U+3096)
- `ー`, `－`, `―` → `ー`、空白類 → 半角スペース、連続空白は 1 つに圧縮
- **注意**: 全角小文字 `ａ–ｚ` は変換されず**そのまま追加**される(半角化されない)。`places.search_text` 生成側との整合は `cautions.md §2.3` を参照。

### 6.2 スコアリング(`SearchRanking.rankCandidates`)
テキストスコア $S_t$(上から最初に該当したもの。いずれにも該当しなければ除外):

| 条件 | $S_t$ |
|---|---|
| `name == query` | 1000 |
| `name` 前方一致 | 500 |
| `searchText` 前方一致 | 400 |
| `name` 部分一致 | 200 |
| `searchText` 部分一致 | 100 |

距離ブースト $B(d)$(単位 m):

$$
B(d)=\begin{cases}100&d\le1000\\50&d\le5000\\20&d\le20000\\0&\text{otherwise}\end{cases},\qquad
\text{score}=S_t+B(d)-2\cdot\frac{d}{1000}
$$

例: 完全一致・500 m → $1000+100-1.0=1099.0$。前方一致・12 km → $500+20-24=496$。
- 位置が無い場合は `candidate.distanceMeters`(既存値)を使用。上位 `limit=20` 件を降順で返す。
- 桁数: スコアは `Double`、実質整数桁(4 桁)+距離項の小数 1–2 桁。
- 観察: 距離ペナルティ $-2d/1000$ は 20 km で −40、100 km で −200 となり、遠距離では $S_t$ 差(100 点刻み)と逆転し得る。

---

## 7. 標高(`ElevationRepository`)

### 7.1 緯度経度 → タイル座標(Web Mercator, z = 14, $n=2^{14}=16384$)

$$
x=\frac{\lambda+180}{360}\,n,\qquad
y=\frac{1-\operatorname{asinh}(\tan\varphi)/\pi}{2}\,n
$$

タイル番号は $\lfloor x\rfloor,\lfloor y\rfloor$ を $[0,n-1]$ にクランプ。タイル内ピクセル:

$$
p_x=\operatorname{clamp}_{[0,255]}\big(\lfloor(x-t_x)\cdot256\rfloor\big),\quad p_y\ \text{同様}
$$

### 7.2 地上解像度
$$
\text{res}=\frac{2\pi R_{eq}\cos\varphi}{256\cdot2^{14}}=\frac{40{,}075{,}016.7\cos\varphi}{4{,}194{,}304}\approx 9.555\cos\varphi\ \text{m/px}
$$
緯度 35°(中国地方付近)で ≈ **7.83 m/px**。したがって標高は最近傍サンプリングで**水平分解能 約 8 m**(DEM10B の名目 10 m と整合)。

### 7.3 標高値のデコード(24 bit)

$$
X=2^{16}R+2^{8}G+B\quad(0\le X<2^{24}=16{,}777{,}216)
$$

$$
h=\begin{cases}0.01\,X & X<2^{23}\ (8{,}388{,}608)\\ \text{海面/無効 → }0.0 & X=2^{23}\ (\text{0x800000})\\ 0.01\,(X-2^{24}) & X>2^{23}\end{cases}
$$

- **分解能 0.01 m(= 1 cm、標高値は小数 2 桁)**。表現範囲は $\pm 83{,}886.07$ m(整数部 5 桁)。
- ただし**精度**は DEM10B 自体の標高誤差(数 m オーダー)で決まり、cm 桁は実質ノイズ。表示は **整数 m(または 0.1 m)** で十分。
- 無効値を 0.0(海面)として返すため、データ欠損域が標高 0 m に見える。

### 7.4 ルート標高プロファイル
サンプル数と間隔:

$$
N=\operatorname{clamp}_{[30,300]}\Big(\operatorname{round}\frac{D}{50}\Big),\qquad \Delta=\frac{D}{N}
$$

$D$: 総距離(m)。サンプル $s=0..N$ の距離 $s\Delta$ を累積 Haversine 距離配列で対応する頂点に割当(最寄りの手前頂点、**補間なし**)。
- 取得できないタイルは `buildRouteElevationProfile` の地形モデル、さらにだめなら 10.0 m。結果は `coerceAtLeast(0.0)`(海面下は 0 に丸め)。
- タイルは事前ダウンロード上限 **30 枚**(z14 の 1 タイル ≈ 2.0 km 角 @35°。経路が通過するタイル数が 30 を超える長距離では、超過分は未取得となりフォールバック標高になる)。
- メモリ LRU 64 枚、ディスク `cacheDir/dem_tiles/`。DL タイムアウト connect 3 s / read 4 s。
- 長距離で $N=300$ が上限のため、$D>15$ km でサンプル間隔 $>50$ m(例: 100 km → 333 m 間隔)。

---

## 8. リルート・逸脱検知

### 8.1 `OffRouteDetector`(routing-core `NavigationStats.kt`)
パラメータ: 逸脱 $T_{\text{off}}$, 連続回数 $K$, 復帰 $T_{\text{rec}}$。
- 通常状態: $d>T_{\text{off}}$ が **連続 $K$ 回** で `isOffRoute=true`、1 回でも $\le T_{\text{off}}$ ならカウント 0 に戻る。
- 逸脱状態: $d\le T_{\text{rec}}$ で復帰(ヒステリシス)。
- 既定値は (50 m, 3, 20 m)。`AdaptiveRerouteManager` は (**30 m**, **3**, **15 m**) で構成。

### 8.2 速度適応閾値(`AdaptiveRerouteManager.onLocationUpdate`)

$$
T_{\text{dyn}}=\begin{cases}\operatorname{clamp}_{[30,60]}(2v)&v>8\ \text{m/s}\\30&\text{otherwise}\end{cases},\qquad
d_{\text{in}}=d\cdot\frac{30}{T_{\text{dyn}}}
$$

検知器へは $d_{\text{in}}$ を入力(閾値 30 m 固定の検知器に対し、距離側をスケーリングして実質閾値を $T_{\text{dyn}}$ にする)。$v>8$ m/s (28.8 km/h) で $T_{\text{dyn}}$ は 30–60 m。
> 注: $v>8$ のとき $2v>16$ なので、下限 30 の `clamp` は $v<15$ m/s で作用し、$v\ge30$ m/s で 60 に飽和。復帰判定側(15 m)にも同じスケーリングが掛かる点に注意。

リルート発火: `isOff` かつ(状態遷移時 または 現在ノードが前回リルート起点と異なる)。実行中ジョブはキャンセルして最新位置で再探索。新経路到着まで旧経路を表示し続ける。

### 8.3 `RealReroutePolicy.shouldReroute`
1. 最短距離 $d_{\min}\ge T$(`deviationThresholdMeters`、運用値 30 m)→ **即リルート**
2. ヘディング有効かつ $|\operatorname{wrap}(h-\theta_{\text{seg}})|>150^\circ$ **かつ** $d_{\min}>15$ m → **逆走リルート**
3. それ以外 `false`(ルート点 2 未満も `false`)。

---

## 9. 追跡・省電力

### 9.1 GPS 健全性(`GpsHealthMonitor`)
経過 $\Delta t=t_{\text{now}}-t_{\text{lastFix}}$:

| 状態 | 条件 |
|---|---|
| HEALTHY | 有効 fix(水平精度 ≤ 100 m)受信直後 |
| WEAK | $\Delta t\ge5000$ ms、または精度 > 100 m で破棄 |
| LOST | $\Delta t\ge15000$ ms |

初回 fix 前は状態を変更しない。

### 9.2 追跡ポリシー(`TrackingPolicy`)
$$
\text{track}=(\text{RESUMED})\ \lor\ \text{isNavigating}\ \lor\ \text{isRecording}
$$

### 9.3 カメラ更新間引き(`CameraMotionPolicy`)
速度 $v$(m/s、負値/`null` は 0)、前回からの移動距離 $d$、経過 $\Delta t$:

| 区分 | 条件 | 判定 |
|---|---|---|
| 停止 | $v<0.5$ (≈1.8 km/h) | $d<2.5$ m → 抑制 / $\Delta t<2500$ ms → 抑制 / 他 → 更新(600 ms アニメ) |
| 低速 | $0.5\le v<3.0$ (≈10.8 km/h) | $d<1.2$ m **かつ** $\Delta t<1000$ ms → 抑制 / 他 → 更新(500 ms) |
| 巡航 | $v\ge3.0$ | 常に更新(400 ms) |

### 9.4 バッテリー(`BatteryMonitor`)
$$
\text{level}=\left\lfloor\frac{100\cdot\text{rawLevel}}{\text{scale}}\right\rfloor\ (\text{整数除算、範囲外は }100),\qquad \text{低電力}\iff \text{level}\le20\%\ \land\ \text{未充電}\ \lor\ \text{OS省電力}
$$
→ 3D 描画を抑制。分解能 1%(整数 2–3 桁)。

---

## 10. 定数一覧(チューニング時の参照用)

| 定数 | 値 | 場所 |
|---|---|---|
| 地球半径 $R$ | 6 371 000 m | Haversine 各所 |
| 度→m | 111 320 m/° | `RealReroutePolicy`, `RealRoutePlanner.findNearestNode` |
| A\* 最小倍率 | 0.90 | `*CostModel`, `LazyMappedRouting` |
| 代替経路 $m_h$ / 幹線ペナルティ | 1.15 / 1.30 | `RealRoutePlanner` |
| 経路ペナルティ / 類似度閾値 / 最大本数 | ×1.30 / 0.70 / 3 | `PenaltyMultiRouteFinder` |
| リルート閾値 / 連続 / 復帰 | 30 m / 3 回 / 15 m | `AdaptiveRerouteManager` |
| 逆走角 / 逆走距離 | 150° / 15 m | `RealReroutePolicy` |
| 速度適応閾値 | $v>8$ m/s で clamp(2v, 30, 60) | `AdaptiveRerouteManager` |
| DEM ズーム / タイル上限 / LRU | 14 / 30 枚 / 64 枚 | `ElevationRepository` |
| 標高サンプル | 50 m 間隔, 30–300 点 | `ElevationRepository` |
| GPS WEAK / LOST | 5 s / 15 s | `GpsHealthMonitor` |
| 低電力閾値 | 20 % | `BatteryMonitor` |
| オンライン通信タイムアウト | 3 s | `OnlineFeatureGate` |

---

## 11. ロジック上の留意点(既知の簡略化・リスク)

1. **A\* 許容性**: 新しいコストモデルを追加する際は倍率下限を 0.90 以上に保つこと。違反すると最短保証が崩れる。
2. **`SearchNormalizer`**: 全角小文字 `ａ–ｚ` が半角化されない。検索DB側生成規則と揃っているか要確認。
3. **所要時間**: 経路全体を単一 roadType・勾配なしで推定。標高・勾配を所要時間へ反映するには `estimateTotalSeconds` を経路エッジ列で呼ぶ必要がある。
4. **標高 0.0 の意味の曖昧さ**: 海面と無効値(`0x800000`)が同じ 0.0。取得失敗時は `fallbackProfile`/10.0 m。
5. **`nearestNodeIndex` は O(n) 線形走査**、`LazyMappedRouting.readEdge()` は 2 GB 超で `offset.toInt()` がオーバーフロー(`application/bugs.md` 参照)。
6. **リルートの距離スケーリング**: 閾値を可変にする代わりに入力距離をスケーリングしているため、復帰閾値(15 m)も同率でスケールされる。

---

## 12. 関連ドキュメント
`README.md` / `architecture_spec.md` / `plans.md` / `cautions.md` / `overhaul_plan.md` / `parity_checklist.md` / `roadmap_p0_to_p7.md` / `scorecard_90percent.md` / `FINAL_REPORT.md` / `NIGHT_LOG.md`
