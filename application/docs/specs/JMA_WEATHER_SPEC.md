# CycleMap 天気機能仕様案（気象庁JSON）

## 1. 目的と対象範囲

CycleMapで、通信可能な場合に気象庁ホームページの予報データを取得し、天気・気温を表示する。取得結果は端末に保存し、オフライン時にも直近の予報を表示する。保存データが期限切れで通信もない場合は、天気が更新できないことを専用状態で示す。

本書はAndroidアプリへの実装仕様案と、API構造を確認する試験コードの仕様を定義する。現在の試験コードは、山口県・山口市を固定入力として地域コード解決とAPI取得を検証するもので、GPSの逆ジオコーディング・キャッシュ・画面表示は未実装。

## 2. 採用するデータソース

気象庁ホームページが利用するJSONをHTTP GETで取得する。ログイン・APIキーは不要だが、気象庁が一般向けAPIとして仕様・互換性を保証しているものではない。URL・JSON構造の変更に備え、通信失敗や未知の構造を通常の画面状態として処理する。

| 用途 | URL |
|---|---|
| 地域階層コード | `https://www.jma.go.jp/bosai/common/const/area.json` |
| 予報地域と気温地点の対応 | `https://www.jma.go.jp/bosai/forecast/const/forecast_area.json` |
| 府県予報データ | `https://www.jma.go.jp/bosai/forecast/data/forecast/{officeCode}.json` |
| 短期天気概況（必要な場合） | `https://www.jma.go.jp/bosai/forecast/data/overview_forecast/{officeCode}.json` |

例：東京都の予報データは `https://www.jma.go.jp/bosai/forecast/data/forecast/130000.json`。

### 参照リンク

- [気象庁 地域階層JSON（area.json）](https://www.jma.go.jp/bosai/common/const/area.json)
- [気象庁 予報地域・アメダス地点対応JSON（forecast_area.json）](https://www.jma.go.jp/bosai/forecast/const/forecast_area.json)
- [気象庁 東京都の予報JSON（例）](https://www.jma.go.jp/bosai/forecast/data/forecast/130000.json)
- [気象庁ホームページの利用規約](https://www.jma.go.jp/jma/kishou/info/coment.html)
- [気象庁 予報区等GISデータ](https://www.data.jma.go.jp/developer/gis.html)
- [非公式JSON構造の解説・サンプル](https://github.com/misohena/el-jma/blob/main/docs/how-to-get-jma-forecast.org)
- [試験コード JmaWeatherProbeTest.kt](app/src/test/java/com/gorite/cyclemap/JmaWeatherProbeTest.kt)

## 3. 技術スタック

| 領域 | 採用技術・方針 |
|---|---|
| アプリ | Kotlin 2.0.21、Android Gradle Plugin 8.10.1、Java 11 bytecode、minSdk 33 |
| UI | Jetpack Compose、既存画面状態管理に合わせたUI state / UDF |
| HTTP | Android標準 `HttpURLConnection`。I/O dispatcherで実行し、接続・読込タイムアウトを設定 |
| JSON | 現在の `re_test` に存在するGsonを利用。API用の新規通信SDKは追加しない |
| 座標 | 既存GPS位置を利用。地域名は端末内の逆ジオコーディングを優先 |
| 地域対応 | `area.json` と `forecast_area.json` をAPK assetsに同梱する案。実装時は出典・取得日を記録 |
| 保存 | 小規模な最新予報・取得メタデータはアプリ専用領域に保存（SharedPreferencesまたはJSONファイル）。履歴DBは初期範囲外 |
| 通信検知 | `ConnectivityManager.NetworkCallback` で接続復帰を検知。画面復帰時にも期限を確認 |
| 更新 | 前面利用中は1時間ごとに更新要否を確認。接続中の復帰時は即時確認。バックグラウンドでの厳密な毎時実行は保証しない |

既存の `INTERNET` 権限は `re_test/app/src/main/AndroidManifest.xml` で確認する。ネットワーク状態監視を追加する際は `ACCESS_NETWORK_STATE` の有無を確認して必要なら宣言する。

## 4. 地域解決仕様

### 4.1 市区町村から予報コードを得る

1. GPS緯度経度から、アプリ内で都道府県・市区町村名を逆引きする。
2. 市区町村名単独ではなく、都道府県名と組にして `area.json` の `class20s` を検索する。同名自治体の誤選択を避ける。
3. 見つかった `class20s` の `parent` を `class15s`、さらに `class10s` へたどる。
4. `class10s` の `parent` から府県予報区（`offices`）コードを得る。このコードが予報JSONのURL末尾になる。
5. `forecast_area.json` の府県予報区配下から該当 `class10` を探す。予報の天気・降水確率はこの区域コードに対応する値を使う。
6. 同対応行の `amedas` 地点コードを用いて、予報JSON内の `temps` を探す。気温地点が得られない場合は気温を「—」表示し、別区域の値であるかのように流用しない。
7. 市区町村名が特定できない、対応表にない、JSON構造が想定外の場合は、GPSから得られた府県予報区の先頭または代表区域へフォールバックし、UI上の対象地域名を明示する。

気象庁の階層は行政界と完全一致するものではない。画面では「山口市」ではなく、必要に応じて実際の予報対象区域（例：「中部」）も併記する。

### 4.2 APK同梱データ

`area.json` と `forecast_area.json` は小さな地域対応データとしてAPK assetsへ格納し、初回オフラインでも地域コードを解決できるようにする。これらは気象庁側で変更される可能性があるため、更新日を記録し、アプリ更新時に差し替える。オンラインで動的取得して同梱データを自動更新する機構は初期版の必須要件に含めない。

## 5. APIレスポンスの読み取り

`forecast/{officeCode}.json` は配列で、先頭要素の `timeSeries` に短期予報が入る。各timeSeriesの `areas` は項目ごとに異なる地域粒度・コードを持つ。

- 天気: `timeSeries[].areas[]` の対象区域 `area.code` を照合し、`weathers[]` と `weatherCodes[]` を読む。
- 降水確率: 該当区域のtimeSeriesに `pops[]` がある場合に読む。
- 気温: `temps[]` を持つtimeSeriesから、`forecast_area.json` が対応付けたアメダス地点コードを照合する。
- 予報時刻: 対応するtimeSeriesの `timeDefines[]` と同じ配列インデックスを使用する。
- 空文字、欠落、未掲載はnull相当として扱う。`0`に変換しない。

天気・気温のtimeSeriesは同じ配列・同じ時間間隔とは限らない。時刻配列との対応を項目ごとに保ち、固定インデックスだけで今日の値を決めない。今日の表示値は現在時刻を含む時間帯、または日別予報の定義に従い選ぶ。

## 6. 更新・キャッシュ・オフライン状態

- キャッシュには正規化した表示用予報、取得時刻、気象庁 `reportDatetime`、office/class10/市区町村コード、対象区域名を保存する。
- アプリ起動・前面復帰・ネットワーク接続復帰時に、キャッシュの有効期限を確認して必要なら取得する。
- 前面利用中は1時間周期で更新要否を確認する。取得失敗時は短時間に連続再試行せず、次の復帰・周期で再試行する。
- 同じofficeコードへの同時リクエストを抑止する。1時間のポーリングは利用側の確認周期であり、気象庁の発表頻度そのものではない。
- 有効期限の初期値は取得から3時間を提案する。気象庁の発表は通常1日数回であるため、毎時の通信は更新確認に過ぎず、同一 `reportDatetime` の場合は保存値を維持する。
- キャッシュが有効ならオンライン・オフラインにかかわらず表示する。
- キャッシュが期限切れかつオフラインなら「天気情報が古くなっています。ネット接続後に更新します」など専用表示を出し、最後の発表/取得時刻を示す。
- 期限切れでオンラインなら古い値を表示したまま更新し、失敗時は「更新できませんでした」と最終取得時刻を出す。
- キャッシュが一度もないオフライン状態は「天気情報は未取得です。ネット接続後に取得します」と表示する。

### 状態モデル案

`Loading`, `Fresh(data)`, `Stale(data, offline)`, `Unavailable`, `Refreshing(data?)`, `Error(data?, reason)`。UI層で通信状態と鮮度を混在させず、Repositoryが鮮度を判定できるメタデータを渡す。

## 7. 帰属表示と利用上の注意

気象庁ホームページの利用規約に従い、天気表示の近くまたはライセンス画面に「出典：気象庁ホームページ」と対象ページへのリンクを表示する。データを加工・要約した場合、気象庁が作成した文章と誤認させない。気象庁の発表データをアプリ独自予報として再解釈・発表しない。

一般利用者へ配布するアプリでは、不特定多数端末からの集中アクセスを避ける。端末キャッシュ、接続・発表時刻を考慮した重複取得抑止、タイムアウト、指数的または十分な間隔の再試行を実装する。現時点で公式のAPI SLAや安定した互換性保証はない。

## 8. Kotlin取得サンプル

以下はAndroid標準APIでGETし、GsonでJSONを読む最小例。実際のRepositoryでは `Dispatchers.IO` で呼び、例外をUI用状態へ変換する。

```kotlin
data class JmaForecastResponse(
    val body: String,
    val reportDatetime: String?,
)

suspend fun fetchJmaForecast(officeCode: String): JmaForecastResponse =
    withContext(Dispatchers.IO) {
        val url = URL(
            "https://www.jma.go.jp/bosai/forecast/data/forecast/$officeCode.json"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "CycleMap/1.0 (Android)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "JMA HTTP ${connection.responseCode}"
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JsonParser.parseString(body).asJsonArray
            val reportDatetime = root.firstOrNull()
                ?.asJsonObject?.get("reportDatetime")?.takeIf { !it.isJsonNull }?.asString
            JmaForecastResponse(body, reportDatetime)
        } finally {
            connection.disconnect()
        }
    }
```

### 地域階層をたどるサンプル

```kotlin
fun resolveOfficeCode(area: JsonObject, cityCode: String): String? {
    val city = area.getAsJsonObject("class20s")?.getAsJsonObject(cityCode) ?: return null
    val class15Code = city.get("parent")?.asString ?: return null
    val class15 = area.getAsJsonObject("class15s")?.getAsJsonObject(class15Code) ?: return null
    val class10Code = class15.get("parent")?.asString ?: return null
    val class10 = area.getAsJsonObject("class10s")?.getAsJsonObject(class10Code) ?: return null
    return class10.get("parent")?.asString // officesコード。例: 350000
}
```

気温・天気配列の詳細な選択、キャッシュ、通信状態監視はサンプルをそのまま本番利用せず、上記の仕様に従ってRepositoryへ実装する。

## 9. 現在の試験結果と残作業

試験コード `JmaWeatherProbeTest` で、山口市（`3520300`）→山口県（`350000`）→中部（`350020`）への対応と、天気・気温地点の読み出しを確認した。テスト時点の例では、天気「くもり 夜 晴れ」、気温地点 `81286` の値を取得した。

本番機能化では次を実施する。

1. `area.json` と `forecast_area.json` をassetsへ追加し、出典・取得日を添える。
2. 逆ジオコーディングの結果形式を確認し、市区町村・県の一意な照合を行う。
3. `WeatherRepository`、パーサー、永続キャッシュ、NetworkCallback、Compose表示状態を実装する。
4. 予報JSONの項目欠落・空値・未知コード・通信切断・地域変更をテストする。
5. 実機でオンライン取得後に機内モードへ切り替え、キャッシュ表示と期限切れ表示を確認する。

