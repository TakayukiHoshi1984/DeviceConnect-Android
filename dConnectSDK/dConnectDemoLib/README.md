# dConnectDemoLib

プラグインのデモアプリケーション (以下、デモ) を Android 端末上のストレージにインストールし、Device Connect Manager の HTTP サーバーでホスティングするための機能を提供します。

デモは HTML + JS で実装されるものとします。

## 要件
API level 14 以上

## 機能

### インストール機能
DemoInstaller クラスでデモのインストール・上書き・削除の機能を提供します。

プラグインの assets フォルダ直下にデモ一式を圧縮した ZIP ファイルに配置しておくことで、DemoInstaller が端末上にデモをインストールするようになります。

### 更新タイミング判定機能
デモを更新すべきタイミングについては、DemoInstaller.isUpdateNeeded(Context) で判定することができます。

更新すべきと判定される条件は以下のとおりです。

- 既にデモがインストールされていること。
- 前回のインストール以降、Hostプラグインの versionName が更新されていること。

判定を行うタイミングについては、プラグインごとに決めることができます。Hostプラグインではプラグイン起動時に判定を行っています。

### デモインストール用UI
DemoSettingFragment を Activity 上で使用することで、以下のUIをユーザーに提供することができます。

- インストール
- 削除
- 上書き
- 既定のブラウザでの表示
- ホーム画面上にショートカットを作成

## 使い方

### 準備
dConnectDemoLib の使用にあたって、以下の項目を決定してください。

- デモのソースコードの置き場所
- APKに同梱する際のZIPファイル名
- デモのインストール先のフォルダ名
- デモのトップページ
- プラグインのパッケージ名
- デモの更新判定のタイミング
- デモインストールUIを持たせる設定画面

以降、ExamplePlugin というAndroidStudioプロジェクトで本ライブラリを使用するという設定で説明します。

ExamplePlugin での前提は以下のとおりです。

- デモのソースコードの置き場所: ExamplePlugin/path/to/demo/src
- APKに同梱する際のZIPファイル名: example-demo.zip
- デモのインストール先のフォルダ名: example-demo
- デモのトップページ: example-demo/index.html
- プラグインのパッケージ名: com.example.plugin
- デモの更新判定のタイミング: プラグイン起動時
- デモインストールUIを持たせる設定画面: ExampleSettingActivity

### 1. インポート
プラグインの build.gradle に以下を追加してください。

```groovy
repositories {
    maven { url 'https://raw.githubusercontent.com/DeviceConnect/DeviceConnect-Android/master/dConnectSDK/dConnectDemoLib/repository/' }
}

dependencies {
    implementation 'org.deviceconnect:dconnect-demo-lib:0.9.0'
}
```

### 2. デモ圧縮設定
同じく、プラグインの build.gradle に以下を追加してください。

```groovy
android {

    ...
    
    task zipDemo(type:Zip) {
        File demoDir = new File(projectDir, '../path/to/demo/src')
        File assetsDir = new File(projectDir, 'src/main/assets')

        from demoDir
        destinationDir assetsDir
        archiveName = 'example-demo.zip'
    }
    
    tasks.preBuild.dependsOn(zipDemo)
}
```

上記のスクリプトにより、ExamplePlugin をビルドする時に ExamplePlugin/path/to/demo/src フォルダの内容が example-demo.zip に圧縮され、APK に同梱されるようになります。

### 3. AndroidManifest.xml の記述

インストール時にファイル操作を行いますので、プラグインのAndroidManifest.xml に `WRITE_EXTERNAL_STORAGE` パーミッションを追加してください。

```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```

次に、メインアクティビティを設定してください。ショートカット作成時、Android フレームワークがショートカット作成元を特定するために必要となります。

```xml
<activity android:name="com.example.plugin.ExampleSettingActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
    </intent-filter>
</activity>
```

### 4. DemoInstaller の拡張

DemoInstaller クラスを拡張し、デモの ZIP ファイル名とデモのインストール先のフォルダ名を指定します。

```java
public class ExampleDemoInstaller extends DemoInstaller {

    private static final String DEMO_DIR = "example-demo";
    
    private static final String ZIP_NAME = "example-demo.zip";

    public ExampleDemoInstaller(final Context context) {
        super(context, DEMO_DIR, ZIP_NAME);
    }
}
```

この設定により、example-demo.zip が下記のフォルダの下に解凍されるようになります。

```
/storage/emulated/0/org.deviceconnect.android.manager/com.example.plugin/example-demo
```

注意: `/storage/emulated/0` の部分は端末によって異なる場合があります。

### 5. DemoSettingFragment の拡張

以下のように DemoSettingFragment を拡張し、デモインストール用 UI を用意します。

以下の例では、ストレージ操作のパーミッションを取得するために、プラグインSDK の PermissionUtility を使用しています。

```java
public class ExampleDemoSettingFragment extends DemoSettingFragment {

    private static final String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected DemoInstaller createDemoInstaller(final Context context) {
        return new ExampleDemoInstaller(context);
    }

    @Override
    protected String getDemoDescription(DemoInstaller demoInstaller) {
        return ""; // TODO デモについての説明
    }

    @Override
    protected int getShortcutIconResource(final DemoInstaller installer) {
        return 0; // TODO デモのアイコン画像
    }

    @Override
    protected String getShortcutShortLabel(final DemoInstaller installer) {
        return ""; // TODO デモのショートカットのショートラベル
    }

    @Override
    protected String getShortcutLongLabel(final DemoInstaller installer) {
        return ""; // TODO デモのショートカットのロングラベル
    }

    @Override
    protected String getShortcutUri(final DemoInstaller installer) {
        return "gotapi://shortcut/" + installer.getPluginPackageName() + "/example-demo/index.html";
    }

    @Override
    protected ComponentName getMainActivity(final Context context) {
        return new ComponentName(context, ExampleSettingActivity.class);
    }

    @Override
    protected void onInstall(final Context context, final boolean createsShortcut) {
        requestPermission(context, new PermissionUtility.PermissionRequestCallback() {
            @Override
            public void onSuccess() {
                install(createsShortcut);
            }

            @Override
            public void onFail(final @NonNull String deniedPermission) {
                showInstallErrorDialog("Denied permission: " + deniedPermission);
            }
        });
    }

    @Override
    protected void onOverwrite(final Context context) {
        requestPermission(context, new PermissionUtility.PermissionRequestCallback() {
            @Override
            public void onSuccess() {
                overwrite();
            }

            @Override
            public void onFail(final @NonNull String deniedPermission) {
                showOverwriteErrorDialog("Denied permission: " + deniedPermission);
            }
        });
    }

    @Override
    protected void onUninstall(final Context context) {
        requestPermission(context, new PermissionUtility.PermissionRequestCallback() {
            @Override
            public void onSuccess() {
                uninstall();
            }

            @Override
            public void onFail(final @NonNull String deniedPermission) {
                showUninstallErrorDialog("Denied permission: " + deniedPermission);
            }
        });
    }

    private void requestPermission(final Context context, final PermissionUtility.PermissionRequestCallback callback) {
        PermissionUtility.requestPermissions(context, getMainHandler(), PERMISSIONS, callback);
    }
}

```

ExampleDemoSettingFragment を ExampleSettingActivity に組み込む手順については割愛します。

### 6. 自動更新の実装
最後に、プラグイン起動時の自動更新処理を実装します。

```java
public class ExamplePlugin extends DConnectMessageService {

    /**
     * デモページインストーラ.
     */
    private DemoInstaller mDemoInstaller;

    /**
     * デモページアップデート通知.
     */
    private DemoInstaller.Notification mDemoNotification;
    
    /**
     * デモページ関連の通知を受信するレシーバー.
     */
    private final BroadcastReceiver mDemoNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            String action = intent.getAction();
            mDemoNotification.cancel(context);
            if (DemoInstaller.Notification.ACTON_UPDATE_DEMO.equals(action)) {
                updateDemoPage(context);
            }
        }
    };

    @Override
    protected void onCreate() {
        ...
        
        mDemoInstaller = new ExampleDemoInstaller(getApplicationContext());
        mDemoNotification = new DemoInstaller.Notification(
                1,  // TODO 自動更新通知の識別子
                "Example Plugin", // TODO プラグインの名前
                0,  // TODO 自動更新通知用のアイコン画像
                "com.example.plugin.channel", // TODO 自動更新通知用チャネルのチャネルID
                "Example Plugin Demo Page", // TODO 自動更新通知用チャネルのタイトル
                "Example Plugin Demo Page" // TODO 自動更新通知用チャネルの説明
        );
        
        registerDemoNotification();
        updateDemoPageIfNeeded();
        
        ...
    }
    
    /**
     * 自動更新失敗時のリトライ命令のレシーバーを登録する.
     */
    private void registerDemoNotification() {
        IntentFilter filter  = new IntentFilter();
        filter.addAction(DemoInstaller.Notification.ACTON_CONFIRM_NEW_DEMO);
        filter.addAction(DemoInstaller.Notification.ACTON_UPDATE_DEMO);
        registerReceiver(mDemoNotificationReceiver, filter);
    }
    
    /**
     * 自動更新が必要であれば実行する.
     */
    private void updateDemoPageIfNeeded() {
        final Context context = getApplicationContext();
        if (DemoInstaller.isUpdateNeeded(context)) {
            updateDemoPage(context);
        }
    }

    /**
     * 自動更新を実行する.
     */
    private void updateDemoPage(final Context context) {
        mDemoInstaller.update(new DemoInstaller.UpdateCallback() {
            @Override
            public void onBeforeUpdate(final File demoDir) {
                // NOTE: 特に処理を行う必要はなし
            }

            @Override
            public void onAfterUpdate(final File demoDir) {
                // 自動更新完了を通知バーでお知らせする.
                mDemoNotification.showUpdateSuccess(context);
            }

            @Override
            public void onFileError(final IOException e) {
                // 自動更新失敗を通知バーでお知らせする. （ファイル操作エラーが原因）
                mDemoNotification.showUpdateError(context);
            }

            @Override
            public void onUnexpectedError(final Throwable e) {
                // 自動更新失敗を通知バーでお知らせする.（予期していないエラーが原因）
                mDemoNotification.showUpdateError(context);
            }
        }, new Handler(Looper.getMainLooper()));
    }
}
```