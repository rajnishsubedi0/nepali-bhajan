package com.rkant.bhajanapp.Favourites;

import android.content.Context;
import android.content.Intent;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.HorizontalScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rkant.bhajanapp.FirstActivities.DB_Handler;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.secondActivities.DataHolder;
import com.rkant.bhajanapp.secondActivities.SecondActivity;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;

public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.MyViewHolderClass> {
    Context context;
    ArrayList<com.rkant.bhajanapp.secondActivities.DataHolder> arrayList;
    ArrayList<com.rkant.bhajanapp.FirstActivities.DataHolder> arrayList1;
    DB_Handler dbHandler;

    public RecyclerAdapter(Context context, ArrayList<DataHolder> arrayList,
                           ArrayList<com.rkant.bhajanapp.FirstActivities.DataHolder> arrayList1){
        this.arrayList=arrayList;
        this.context=context;
        this.arrayList1=arrayList1;
    }
    public class MyViewHolderClass extends RecyclerView.ViewHolder {
        TextView textView,textViewNepaliNumber,textViewSubtitle;
        LinearLayout linearLayout;
        public MyViewHolderClass(@NonNull View itemView) {
            super(itemView);
            textView=itemView.findViewById(R.id.textView);
            textViewNepaliNumber=itemView.findViewById(R.id.textViewNepaliNumber);
            textViewSubtitle=itemView.findViewById(R.id.textViewSubtitle);
            linearLayout=itemView.findViewById(R.id.layout_name);
        }
    }
    @NonNull
    @Override
    public RecyclerAdapter.MyViewHolderClass onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view=LayoutInflater.from(parent.getContext()).inflate(R.layout.list_layout,parent,false);
        return new MyViewHolderClass(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerAdapter.MyViewHolderClass holder, int position) {
        if (position < 0 || position >= arrayList.size()) return;

        String string = arrayList.get(position).getBhajan_name_nepali();
        String str = arrayList.get(position).getId();
        String englishName = arrayList.get(position).getBhajan_name_english();
        String bhajanType = arrayList.get(position).getBhajan_type();

        holder.textView.setText(string);

        String nepaliNumStr = "";
        if (arrayList1 != null && position < arrayList1.size()) {
            nepaliNumStr = arrayList1.get(position).getString();
        } else {
            nepaliNumStr = String.valueOf(position + 1);
        }
        holder.textViewNepaliNumber.setText(nepaliNumStr);

        if (holder.textViewSubtitle != null) {
            holder.textViewSubtitle.setText("•" + bhajanType+"•");
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
                            final int targetedPosition = currentPos;
                            final String bhajanName = arrayList.get(targetedPosition).getBhajan_name_nepali();
                            
                            // Show beautiful, custom themed confirmation dialog
                            final android.app.Dialog dialog = new android.app.Dialog(context);
                            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
                            dialog.setContentView(R.layout.dialog_remove_favourite);

                            if (dialog.getWindow() != null) {
                                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                                dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                            }

                            TextView dialogMessage = dialog.findViewById(R.id.dialog_message);
                            if (dialogMessage != null) {
                                dialogMessage.setText("Do you want to remove \"" + bhajanName + "\" from your favourite list?");
                            }

                            com.google.android.material.button.MaterialButton btnCancel = dialog.findViewById(R.id.btn_cancel);
                            com.google.android.material.button.MaterialButton btnRemove = dialog.findViewById(R.id.btn_remove);

                            if (btnCancel != null) {
                                btnCancel.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        dialog.dismiss();
                                    }
                                });
                            }

                            if (btnRemove != null) {
                                btnRemove.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        dbHandler = new DB_Handler(context.getApplicationContext());
                                        dbHandler.deleteCourse(arrayList.get(targetedPosition).getId());
                                        try {
                                            ((FavouriteBookmarked)context).addData2();
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        } catch (JSONException e) {
                                            throw new RuntimeException(e);
                                        }
                                        dialog.dismiss();
                                    }
                                });
                            }

                            dialog.show();
                        }
                        return false;
                    }
        });

    }

    @Override
    public int getItemCount() {
        return arrayList.size();
    }
}
  