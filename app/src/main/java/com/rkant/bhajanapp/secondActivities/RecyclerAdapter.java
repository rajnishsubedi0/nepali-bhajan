package com.rkant.bhajanapp.secondActivities;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.rkant.bhajanapp.FirstActivities.DataHolder;
import com.rkant.bhajanapp.R;

import java.util.ArrayList;

public class RecyclerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<RecyclerAdapter.MyViewHolder>{
    private ArrayList<DataHolder> arrayList;
    private LinearLayout linearLayout;
    private Context context;

    public RecyclerAdapter(ArrayList<DataHolder> arrayList, Context context){
        this.arrayList=arrayList;
        this.context=context;

    }
    public class MyViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        TextView textView;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            textView=itemView.findViewById(R.id.textView);
            linearLayout=itemView.findViewById(R.id.layout_name);



        }
    }
    @NonNull
    @Override
    public RecyclerAdapter.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView= LayoutInflater.from(parent.getContext()).inflate(R.layout.logics_recycler_holder,parent,false);
        return new RecyclerAdapter.MyViewHolder(itemView);

    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerAdapter.MyViewHolder holder, int position) {
        String string = arrayList.get(position).getString();
        
        if (string == null || string.trim().isEmpty()) {
            holder.textView.setText("");
            holder.textView.setVisibility(View.GONE);
            holder.itemView.findViewById(R.id.layout_name).setBackgroundColor(android.graphics.Color.TRANSPARENT);
            holder.itemView.findViewById(R.id.layout_name).setMinimumHeight((int) (18 * context.getResources().getDisplayMetrics().density));
        } else {
            holder.textView.setText(string);
            holder.textView.setVisibility(View.VISIBLE);
            holder.itemView.findViewById(R.id.layout_name).setBackgroundResource(R.drawable.bg_lyric_card);
            holder.itemView.findViewById(R.id.layout_name).setMinimumHeight(0);
        }
    }

    @Override
    public int getItemCount() {
        return arrayList.size();
    }

 

}
  