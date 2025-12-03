package com.pu.shesecure;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {

    private final String[] titles = {"Hello there!", "Stay Secure", "How it works", "Permissions"};
    private final String[] descriptions = {"Welcome to She Secure, your safety companion designed to protect you anytime. With just a tap, you can stay connected, call for help instantly, and feel more secure wherever you go.",
            "Quickly access safety features such as SOS alerts, live location sharing with trusted contacts, and emergency calling to your primary number whenever you need immediate help.",
            "If you ever feel unsafe, enable She Secure Mode. The app listens for your SOS keywords, default is 'help me'. When triggered, it alerts contacts with your location and calls your primary number.",
            "To keep you safe, She Secure needs some permissions. These allow alerts, live tracking, and emergency calls to work properly. We never store or share personal data. Please grant them."};
    private final int[] images = {R.drawable.app_logo, R.drawable.vector_stay_secure, R.drawable.vector_power_off, R.drawable.vector_permission};

    public OnboardingAdapter() {}

    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding, parent, false);
        return new OnboardingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        holder.title.setText(titles[position]);
        holder.description.setText(descriptions[position]);
        holder.image.setImageResource(images[position]);
    }

    @Override
    public int getItemCount() {
        return titles.length;
    }

    public static class OnboardingViewHolder extends RecyclerView.ViewHolder {
        TextView title, description;
        ImageView image;

        OnboardingViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.onboarding_title);
            description = itemView.findViewById(R.id.onboarding_description);
            image = itemView.findViewById(R.id.onboarding_image);
        }
    }
}