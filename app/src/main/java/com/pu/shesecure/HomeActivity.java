package com.pu.shesecure;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomeActivity extends AppCompatActivity {

    private final SecureFragment secureFragment = new SecureFragment();
    private final ContactsFragment contactsFragment = new ContactsFragment();
    private final DashboardFragment dashboardFragment = new DashboardFragment();

    private Fragment activeFragment = secureFragment;
    private BottomNavigationView navigationView;
    private long lastBackPressedTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        getSupportFragmentManager().beginTransaction() // Adding all fragments
                .add(R.id.fragment_container, contactsFragment).hide(contactsFragment) // ContactsFragment
                .add(R.id.fragment_container, dashboardFragment).hide(dashboardFragment) // DashboardFragment
                .add(R.id.fragment_container, secureFragment).commitNow(); // SecureFragment

        navigationView = findViewById(R.id.bottom_navigation);
        navigationView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.secure_nav) {
                switchFragment(secureFragment);
            } else if (item.getItemId() == R.id.contacts_nav) {
                switchFragment(contactsFragment);
            } else if (item.getItemId() == R.id.dashboard_nav) {
                switchFragment(dashboardFragment);
            }
            return true;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (activeFragment != secureFragment) {
                    navigationView.setSelectedItemId(R.id.secure_nav);
                    return;
                }

                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBackPressedTime >= 2000) {
                    lastBackPressedTime = currentTime;
                    Toast.makeText(HomeActivity.this, "Back again to exit", Toast.LENGTH_SHORT).show();
                } else finishAffinity();
            }
        });
    }

    private void switchFragment(Fragment fragment) {
        if (activeFragment == fragment) return;
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.hide(activeFragment).show(fragment).commitNow();
        activeFragment = fragment;
    }

    public void switchToContacts() {
        contactsFragment.highlightAddButton();
        navigationView.setSelectedItemId(R.id.contacts_nav);
    }
}