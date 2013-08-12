package com.hide_ab.apitest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.view.Menu;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView tvTemperature01;
    private MyHandler myHandler = null;
    private Timer myTimer;
    public int iFlg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.iFlg = 0;

        // 画面構成を適用
        setContentView(R.layout.activity_main);

        this.tvTemperature01 = (TextView)findViewById(R.id.temperature01);

        // 定期通信処理へのハンドラ
        this.myHandler = new MyHandler();
        // タイマから呼ばれる定期通信用の通信タスクを生成
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Message msg = new Message();
                MainActivity.this.myHandler.sendMessage(msg);
            }
        };

        // タイマにスケジュール登録
        this.myTimer = new Timer();
        myTimer.schedule(task, 1000, 5000);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // タイマを停止
        this.myTimer.cancel();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            // 非同期通信タスクを定義
            HttpPostTask task = new HttpPostTask(
                MainActivity.this,
                "http://ec2-54-248-130-198.ap-northeast-1.compute.amazonaws.com/observations.xml",
                // タスク完了時に呼ばれるUIのハンドラ
                new HttpPostHandler() {
                    // 正常終了時
                    @Override
                    public void onPostCompleted(String response) {
                        MainActivity.this.tvTemperature01.setText(R.string.strOK);
                    }
                    // 異常終了時
                    @Override
                    public void onPostFailed(String response) {
                        MainActivity.this.tvTemperature01.setText(R.string.strNG);
                    }
                }
            );
            // タスクを開始
            task.execute();
        }
    }

    // タスク完了時に呼ばれるUIのハンドラ
    public abstract class HttpPostHandler extends Handler {
        // このメソッドは隠ぺいし，Messageなどの低レベルオブジェクトを直接扱わないでもよいようにさせる
        public void handleMessage(Message msg) {
            boolean isPostSuccess = msg.getData().getBoolean("http_post_success");
            String http_response = msg.getData().get("http_response").toString();

            if(isPostSuccess) {
                onPostCompleted(http_response);
            }
            else {
                onPostFailed(http_response);
            }
        }

        // 正常終了時
        public abstract void onPostCompleted(String response);
        // 異常終了時
        public abstract void onPostFailed(String response);
    }

    // 非同期通信タスク
    class HttpPostTask extends AsyncTask<Void, Void, Void> {
        private String response_encoding = "UTF-8";
        private Activity parent_activity = null;
        private String post_url = null;
        private Handler ui_handler = null;
        private ResponseHandler<Void> response_handler = null;
        private String http_err_msg = null;
        private String http_ret_msg = null;

        // 生成時
        public HttpPostTask(Activity parent_activity, String post_url, Handler ui_handler)  {
            // 初期化
            this.parent_activity = parent_activity;
            this.post_url = post_url;
            this.ui_handler = ui_handler;
        }

        // タスク開始時
        protected void onPreExecute() {
            // レスポンスハンドラを生成
            response_handler = new ResponseHandler<Void>() {

                // HTTPレスポンスから，受信文字列をエンコードして文字列として返す
                @Override
                public Void handleResponse(HttpResponse response) throws IOException {
                    String mStatus = String.valueOf(response.getStatusLine().getStatusCode());

                    switch (response.getStatusLine().getStatusCode()) {
                    // 正常終了
                    case HttpStatus.SC_OK:
//                        HttpPostTask.this.http_ret_msg = EntityUtils.toString(
//                            response.getEntity(),
//                            HttpPostTask.this.response_encoding
//                        );

//                        String http_ret_msgs = EntityUtils.toString(
//                                response.getEntity(),
//                                HttpPostTask.this.response_encoding
//                            );

                        HttpEntity httpEntityObj = response.getEntity();
                        long mContentLength = httpEntityObj.getContentLength();
                        InputStream inputStreamObj = httpEntityObj.getContent();

                        InputStreamReader inputStreamReaderObj = new InputStreamReader(inputStreamObj);
                        BufferedReader bufferedReaderObj = new BufferedReader(inputStreamReaderObj);
                        StringBuilder stringBuilderObj = new StringBuilder();
                        String sLine = null;
                        while ((sLine = bufferedReaderObj.readLine()) != null) {
                            if (sLine != null) {
                                StringBuilder sb = new StringBuilder(sLine);
                                sb.append("\r\n");
                                sLine = new String(sb);
                            }
                            stringBuilderObj.append(sLine);
                        }
                        String sReturn = stringBuilderObj.toString();
                        break;

                    // 404 Not Found
                    case HttpStatus.SC_NOT_FOUND:
                        HttpPostTask.this.http_err_msg = "404 Not Found";
                        break;

                    // 通信エラー
                    default:
                        HttpPostTask.this.http_err_msg = "通信エラーが発生";
                        break;
                    }
                    return null;
                }
            };
        }

        // メイン処理
        protected Void doInBackground(Void... unused) {
            // URL
            URI url = null;
            try {
                url = new URI(post_url);
            } catch(URISyntaxException e) {
                return null;
            }

            // GETリクエストを構築
//            HttpGet request = new HttpGet(url);
            // POSTパラメータ付きでPOSTリクエストを構築
            HttpPost request = new HttpPost(url);

            // GETリクエストを実行
            DefaultHttpClient httpClient = new DefaultHttpClient();
            try {
                httpClient.execute(request, response_handler);
            } catch (ClientProtocolException e) {
                return null;
            } catch (IOException e) {
                return null;
            }

            // shutdownすると通信できなくなる
            httpClient.getConnectionManager().shutdown();

            return null;
        }

        // タスク終了時
        protected void onPostExecute(Void unused) {
            // 受信結果をUIに渡すためにまとめる
            Bundle bundle = new Bundle();
            // エラー発生時
            if(http_err_msg != null) {
                bundle.putBoolean("http_post_success", false);
                bundle.putString("http_response", http_err_msg);
            }
            // 通信成功時
            else {
                bundle.putBoolean("http_post_success", true);
                bundle.putString("http_response", http_ret_msg);
            }

            // messageを用意
            Message message = new Message();
            message.setData(bundle);

            // 受信結果に基づいてUI操作させる
            this.ui_handler.sendMessage(message);
        }
    }
}
