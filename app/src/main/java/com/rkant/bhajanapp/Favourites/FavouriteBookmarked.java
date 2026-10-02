package com.rkant.bhajanapp.Favourites;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.SearchView;
import android.widget.Toast;

import com.rkant.bhajanapp.FirstActivities.DB_Handler;
import com.rkant.bhajanapp.FirstActivities.DataHolder;
import com.rkant.bhajanapp.MainActivity;
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

public class FavouriteBookmarked extends AppCompatActivity {
    MenuItem youtube_url_menu;
    RecyclerView recyclerView;
    public static ArrayList<DataHolder> publicArrayList, nepaliNumberArrayList;
    public static ArrayList<com.rkant.bhajanapp.secondActivities.DataHolder> notPublicArrayList;
    public static RecyclerAdapter publicRecyclerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favourite);
        
        // Handle Back button navigation
        android.widget.ImageView btnBackFavourite = findViewById(R.id.btn_back_favourite);
        if (btnBackFavourite != null) {
            btnBackFavourite.setOnClickListener(v -> finish());
        }

        // Hide the favorite button on the Favourites activity view but keep it on main
        android.widget.ImageView btnFavouriteHeader = findViewById(R.id.btn_favourite_header);
        if (btnFavouriteHeader != null) {
            btnFavouriteHeader.setVisibility(android.view.View.GONE);
        }

        // Change the title to Favourites
        android.widget.TextView headerTitle = findViewById(R.id.header_title);
        if (headerTitle != null) {
            headerTitle.setText(R.string.title_favourites);
        }

        recyclerView = findViewById(R.id.recyclerView);
        publicArrayList = new ArrayList<>();
        notPublicArrayList = new ArrayList<>();
        nepaliNumberArrayList = new ArrayList<>();


        ThemeHelper.styleActionBar(getSupportActionBar(), getWindow(), this);

        publicRecyclerAdapter = new RecyclerAdapter(FavouriteBookmarked.this, notPublicArrayList, nepaliNumberArrayList);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(publicRecyclerAdapter);
        DB_Handler dbHandler = new DB_Handler(getApplicationContext());
        dbHandler.fetchDbData();
        try {
            addData2();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public void addData2() throws IOException, JSONException {
        String jsonArrayString = readDataFromFile(R.raw.bhajan_list);
        JSONArray jsonArray = new JSONArray(jsonArrayString);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            String str = jsonObject.getString("id");
            for (int j = 0; j < publicArrayList.size(); j++) {
                if (str.equals(publicArrayList.get(j).getString())) {
                    notPublicArrayList.add(new com.rkant.bhajanapp.secondActivities.DataHolder(
                            jsonObject.getString("bhajan_nepali"),
                            jsonObject.getString("bhajan_english"),
                            jsonObject.getString("id"),
                            jsonObject.optString("bhajan", "others"),
                            jsonObject.optString("bhajan_type", "अन्य भजन")));
                    publicRecyclerAdapter.notifyDataSetChanged();
                }
            }
        }
        String nepaliJsonArrayString = readDataFromFile(R.raw.nepali_numbers);
        JSONArray jsonArray1 = new JSONArray(nepaliJsonArrayString);
        for (int i = 0; i < jsonArray1.length(); i++) {
            nepaliNumberArrayList.add(new DataHolder(jsonArray1.getString(i)));
            publicRecyclerAdapter.notifyDataSetChanged();
        }
    }


    public String readDataFromFile(int i) throws IOException {
        InputStream inputStream = null;
        StringBuilder builder = new StringBuilder();
        try {
            String jsonString = null;
            inputStream = getResources().openRawResource(i);
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(inputStream, "UTF-8"));
            while ((jsonString = bufferedReader.readLine()) != null) {
                builder.append(jsonString);
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return new String(builder);
    }

}  