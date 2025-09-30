package io.github.childscreentime.model;

import android.content.SharedPreferences;

import io.github.childscreentime.utils.Utils;


public class CreditPreferences {
    public static final Credit DEFAULT_CREDIT = new Credit(20, 1, 1);
    private final String key;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;
    private final SharedPreferences sharedPreferences;

    public CreditPreferences(SharedPreferences sharedPreferences2, String todayAsString, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        this.sharedPreferences = sharedPreferences2;
        this.key = todayAsString;
        this.listener = listener;
    }

    public static CreditPreferences getTodayCreditPreferences(SharedPreferences sharedPreferences, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        return new CreditPreferences(sharedPreferences, Utils.getTodayAsString(), listener);
    }

    public void save(Credit credit) {
        this.sharedPreferences.edit().putString(this.key, credit.asString()).apply();
        listener.onSharedPreferenceChanged(sharedPreferences, this.key);
    }

    public Credit get() {
        return Credit.fromString(getAsString());
    }

    public String getAsString() {
        return this.sharedPreferences.getString(this.key ,  DEFAULT_CREDIT.asString());
    }
}
