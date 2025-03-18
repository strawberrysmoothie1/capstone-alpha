package com.example.myapplication;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.Login_network.GetPendingRequestsResponse;
import com.example.myapplication.Login_network.LoginClient;
import com.example.myapplication.Login_network.LoginRequest;
import com.example.myapplication.Login_network.LoginResponse;
import com.example.myapplication.Login_network.LoginService;
import com.example.myapplication.model.BedDisplay;
import com.example.myapplication.model.TempGuardianRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LogActivity extends AppCompatActivity {

    private static final String TAG = "LogActivity";
    private LoginService loginService;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        // SharedPreferences 초기화
        preferences = getSharedPreferences("AutoLogin", MODE_PRIVATE);
        
        // Retrofit 서비스 초기화 - 가장 먼저 초기화하도록 위치 변경
        loginService = LoginClient.getClient("http://10.0.2.2:5000/").create(LoginService.class);

        // UI 요소 참조
        EditText etId = findViewById(R.id.etId);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        CheckBox cbAutoLogin = findViewById(R.id.cbAutoLogin);
        Button btnRegister = findViewById(R.id.btnRegister);

        // 자동 로그인 체크: autoLogin 플래그와 "id"가 저장되어 있으면 바로 다음 화면으로 이동
        if (preferences.getBoolean("autoLogin", false)) {
            String savedId = preferences.getString("id", "");
            if (!savedId.isEmpty()) {
                Toast.makeText(this, "자동 로그인 중...", Toast.LENGTH_SHORT).show();
                // 로그인한 사용자 정보로 침대 요청 확인
                checkBedRequests(savedId);
                return;  // 로그인 화면을 더 이상 표시하지 않음
            }
        }

        // 로그인 버튼 클릭 이벤트
        btnLogin.setOnClickListener(view -> {
            // 사용자 입력 값 가져오기
            String id = etId.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            // 입력 값 유효성 검사
            if (id.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "아이디/비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 로그인 요청 (자동 로그인 여부 포함)
            LoginRequest request = new LoginRequest(id, password, cbAutoLogin.isChecked());
            Call<LoginResponse> call = loginService.login(request);
            call.enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        LoginResponse result = response.body();
                        if (result.isSuccess()) {
                            // 자동 로그인 설정 저장 및 SharedPreferences 업데이트
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString("lastLoggedInId", id);
                            // 수정된 부분: 항상 "id" 키에 값을 저장함.
                            editor.putBoolean("autoLogin", cbAutoLogin.isChecked());
                            editor.putString("id", id);
                            editor.apply();

                            Toast.makeText(LogActivity.this, "로그인 성공!", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "로그인 성공: userId = " + id);

                            // 로그인 성공 후 침대 권한 요청 확인
                            checkBedRequests(id);
                        } else {
                            Toast.makeText(LogActivity.this, result.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        if (response.code() == 401) {
                            Toast.makeText(LogActivity.this, "아이디 또는 비밀번호가 틀립니다.", Toast.LENGTH_SHORT).show();
                        } else if (response.code() == 403) {
                            Toast.makeText(LogActivity.this, "승인 대기 중입니다.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(LogActivity.this, "서버 오류: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                @Override
                public void onFailure(Call<LoginResponse> call, Throwable t) {
                    Log.e(TAG, "로그인 요청 실패", t);
                    Toast.makeText(LogActivity.this, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        // 회원가입 버튼 클릭 이벤트
        btnRegister.setOnClickListener(view -> {
            Intent intent = new Intent(LogActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
    
    // 침대 권한 요청 확인 메서드
    private void checkBedRequests(String userId) {
        // 'id' 키에 값이 없으면 'lastLoggedInId' 키의 값을 사용합니다.
        if (userId == null || userId.isEmpty()) {
            userId = preferences.getString("lastLoggedInId", "");
        }
        if (userId.isEmpty()) {
            Log.d(TAG, "사용자 ID가 없어 침대 요청을 확인할 수 없습니다.");
            navigateToAddBed(false);
            return;
        }
        
        Log.d(TAG, "checkBedRequests 호출됨: userId = " + userId);
        
        // null 체크 추가
        if (loginService == null) {
            Log.e(TAG, "loginService가 초기화되지 않았습니다");
            navigateToAddBed(false);
            return;
        }
        
        // Map으로 변환하여 API 호출
        Map<String, String> params = new HashMap<>();
        params.put("gdID", userId);
        
        // getPendingTempGuardianRequests API 호출 - designation이 느낌표(!)로 시작하는 요청만 처리
        Call<GetPendingRequestsResponse> call = loginService.getPendingTempGuardianRequests(params);
        call.enqueue(new Callback<GetPendingRequestsResponse>() {
            @Override
            public void onResponse(Call<GetPendingRequestsResponse> call, Response<GetPendingRequestsResponse> response) {
                Log.d(TAG, "getPendingRequests API 응답 받음: " + response.code());
                
                if (response.isSuccessful() && response.body() != null) {
                    GetPendingRequestsResponse pendingResponse = response.body();
                    
                    if (pendingResponse.getResult()) {
                        List<TempGuardianRequest> requestsList = pendingResponse.getRequests();
                        boolean hasBedRequests = requestsList != null && !requestsList.isEmpty();
                        navigateToAddBed(hasBedRequests);
                    } else {
                        navigateToAddBed(false);
                    }
                } else {
                    navigateToAddBed(false);
                }
            }

            @Override
            public void onFailure(Call<GetPendingRequestsResponse> call, Throwable t) {
                Log.e(TAG, "침대 요청 API 호출 실패", t);
                navigateToAddBed(false);
            }
        });
    }
    
    // 침대 요청 알림창 표시 메서드
    private void showBedRequestDialog(List<BedDisplay> requestBeds) {
        // dialog_bed_requests.xml 파일을 인플레이트합니다.
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_bed_requests, null);
        LinearLayout container = dialogView.findViewById(R.id.llRequestsContainer);

        // 각 요청마다 item_request.xml을 인플레이트하여 container에 추가합니다.
        for (BedDisplay bed : requestBeds) {
            View itemView = inflater.inflate(R.layout.item_request, container, false);
            TextView tvRequestInfo = itemView.findViewById(R.id.tvRequestInfo);
            // 침대ID와 기간을 한 줄에 표시 ("침대ID - 기간")
            String requestInfo = "침대ID: "+bed.getBedID() + ", 기간: " + (bed.getPeriod() != null ? bed.getPeriod() : "");
            tvRequestInfo.setText(requestInfo);
            container.addView(itemView);
        }

        new AlertDialog.Builder(this)
                .setTitle("침대 권한 요청 알림")
                .setView(dialogView)
                .setPositiveButton("메세지함으로 이동", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 메시지함 화면으로 이동하고 현재 액티비티 종료
                        Intent intent = new Intent(LogActivity.this, MessagesActivity.class);
                        startActivity(intent);
                        finish();
                    }
                })


                .setNegativeButton("나중에 확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 기본 화면으로 이동
                        navigateToAddBed(false);
                    }
                })
                .setCancelable(false)
                .show();
    }

    // AddBedActivity로 이동하는 메서드
    private void navigateToAddBed(boolean hasBedRequests) {
        Intent intent = new Intent(LogActivity.this, AddBedActivity.class);
        intent.putExtra("hasBedRequests", hasBedRequests);
        startActivity(intent);
        finish();
    }
}
