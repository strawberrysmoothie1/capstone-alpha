package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.example.myapplication.Login_network.LoginClient;
import com.example.myapplication.Login_network.LoginService;
import com.example.myapplication.Login_network.LogoutRequest;
import com.example.myapplication.Login_network.LogoutResponse;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences preferences;
    private LoginService loginService;
    private TextView tvUserID, tvJoinDate;
    private Switch switchNightMode, switchNotification, switchAutoLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 툴바 설정
        Toolbar toolbar = findViewById(R.id.toolbarSettings);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // SharedPreferences 및 LoginService 초기화
        preferences = getSharedPreferences("AutoLogin", MODE_PRIVATE);
        loginService = LoginClient.getClient("http://10.0.2.2:5000/").create(LoginService.class);

        // UI 요소 초기화
        tvUserID = findViewById(R.id.tvUserID);
        tvJoinDate = findViewById(R.id.tvJoinDate);
        switchNightMode = findViewById(R.id.switchNightMode);
        switchNotification = findViewById(R.id.switchNotification);
        switchAutoLogin = findViewById(R.id.switchAutoLogin);

        // 사용자 정보 표시
        String userID = preferences.getString("id", "");
        String joinDate = preferences.getString("joinDate", "");
        tvUserID.setText(userID);
        tvJoinDate.setText(joinDate);

        // 설정 상태 복원
        boolean isNightMode = preferences.getBoolean("nightMode", false);
        boolean isNotificationEnabled = preferences.getBoolean("notification", true);
        boolean isAutoLoginEnabled = preferences.getBoolean("autoLoginEnabled", true);

        switchNightMode.setChecked(isNightMode);
        switchNotification.setChecked(isNotificationEnabled);
        switchAutoLogin.setChecked(isAutoLoginEnabled);

        // 다크 모드 스위치 리스너
        switchNightMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                preferences.edit().putBoolean("nightMode", isChecked).apply();
                if (isChecked) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
            }
        });

        // 알림 스위치 리스너
        switchNotification.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                preferences.edit().putBoolean("notification", isChecked).apply();
                Toast.makeText(SettingsActivity.this, 
                    isChecked ? "알림이 활성화되었습니다." : "알림이 비활성화되었습니다.", 
                    Toast.LENGTH_SHORT).show();
            }
        });

        // 자동 로그인 스위치 리스너
        switchAutoLogin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                preferences.edit().putBoolean("autoLoginEnabled", isChecked).apply();
                Toast.makeText(SettingsActivity.this, 
                    isChecked ? "자동 로그인이 활성화되었습니다." : "자동 로그인이 비활성화되었습니다.", 
                    Toast.LENGTH_SHORT).show();
            }
        });

        // 로그아웃 버튼 리스너
        findViewById(R.id.btnLogout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                    .setTitle("로그아웃")
                    .setMessage("정말 로그아웃하시겠습니까?")
                    .setPositiveButton("예", (dialog, which) -> logout())
                    .setNegativeButton("아니오", null)
                    .show();
            }
        });

        // 계정 삭제 버튼 리스너
        findViewById(R.id.btnDeleteAccount).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                    .setTitle("계정 삭제")
                    .setMessage("계정을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
                    .setPositiveButton("삭제", (dialog, which) -> {
                        Toast.makeText(SettingsActivity.this, "이 기능은 아직 구현되지 않았습니다.", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("취소", null)
                    .show();
            }
        });

        // 앱 정보 버튼 리스너
        findViewById(R.id.btnAppInfo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                    .setTitle("앱 정보")
                    .setMessage("아기침대 앱 버전 1.0\n\n이 앱은 침대 공유 및 관리를 위한 앱입니다.\n개발자: 캡스톤 팀")
                    .setPositiveButton("확인", null)
                    .show();
            }
        });
    }

    private void logout() {
        String userId = preferences.getString("id", "");
        if (userId.isEmpty()) {
            Toast.makeText(SettingsActivity.this, "저장된 사용자 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(SettingsActivity.this, LogActivity.class));
            finish();
            return;
        }

        LogoutRequest logoutRequest = new LogoutRequest(userId);
        Call<LogoutResponse> call = loginService.logout(logoutRequest.toMap());
        call.enqueue(new Callback<LogoutResponse>() {
            @Override
            public void onResponse(Call<LogoutResponse> call, Response<LogoutResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LogoutResponse result = response.body();
                    if (result.isSuccess()) {
                        // 자동 로그인 정보 삭제
                        preferences.edit()
                            .remove("id")
                            .remove("password")
                            .apply();

                        Toast.makeText(SettingsActivity.this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SettingsActivity.this, LogActivity.class));
                        finish();
                    } else {
                        Toast.makeText(SettingsActivity.this, "로그아웃 실패: " + result.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(SettingsActivity.this, "서버 응답 오류", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<LogoutResponse> call, Throwable t) {
                Toast.makeText(SettingsActivity.this, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
} 