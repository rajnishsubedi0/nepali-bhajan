package com.rkant.bhajanapp.secondActivities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;

import com.rkant.bhajanapp.CustomToast;
import com.rkant.bhajanapp.Favourites.FavouriteBookmarked;
import com.rkant.bhajanapp.FirstActivities.DataHolder;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.ThemeHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class SecondActivity extends AppCompatActivity {
    MenuItem youtube_url_menu;
    ArrayList<DataHolder> arrayList;
    RecyclerView recyclerView;
    RecyclerAdapter bhajanData_recyclerView;
    String youtube_url;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logic);
        recyclerView = findViewById(R.id.recyclerSecondView);
        arrayList = new ArrayList<>();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ThemeHelper.styleActionBar(getSupportActionBar(), getWindow(), this);

        // Set up custom toolbar back and more options click listeners
        android.widget.ImageView btnBack = findViewById(R.id.btn_back);
        android.widget.ImageView btnYoutubeHeader = findViewById(R.id.btn_youtube_header);

        if (btnBack != null) {
            btnBack.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View view) {
                    onBackPressed();
                }
            });
        }

        

        try {
            setData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        // Show/hide custom themed YouTube play button dynamically based on link existence
        if (btnYoutubeHeader != null) {
            btnYoutubeHeader.setVisibility(android.view.View.VISIBLE);
            btnYoutubeHeader.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View view) {
                    if (youtube_url != null && !youtube_url.isEmpty()) {
                        showYoutubePlayDialog();
                    } else {
                        CustomToast.showError(SecondActivity.this, "No link found");
                    }
                }
            });
        }

    }

    public void setData() throws IOException, JSONException {
        Bundle bundle = getIntent().getExtras();
        if (bundle == null) return;
        String intentPosition = bundle.getString("position");
        if (intentPosition == null) return;
        String jsonDataString = readDataFromFile(R.raw.bhajan_data);
        JSONArray jsonArray=new JSONArray(jsonDataString);
        for (int i=0;i<jsonArray.length();i++){
            JSONObject object=jsonArray.getJSONObject(i);
            if(intentPosition.equals(object.getString("id"))){
                String str=object.getString("bhajan");
                JSONArray array=new JSONArray(str);
                for (int j=0;j<array.length();j++){
                    arrayList.add(new DataHolder(array.getString(j)));
                }
                break;
            }
        }
        setAdapter();
        String string_url_link=readDataFromFile(R.raw.youtube_link);
        JSONArray url_array=new JSONArray(string_url_link);
        youtube_url="";
        for (int j=0;j<url_array.length();j++){
            JSONObject object=url_array.getJSONObject(j);
            if(intentPosition.equals(object.getString("id"))){
                youtube_url=object.getString("link");
                break;
            }
        }

    }



    public String readDataFromFile(int i) throws IOException {
        InputStream inputStream=null;
        StringBuilder builder=new StringBuilder();
        try{
            String jsonString=null;
            inputStream=getResources().openRawResource(i);
            BufferedReader bufferedReader=new BufferedReader(
                    new InputStreamReader(inputStream,"UTF-8"));
            while ((jsonString=bufferedReader.readLine()) !=null){
                builder.append(jsonString);
            }
        }
        finally {
            if(inputStream != null){
                inputStream.close();
            }
        }
        return new String(builder);
    }


    @Override
    //Keeping Screen On
    protected void onStart() {
        super.onStart();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }, 120000);

    }

    public void setAdapter() {
        bhajanData_recyclerView=new RecyclerAdapter(arrayList, SecondActivity.this   );
        RecyclerView.LayoutManager layoutManager=new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(bhajanData_recyclerView);
    }

    private void showYoutubePlayDialog() {
        if (youtube_url == null || youtube_url.isEmpty()) {
            CustomToast.showError(SecondActivity.this, "No link found");
            return;
        }

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_youtube_link);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        com.google.android.material.button.MaterialButton btnCancel = dialog.findViewById(R.id.btn_cancel);
        com.google.android.material.button.MaterialButton btnOpen = dialog.findViewById(R.id.btn_open_youtube);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    dialog.dismiss();
                }
            });
        }

        if (btnOpen != null) {
            btnOpen.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    try {
                        Uri uri = Uri.parse(youtube_url);
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    } catch (Exception e) {
                        CustomToast.showError(SecondActivity.this, "Failed to open link");
                    }
                    dialog.dismiss();
                }
            });
        }

        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.youtube_link, menu);
        youtube_url_menu = menu.findItem(R.id.youtube_url);
        youtube_url_menu.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(@NonNull MenuItem menuItem) {
                showYoutubePlayDialog();
                return false;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

}

  