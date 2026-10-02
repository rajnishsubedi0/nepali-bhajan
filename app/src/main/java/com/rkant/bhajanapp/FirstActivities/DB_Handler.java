package com.rkant.bhajanapp.FirstActivities;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.Toast;

import com.rkant.bhajanapp.CustomToast;
import com.rkant.bhajanapp.Favourites.FavouriteBookmarked;
import com.rkant.bhajanapp.MainActivity;

import java.util.ArrayList;

public class DB_Handler extends SQLiteOpenHelper {
    Context context;
    ArrayList<DataHolder> arrayList;
        private static final String DB_NAME="bhajan_list";
        private static final int DB_VERSION=1;
        private static final String DB_TABLE_NAME="bhajans_table";
        private static final String SERIAL_NO="serial_no";
        private static final String BHAJAN_NAME="bhajan_name";
        public DB_Handler(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
            this.context=context;
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            String query="CREATE TABLE "+DB_TABLE_NAME+" ("+
                    SERIAL_NO+" INTEGER PRIMARY KEY AUTOINCREMENT, "+
                    BHAJAN_NAME+" TEXT)";
            sqLiteDatabase.execSQL(query);

        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS "+DB_TABLE_NAME);
            onCreate(sqLiteDatabase);
        }
        public void addData(String str){
            if(checkIfExists(str)){
            SQLiteDatabase db=this.getWritableDatabase();
            ContentValues contentValues=new ContentValues();
            contentValues.put(BHAJAN_NAME,str);
            db.insert(DB_TABLE_NAME,null,contentValues);
            db.close();
            CustomToast.showSuccess(context, "Bhajan Added");
            }


        }
        public boolean checkIfExists(String str) {
            arrayList = new ArrayList<>();
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM " + DB_TABLE_NAME, null);
            // ArrayList<DataHolder> arrayList=new ArrayList<>();
            while (cursor.moveToNext()) {
                if (str.equals(cursor.getString(1))) {
                    CustomToast.showInfo(context, "Bhajan already been added");
                    return false;
                }

            }
            return true;

        }
        public void fetchDbData(){
        SQLiteDatabase db=this.getReadableDatabase();
        Cursor cursor=db.rawQuery("SELECT * FROM "+DB_TABLE_NAME,null);
        // ArrayList<DataHolder> arrayList=new ArrayList<>();
        while (cursor.moveToNext()){
            FavouriteBookmarked.publicArrayList.add(new DataHolder(cursor.getString(1)));
           // FavouriteBookmarked.publicRecyclerAdapter.notifyDataSetChanged();
        }
    }
    public void deleteCourse(String bhajan_id_name) {

        SQLiteDatabase db = this.getWritableDatabase();

        db.delete(DB_TABLE_NAME, "bhajan_name=?", new String[]{bhajan_id_name});
        FavouriteBookmarked.publicArrayList.clear();
        FavouriteBookmarked.nepaliNumberArrayList.clear();
        FavouriteBookmarked.notPublicArrayList.clear();
        fetchDbData();
        CustomToast.showSuccess(context, "Removed");
        db.close();
    }

    }

  