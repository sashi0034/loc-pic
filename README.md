# LocPic

写真・撮影時刻・現在地・短いメモをひとまとめにし、Google Mapsリンク付きでSlackへ投稿するAndroidアプリです。投稿できなかった写真は端末内に残り、「再送」画面からまとめて送り直せます。成功した写真も「履歴」画面に残ります。

## セットアップ

1. Android Studioでこのフォルダーを開きます。
2. Android SDK 37が未導入なら、SDK Managerから `Android SDK Platform 37` と `Android SDK Build-Tools 37.0.0` を導入します。この作業フォルダーでは `.android-sdk` に導入済みです。
3. `secrets.yml.example` を `secrets.yml` にコピーし、Slackの値を設定します。

```yaml
slack_bot_token: "xoxb-your-bot-token"
slack_channel_id: "C0123456789"
```

4. Gradle Sync後、カメラと位置情報を利用できる実機で実行します。

設定前でも撮影できます。その場合は投稿失敗として端末内に保存されるため、設定後に「再送」画面から一括送信できます。

Slack AppにはBot Token Scopeの `files:write` を付け、投稿先チャンネルへBotを招待してください。チャンネル名ではなく `C` から始まるチャンネルIDを設定します。

## 動作

- 撮影: 写真をアプリ専用領域へ保存し、現在地を最大10秒待って取得してSlackへ投稿します。
- 投稿文: 内容、撮影時刻、`https://www.google.com/maps?q=緯度,経度` を含みます。
- 履歴: 投稿済み・未送信を含め、写真、メモ、時刻、地図リンクを一覧表示します。
- 再送: 未送信写真を一括処理し、まだ失敗する写真だけを端末に残します。
- 権限: カメラは必須です。位置情報を許可しない場合も撮影・投稿できますが、地図リンクは付きません。

## セキュリティ上の注意

`secrets.yml` はGit管理対象外ですが、Bot Tokenはビルド時にAPKへ埋め込まれるため、配布APKから完全には秘匿できません。社内限定の試作・小規模運用を想定した構成です。一般公開する場合は、Bot Tokenを保管するバックエンド経由へ変更してください。

## テスト

```powershell
./gradlew.bat test
```
