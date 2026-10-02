package com.rkant.bhajanapp.FirstActivities;

import android.content.Context;
import android.content.Intent;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.secondActivities.SecondActivity;
import com.rkant.bhajanapp.secondActivities.DataHolder;

import java.util.ArrayList;
import java.util.HashMap;

public class RecyclerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<RecyclerAdapter.MyViewHolder>{
    private ArrayList<DataHolder> arrayList;
    //For debugging position i am using hashmap
    HashMap<String,Integer> hashMap;
    AdapterView.OnItemSelectedListener listener;
    Context context;
    DB_Handler dbHandler;
    ArrayList<com.rkant.bhajanapp.FirstActivities.DataHolder> nepaliNumbers;
    public RecyclerAdapter(ArrayList<DataHolder> arrayList, AdapterView.OnItemSelectedListener listener, Context context,
                           ArrayList<com.rkant.bhajanapp.FirstActivities.DataHolder> nepaliNumbers){
        this.arrayList=arrayList;
        this.listener=listener;
        this.context=context;
        this.nepaliNumbers=nepaliNumbers;
        //For debugging purpose to know id position
        hashMap=new HashMap<>();
        for (int i=0;i<arrayList.size();i++){
            hashMap.put(arrayList.get(i).getId(),i);
        }
    }



    public class MyViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        TextView textView,textViewNepaliNumber,textViewSubtitle;
        LinearLayout linearLayout;
        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            textView=itemView.findViewById(R.id.textView);
            linearLayout=itemView.findViewById(R.id.layout_name);
            textViewNepaliNumber=itemView.findViewById(R.id.textViewNepaliNumber);
            textViewSubtitle=itemView.findViewById(R.id.textViewSubtitle);
        }
    }

    @NonNull
    @Override
    public RecyclerAdapter.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView= LayoutInflater.from(parent.getContext()).inflate(R.layout.list_layout,parent,false);
        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerAdapter.MyViewHolder holder, int position) {
        if (position < 0 || position >= arrayList.size()) return;

        String string = arrayList.get(position).getBhajan_name_nepali();
        String str = arrayList.get(position).getId();
        String englishName = arrayList.get(position).getBhajan_name_english();
        String bhajanType=arrayList.get(position).getBhajan_type();
        holder.textView.setText(string);

        String nepaliNumStr = "";
        if (nepaliNumbers != null && position < nepaliNumbers.size()) {
            nepaliNumStr = nepaliNumbers.get(position).getString();
        } else {
            nepaliNumStr = String.valueOf(position + 1);
        }
        holder.textViewNepaliNumber.setText(nepaliNumStr);

        if (holder.textViewSubtitle != null) {
            holder.textViewSubtitle.setText("•" + bhajanType+"•" );
        }

        holder.linearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int currentPos = holder.getAdapterPosition();
                if (currentPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    currentPos = position;
                }
                if (currentPos >= 0 && currentPos < arrayList.size()) {
                    Intent intent = new Intent(context, SecondActivity.class);
                    intent.putExtra("position", arrayList.get(currentPos).getId());
                    context.startActivity(intent);
                }
            }
        });

        holder.linearLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                int currentPos = holder.getAdapterPosition();
                if (currentPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    currentPos = position;
                }
                if (currentPos >= 0 && currentPos < arrayList.size()) {
                    final DataHolder currentBhajan = arrayList.get(currentPos);
                    final String favStr = currentBhajan.getId();
                    final String favString = currentBhajan.getBhajan_name_nepali();

                    // Create and style the custom Dialog
                    final android.app.Dialog dialog = new android.app.Dialog(context);
                    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
                    dialog.setContentView(R.layout.dialog_add_favourite);

                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                        dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                    }

                    TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
                    TextView dialogMessage = dialog.findViewById(R.id.dialog_message);
                    com.google.android.material.button.MaterialButton btnCancel = dialog.findViewById(R.id.btn_cancel);
                    com.google.android.material.button.MaterialButton btnAdd = dialog.findViewById(R.id.btn_add);

                    if (dialogMessage != null) {
                        dialogMessage.setText("Do you want to add \"" + favString + "\" to your favourite list?");
                    }

                    if (btnCancel != null) {
                        btnCancel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dialog.dismiss();
                            }
                        });
                    }

                    if (btnAdd != null) {
                        btnAdd.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dbHandler = new DB_Handler(context.getApplicationContext());
                                dbHandler.addData(favStr);
                                
                                dialog.dismiss();
                            }
                        });
                    }

                    dialog.show();
                }
                return true;
            }
        });

    }

    @Override
    public int getItemCount() {
        return arrayList.size();
    }
    public void filterList(ArrayList<DataHolder> filteredList){
        arrayList=filteredList;
        notifyDataSetChanged();
    }

    public DataHolder getItem(int position) {
        if (arrayList != null && position >= 0 && position < arrayList.size()) {
            return arrayList.get(position);
        }
        return null;
    }

}
  