package com.pu.shesecure;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ContactViewHolder> {

    private final List<ContactsRecord> contactsList;
    private final OnEditClickListener clickListener;

    public ContactsAdapter(List<ContactsRecord> contactsList, OnEditClickListener clickListener) {
        this.contactsList = contactsList;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        ContactsRecord contacts = contactsList.get(position);
        int color = contacts.isFavorite() ? ContextCompat.getColor(holder.itemView.getContext(), R.color.powerRedLight) : ContextCompat.getColor(holder.itemView.getContext(), R.color.white);

        TextView contact_name = holder.itemView.findViewById(R.id.contact_name);
        TextView contact_number = holder.itemView.findViewById(R.id.contact_number);
        ImageView edit_icon = holder.itemView.findViewById(R.id.edit_icon);
        MaterialCardView root_card = holder.itemView.findViewById(R.id.root_card);

        contact_name.setText(contacts.name());
        contact_number.setText(contacts.number());
        edit_icon.setOnClickListener(v -> clickListener.onEditClick(contacts));
        root_card.setCardBackgroundColor(color);
    }

    @Override
    public int getItemCount() {
        return contactsList.size();
    }

    public void updateData(List<ContactsRecord> newContacts) {
        contactsList.clear();
        contactsList.addAll(newContacts);
        notifyDataSetChanged();
    }

    public interface OnEditClickListener {
        void onEditClick(ContactsRecord contacts);
    }

    public static class ContactViewHolder extends RecyclerView.ViewHolder {
        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}