package com.hide_ab.apitest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
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

        this.myHandler = new MyHandler();

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Message msg = new Message();
                MainActivity.this.myHandler.sendMessage(msg);
            }
        };

        this.myTimer = new Timer();
        myTimer.schedule(task, 1000, 5000);
    }

    @Override
    protected void onStop() {
        super.onStop();

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
            if((MainActivity.this.iFlg % 2) == 0) {
                MainActivity.this.tvTemperature01.setText(R.string.temperature01);
            }
            else {
//                MainActivity.this.tvTemperature01.setText(R.string.temperature02);
                exec_post();
            }
            MainActivity.this.iFlg++;
        }
    }

    private void exec_post() {
        // 非同期タスクを定義
        HttpPostTask task = new HttpPostTask(
                this,
                "http://ec2-54-248-130-198.ap-northeast-1.compute.amazonaws.com/observations.xml",
                // タスク完了時に呼ばれるUIのハンドラ
                new HttpPostHandler() {
                    @Override
                    public void onPostCompleted(String response) {
                        MainActivity.this.tvTemperature01.setText(R.string.strOK);
                    }
                    @Override
                    public void onPostFailed(String response) {
                        MainActivity.this.tvTemperature01.setText(R.string.strNG);
                    }
                }
            );
        // タスクを開始
        task.execute();
    }

    class HttpPostTask  extends AsyncTask<Void, Void, Void> {
        private String response_encoding = "UTF-8";
        private Activity parent_activity = null;
        private String post_url = null;
        private Handler ui_handler = null;
        private ResponseHandler<Void> response_handler = null;
        private String http_err_msg = null;
        private String http_ret_msg = null;

        public HttpPostTask(Activity parent_activity, String post_url, Handler ui_handler)  {
            this.parent_activity = parent_activity;
            this.post_url = post_url;
            this.ui_handler = ui_handler;
        }

        protected void onPreExecute() {
            response_handler = new ResponseHandler<Void>() {
                @Override
                public Void handleResponse(HttpResponse response) throws IOException {
                    switch (response.getStatusLine().getStatusCode()) {
                    case HttpStatus.SC_OK:
                        HttpPostTask.this.http_ret_msg = EntityUtils.toString(
                                response.getEntity(),
                                HttpPostTask.this.response_encoding
                              );
                        break;
                    case HttpStatus.SC_NOT_FOUND:
                        HttpPostTask.this.http_err_msg = "404 Not Found";
                        break;
                    default:
                        HttpPostTask.this.http_err_msg = "通信エラーが発生";
                        break;
                    }
                    return null;
                }
            };
        }

        @Override
        protected Void doInBackground(Void... unused) {
            URI url = null;
            try {
                url = new URI( post_url );
            } catch(URISyntaxException e) {
                return null;
            }

            // POSTパラメータ付きでPOSTリクエストを構築
            HttpPost request = new HttpPost( url );
            // POSTリクエストを実行
            DefaultHttpClient httpClient = new DefaultHttpClient();
            try {
                httpClient.execute(request, response_handler);
            } catch (ClientProtocolException e) {
                return null;
            } catch (IOException e) {
                return null;
            }
            return null;
        }
    }

    public abstract class HttpPostHandler extends Handler {
        public void handleMessage(Message msg) {
            boolean isPostSuccess = msg.getData().getBoolean("http_post_success");
            String http_response = msg.getData().get("http_response").toString();
            if( isPostSuccess ) {
                onPostCompleted( http_response );
            }
            else {
                onPostFailed( http_response );
            }
        }
        public abstract void onPostCompleted( String response );
        public abstract void onPostFailed( String response );
    }
}
